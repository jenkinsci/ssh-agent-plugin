package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.jenkins.plugins.sshagent.exec.ExecRemoteAgent;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.slaves.WorkspaceList;
import hudson.util.Secret;
import jenkins.MasterToSlaveFileCallable;
import org.jenkinsci.plugins.gitclient.GitHostKeyVerificationConfiguration;
import org.jenkinsci.plugins.gitclient.verifier.HostKeyVerifierFactory;
import org.jenkinsci.plugins.workflow.steps.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

final class SSHAgentStepExecution extends AbstractStepExecutionImpl {

    private static final long serialVersionUID = 1L;

    private transient SSHAgentStep step;

    private ExecRemoteAgent agent;

    /** Optional environment variable to expose the credential username under. Survives resume. */
    private final String usernameVariable;

    /** Username of the first resolved credential, captured when {@link #usernameVariable} is set. */
    private String username;

    /** Whether Git host key verification is enabled for the block. Survives resume. */
    private final boolean hostKeyVerification;

    /** Computed {@code GIT_SSH_COMMAND} exposed inside the block when {@link #hostKeyVerification} is set. */
    private String gitSshCommand;

    /** Remote path of the temporary known_hosts file, kept so it can be cleaned up on stop. */
    private String knownHostsPath;

    SSHAgentStepExecution(SSHAgentStep step, StepContext context) {
        super(context);
        this.step = step;
        this.usernameVariable = step.getUsernameVariable();
        this.hostKeyVerification = step.isHostKeyVerification();
    }

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        initRemoteAgent();
        context.newBodyInvoker().
                withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(this))).
                withCallback(new Callback(this)).start();
        return false;
    }

    @Override
    public void stop(@NonNull Throwable cause) throws Exception {
        try {
            stop();
        } catch (Exception x) {
            cause.addSuppressed(x);
        }
        super.stop(cause);
    }

    private void stop() throws Exception {
        if (agent != null) {
            TaskListener listener = getContext().get(TaskListener.class);
            Launcher launcher = getContext().get(Launcher.class);
            if (listener != null && launcher != null) {
                agent.stop(launcher, listener);
                listener.getLogger().println(Messages.SSHAgentBuildWrapper_Stopped());
                if (knownHostsPath != null) {
                    try {
                        new FilePath(launcher.getChannel(), knownHostsPath).delete();
                    } catch (IOException | InterruptedException x) {
                        listener.getLogger().println("Failed to delete temporary known_hosts file: " + x.getMessage());
                    }
                }
            }
        }
    }

    private static class Callback extends BodyExecutionCallback.TailCall {

        private static final long serialVersionUID = 1L;

        private final SSHAgentStepExecution execution;

        Callback (SSHAgentStepExecution execution) {
            this.execution = execution;
        }

        @Override
        protected void finished(StepContext context) throws Exception {
            execution.stop();
        }

    }

    private static final class ExpanderImpl extends EnvironmentExpander {

        private static final long serialVersionUID = 1L;

        private final SSHAgentStepExecution execution;

        ExpanderImpl(SSHAgentStepExecution execution) {
            this.execution = execution;
        }

        @Override
        public void expand(EnvVars env) throws IOException, InterruptedException {
            env.overrideAll(execution.agent.getEnv());
            if (execution.usernameVariable != null && execution.username != null) {
                env.override(execution.usernameVariable, execution.username);
            }
            if (execution.gitSshCommand != null) {
                env.override("GIT_SSH_COMMAND", execution.gitSshCommand);
            }
        }
    }

    /**
     * Initializes a SSH Agent.
     *
     * @throws IOException
     */
    private void initRemoteAgent() throws IOException, InterruptedException {
        Launcher launcher = getContext().get(Launcher.class);
        TaskListener listener = getContext().get(TaskListener.class);
        Run<?, ?> build = getContext().get(Run.class);
        FilePath workspace = getContext().get(FilePath.class);
        List<SSHUserPrivateKey> userPrivateKeys = new ArrayList<>();
        for (String id : new LinkedHashSet<>(step.getCredentials())) {
            final SSHUserPrivateKey c = CredentialsProvider.findCredentialById(id, SSHUserPrivateKey.class, build);
            CredentialsProvider.track(build, c);
            if (c == null && !step.isIgnoreMissing()) {
                throw new AbortException(Messages.SSHAgentBuildWrapper_CredentialsNotFound(id));
            }
            if (c != null && !userPrivateKeys.contains(c)) {
                userPrivateKeys.add(c);
            }
        }
        for (SSHUserPrivateKey userPrivateKey : userPrivateKeys) {
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_UsingCredentials(SSHAgentBuildWrapper.description(userPrivateKey)));
        }

        if (usernameVariable != null && !userPrivateKeys.isEmpty()) {
            username = userPrivateKeys.get(0).getUsername();
        }

        agent = new ExecRemoteAgent(launcher, listener, step.getTimeoutMinutes(), step.getExecutable());

        for (SSHUserPrivateKey userPrivateKey : userPrivateKeys) {
            final Secret passphrase = userPrivateKey.getPassphrase();
            final String effectivePassphrase = passphrase == null ? null : passphrase.getPlainText();
            for (String privateKey : userPrivateKey.getPrivateKeys()) {
                agent.addIdentity(privateKey, effectivePassphrase, SSHAgentBuildWrapper.description(userPrivateKey), workspace, launcher, listener);
            }
        }

        if (hostKeyVerification) {
            initHostKeyVerification(workspace, listener);
        }

        listener.getLogger().println(Messages.SSHAgentBuildWrapper_Started());
    }

    /**
     * Sets up Git host key verification for the block by exposing {@code GIT_SSH_COMMAND} built from
     * the git-client plugin's globally configured verification strategy. The ssh options are computed
     * on the agent so that any temporary known_hosts file is created where the build runs.
     */
    private void initHostKeyVerification(FilePath workspace, TaskListener listener)
            throws IOException, InterruptedException {
        EnvVars contextEnv = getContext().get(EnvVars.class);
        if (contextEnv != null && contextEnv.containsKey("GIT_SSH_COMMAND")) {
            // Respect a GIT_SSH_COMMAND the user already set rather than silently replacing it.
            listener.getLogger().println(
                    "GIT_SSH_COMMAND is already set, so ssh-agent will not override it for host key verification.");
            return;
        }
        HostKeyVerifierFactory verifier = GitHostKeyVerificationConfiguration.get()
                .getSshHostKeyVerificationStrategy()
                .getVerifier();
        FilePath tempDir = WorkspaceList.tempDir(workspace);
        if (tempDir == null) {
            throw new IOException("No temp dir in " + workspace);
        }
        FilePath knownHosts = tempDir.createTempFile("known_hosts", "");
        knownHostsPath = knownHosts.getRemote();
        gitSshCommand = "ssh " + knownHosts.act(new VerifyHostKeyOption(verifier, listener));
    }

    private static final class VerifyHostKeyOption extends MasterToSlaveFileCallable<String> {

        private static final long serialVersionUID = 1L;

        private final HostKeyVerifierFactory verifier;
        private final TaskListener listener;

        VerifyHostKeyOption(HostKeyVerifierFactory verifier, TaskListener listener) {
            this.verifier = verifier;
            this.listener = listener;
        }

        @Override
        public String invoke(File knownHosts, VirtualChannel channel) throws IOException {
            return verifier.forCliGit(listener).getVerifyHostKeyOption(knownHosts.toPath());
        }
    }

}
