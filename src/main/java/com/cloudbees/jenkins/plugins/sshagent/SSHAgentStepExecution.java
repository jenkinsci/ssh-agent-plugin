package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.google.inject.Inject;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.*;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class SSHAgentStepExecution extends AbstractStepExecutionImpl {

    private static final long serialVersionUID = 1L;

    @StepContextParameter
    private transient TaskListener listener;

    @StepContextParameter
    private transient Run<?, ?> build;

    @StepContextParameter
    private transient Launcher launcher;

    @StepContextParameter
    private transient FilePath workspace;

    @Inject(optional = true)
    private SSHAgentStep step;

    /**
     * Value for SSH_AUTH_SOCK environment variable.
     */
    private String socket;

    /**
     * Listing of socket files created. Will be used by {@link #purgeSockets()} and {@link #initRemoteAgent()}
     */
    private List<String> sockets;

    /**
     * The proxy for the real remote agent that is on the other side of the channel (as the agent needs to
     * run on a remote machine)
     */
    private transient RemoteAgent agent = null;

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        sockets = new ArrayList<String>();
        initRemoteAgent();
        context.newBodyInvoker().
                withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(this))).
                withCallback(new Callback(this)).withDisplayName(null).start();
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        if (agent != null) {
            agent.stop();
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_Stopped());
        }
        purgeSockets();
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            purgeSockets();
            initRemoteAgent();
        } catch (IOException io) {
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_CouldNotStartAgent());
        }
    }

    // TODO use https://github.com/jenkinsci/jenkins/pull/2066
    static FilePath tempDir(FilePath ws) {
        return ws.sibling(ws.getName() + System.getProperty(WorkspaceList.class.getName(), "@") + "tmp");
    }

    private static class Callback extends BodyExecutionCallback {

        private static final long serialVersionUID = 1L;

        private final SSHAgentStepExecution execution;

        Callback (SSHAgentStepExecution execution) {
            this.execution = execution;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            execution.cleanUp();
            context.onSuccess(result);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            execution.cleanUp();
            context.onFailure(t);
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
            env.override("SSH_AUTH_SOCK", execution.getSocket());
        }
    }

    /**
     * Initializes a SSH Agent.
     *
     * @throws IOException
     */
    private void initRemoteAgent() throws IOException {

        List<SSHUserPrivateKey> userPrivateKeys = new ArrayList<SSHUserPrivateKey>();
        for (String id : new LinkedHashSet<String>(step.getCredentials())) {
            final SSHUserPrivateKey c = CredentialsProvider.findCredentialById(id, SSHUserPrivateKey.class, build);
            if (c == null && !step.isIgnoreMissing()) {
                listener.fatalError(Messages.SSHAgentBuildWrapper_CredentialsNotFound());
            }
            if (c != null && !userPrivateKeys.contains(c)) {
                userPrivateKeys.add(c);
            }
        }
        for (SSHUserPrivateKey userPrivateKey : userPrivateKeys) {
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_UsingCredentials(SSHAgentBuildWrapper.description(userPrivateKey)));
        }

        listener.getLogger().println("[ssh-agent] Looking for ssh-agent implementation...");
        Map<String, Throwable> faults = new LinkedHashMap<String, Throwable>();
        for (RemoteAgentFactory factory : Jenkins.getActiveInstance().getExtensionList(RemoteAgentFactory.class)) {
            if (factory.isSupported(launcher, listener)) {
                try {
                    listener.getLogger().println("[ssh-agent]   " + factory.getDisplayName());
                    agent = factory.start(launcher, listener, tempDir(workspace));
                    break;
                } catch (Throwable t) {
                    faults.put(factory.getDisplayName(), t);
                }
            }
        }
        if (agent == null) {
            listener.getLogger().println("[ssh-agent] FATAL: Could not find a suitable ssh-agent provider");
            listener.getLogger().println("[ssh-agent] Diagnostic report");
            for (Map.Entry<String, Throwable> fault : faults.entrySet()) {
                listener.getLogger().println("[ssh-agent] * " + fault.getKey());
                StringWriter sw = new StringWriter();
                fault.getValue().printStackTrace(new PrintWriter(sw));
                for (String line : StringUtils.split(sw.toString(), "\n")) {
                    listener.getLogger().println("[ssh-agent]     " + line);
                }
            }
            throw new RuntimeException("[ssh-agent] Could not find a suitable ssh-agent provider.");
        }

        for (SSHUserPrivateKey userPrivateKey : userPrivateKeys) {
            final Secret passphrase = userPrivateKey.getPassphrase();
            final String effectivePassphrase = passphrase == null ? null : passphrase.getPlainText();
            for (String privateKey : userPrivateKey.getPrivateKeys()) {
                agent.addIdentity(privateKey, effectivePassphrase, SSHAgentBuildWrapper.description(userPrivateKey));
            }
        }

        listener.getLogger().println(Messages.SSHAgentBuildWrapper_Started());
        socket = agent.getSocket();
        sockets.add(socket);
    }

    /**
     * Shuts down the current SSH Agent and purges socket files.
     */
    private void cleanUp() {
        try {
            TaskListener listener = getContext().get(TaskListener.class);
            if (agent != null) {
                agent.stop();
                listener.getLogger().println(Messages.SSHAgentBuildWrapper_Stopped());
            }
        } catch (Throwable th) {
            getContext().onFailure(th);
        }
        purgeSockets();
    }

    /**
     * Purges all socket files created previously.
     * Especially useful when Jenkins is restarted during the execution of this step.
     */
    private void purgeSockets() {
        Iterator<String> it = sockets.iterator();
        while (it.hasNext()) {
            File socket = new File(it.next());
            if (socket.exists()) {
                if (!socket.delete()) {
                    listener.getLogger().format("It was a problem removing this socket file %s", socket.getAbsolutePath());
                }
            }
            it.remove();
        }
    }

    /**
     * Returns the socket.
     *
     * @return The value that SSH_AUTH_SOCK should be set to.
     */
    @CheckReturnValue private String getSocket() {
        return socket;
    }

}
