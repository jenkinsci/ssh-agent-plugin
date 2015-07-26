package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUser;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.google.inject.Inject;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.*;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class SSHAgentStepExecution extends AbstractStepExecutionImpl {

    /**
     * Serial Version UID.
     */
    private static final long serialVersionUID = -1912198498615126332L;

    @StepContextParameter
    private transient TaskListener listener;

    @StepContextParameter
    private transient FilePath ws;

    @StepContextParameter
    private transient Run build;

    @StepContextParameter
    private transient Launcher launcher;

    @StepContextParameter
    private transient EnvVars env;

    @Inject
    private transient SSHAgentStep step;

    private BodyExecution body;

    /**
     * The proxy for the real remote agent that is on the other side of the channel (as the agent needs to
     * run on a remote machine)
     */
    private transient RemoteAgent agent = null;

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        body = context.newBodyInvoker().withCallback(new Callback()).withDisplayName(null).start();
        initRemoteAgent();
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
            initRemoteAgent();
        } catch (Exception e) {
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_CouldNotStartAgent());
        }
    }

    private class Callback extends BodyExecutionCallback {

        /**
         * Serial Version UID.
         */
        private static final long serialVersionUID = 4118096102821683615L;

        @Override
        public void onSuccess(StepContext context, Object result) {
            if (agent != null) {
                agent.stop();
                listener.getLogger().println(Messages.SSHAgentBuildWrapper_Stopped());
            }
            getContext().onSuccess(result);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            if (agent != null) {
                agent.stop();
                listener.getLogger().println(Messages.SSHAgentBuildWrapper_Stopped());
            }
            getContext().onFailure(t);
        }

    }

    /**
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
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_UsingCredentials(description(userPrivateKey)));
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
                agent.addIdentity(privateKey, effectivePassphrase, description(userPrivateKey));
            }
        }
        env.put("SSH_AUTH_SOCK", agent.getSocket());
        listener.getLogger().println(Messages.SSHAgentBuildWrapper_Started());
    }

    /**
     * Helper method that returns a safe description of a {@link SSHUser}.
     *
     * @param c the credentials.
     * @return the description.
     */
    @Nonnull
    private String description(@Nonnull StandardUsernameCredentials c) {
        String description = Util.fixEmptyAndTrim(c.getDescription());
        return c.getUsername() + (description != null ? " (" + description + ")" : "");
    }
}
