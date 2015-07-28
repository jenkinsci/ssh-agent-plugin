package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.google.inject.Inject;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.*;

import javax.annotation.CheckReturnValue;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class SSHAgentStepExecution extends AbstractStepExecutionImpl {

    private static final long serialVersionUID = -1912198498615126332L;

    @StepContextParameter
    private transient TaskListener listener;

    @StepContextParameter
    private transient Run build;

    @StepContextParameter
    private transient Launcher launcher;

    @Inject
    private transient SSHAgentStep step;

    /**
     * The proxy for the real remote agent that is on the other side of the channel (as the agent needs to
     * run on a remote machine)
     */
    private transient RemoteAgent agent = null;

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        String socket = initRemoteAgent();
        // TODO: Remove this message. It's temporal.
        System.out.println("A SSH Agent is started using this socket " + socket);
        context.newBodyInvoker().
                withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(socket))).
                withCallback(new Callback(agent)).withDisplayName(null).start();
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        if (agent != null) {
            agent.stop();
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_Stopped());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            StepContext context = getContext();
            String socket = initRemoteAgent();
            context.newBodyInvoker().
                    withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(socket))).
                    withCallback(new Callback(agent)).withDisplayName(null).start();

        } catch (IOException io) {
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_CouldNotStartAgent());
        } catch (InterruptedException ie) {
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_CouldNotStartAgent());
        }
    }

    private static class Callback extends BodyExecutionCallback {

        private static final long serialVersionUID = 4118096102821683615L;

        private transient RemoteAgent agent = null;

        Callback (final RemoteAgent agent) {
            this.agent = agent;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            //TaskListener listener = context.get(TaskListener.class);
            if (agent != null) {
                String socket = agent.getSocket();
                agent.stop();
                // TODO: Remove this message. It's temporal.
                System.out.println(Messages.SSHAgentBuildWrapper_Stopped() + " Socket: " + socket);
                //listener.getLogger().println(Messages.SSHAgentBuildWrapper_Stopped());
            }
            context.onSuccess(result);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            if (agent != null) {
                String socket = agent.getSocket();
                agent.stop();
                // TODO: Remove this message. It's temporal.
                System.out.println(Messages.SSHAgentBuildWrapper_Stopped() + " Socket: " + socket);
                //listener.getLogger().println(Messages.SSHAgentBuildWrapper_Stopped());
            }
            context.onFailure(t);
        }

    }

    private static final class ExpanderImpl extends EnvironmentExpander {

        private static final long serialVersionUID = 1267620262525665964L;

        /**
         * Value for SSH_AUTH_SOCK environment variable.
         */
        private final String socket;

        private ExpanderImpl(final String socket) {
            this.socket = socket;
        }

        @Override
        public void expand(EnvVars env) throws IOException, InterruptedException {
            env.override("SSH_AUTH_SOCK", socket);
        }
    }

    /**
     * Initializes a SSH Agent.
     *
     * @return The value that SSH_AUTH_SOCK should be set to.
     * @throws IOException
     */
    @CheckReturnValue
    private String initRemoteAgent() throws IOException {

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
                    agent = factory.start(launcher, listener);
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
        listener.getLogger().println(Messages.SSHAgentBuildWrapper_Started() + " Socket: " + agent.getSocket());
        return agent.getSocket();

    }

}
