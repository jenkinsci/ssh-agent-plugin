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
import hudson.util.Secret;
import org.jenkinsci.plugins.workflow.steps.*;

import java.io.IOException;
import java.util.*;

final class SSHAgentStepExecution extends AbstractStepExecutionImpl {

    private static final long serialVersionUID = 1L;

    private transient SSHAgentStep step;

    private ExecRemoteAgent agent;

    SSHAgentStepExecution(SSHAgentStep step, StepContext context) {
        super(context);
        this.step = step;
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

        agent = new ExecRemoteAgent(launcher, listener);

        for (SSHUserPrivateKey userPrivateKey : userPrivateKeys) {
            final Secret passphrase = userPrivateKey.getPassphrase();
            final String effectivePassphrase = passphrase == null ? null : passphrase.getPlainText();
            for (String privateKey : userPrivateKey.getPrivateKeys()) {
                agent.addIdentity(privateKey, effectivePassphrase, SSHAgentBuildWrapper.description(userPrivateKey), workspace, launcher, listener);
            }
        }

        listener.getLogger().println(Messages.SSHAgentBuildWrapper_Started());
    }

}
