package com.cloudbees.jenkins.plugins.sshagent;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.model.Run;
import hudson.util.Secret;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper.Context;
import jenkins.tasks.SimpleBuildWrapper.Disposer;

import org.apache.commons.lang.StringUtils;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUser;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * TODO: Add description.
 */
public class SSHAgentDisposer extends Disposer {

    /**
     * Serial Version UID.
     */
    private static final long serialVersionUID = -5705766598445563171L;

    /**
     * The proxy for the real remote agent that is on the other side of the
     * channel (as the agent needs to run on a remote machine)
     */
    private RemoteAgent agent = null;

    /**
     * 
     */
    public SSHAgentDisposer(Context context, Run<?, ?> build, Launcher launcher, TaskListener listener, List<String> credentialIds,
            boolean ignoreMissing) throws IOException {
        List<SSHUserPrivateKey> userPrivateKeys = new ArrayList<SSHUserPrivateKey>();
        for (String id: new LinkedHashSet<String>(credentialIds)) {
            final SSHUserPrivateKey c = CredentialsProvider.findCredentialById(id, SSHUserPrivateKey.class, build);
            if (c == null && !ignoreMissing) {
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
        context.env("SSH_AUTH_SOCK", agent.getSocket());
        listener.getLogger().println(Messages.SSHAgentBuildWrapper_Started());

    }

    /* (non-Javadoc)
     * @see jenkins.tasks.SimpleBuildWrapper.Disposer#tearDown(hudson.model.Run, hudson.FilePath, hudson.Launcher, hudson.model.TaskListener)
     */
    @Override
    public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        if (agent != null) {
            agent.stop();
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_Stopped());
        }
    }

    /**
     * Helper method that returns a safe description of a {@link SSHUser}.
     *
     * @param c the credentials.
     * @return the description.
     */
    @NonNull
    private String description(@NonNull StandardUsernameCredentials c) {
        String description = Util.fixEmptyAndTrim(c.getDescription());
        return c.getUsername() + (description != null ? " (" + description + ")" : "");
    }

}
