/*
 * The MIT License
 *
 * Copyright (c) 2012, CloudBees, Inc., Stephen Connolly.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUser;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.security.ACL;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import org.apache.commons.lang.StringUtils;
import org.apache.sshd.agent.unix.AgentServer;
import org.apache.sshd.common.util.SecurityUtils;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

import java.io.IOException;
import java.io.StringReader;
import java.security.KeyPair;
import java.util.Map;

/**
 * A build wrapper that provides an SSH agent using supplied credentials
 */
public class SSHAgentBuildWrapper extends BuildWrapper {
    /**
     * The {@link com.cloudbees.jenkins.plugins.sshcredentials.SSHUser#getId()} of the credentials to use.
     */
    private final String user;

    /**
     * Constructs a new instance.
     *
     * @param user the {@link com.cloudbees.jenkins.plugins.sshcredentials.SSHUser#getId()} of the credentials to use.
     */
    @DataBoundConstructor
    @SuppressWarnings("unused") // used via stapler
    public SSHAgentBuildWrapper(String user) {
        this.user = user;
    }

    /**
     * Gets the {@link com.cloudbees.jenkins.plugins.sshcredentials.SSHUser#getId()} of the credentials to use.
     *
     * @return the {@link com.cloudbees.jenkins.plugins.sshcredentials.SSHUser#getId()} of the credentials to use.
     */
    @SuppressWarnings("unused") // used via stapler
    public String getUser() {
        return user;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        SSHUserPrivateKey userPrivateKey = null;
        for (SSHUserPrivateKey u : CredentialsProvider
                .lookupCredentials(SSHUserPrivateKey.class, build.getProject(), ACL.SYSTEM)) {
            if (user.equals(u.getId())) {
                userPrivateKey = u;
                break;
            }
        }
        if (userPrivateKey == null) {
            listener.fatalError(Messages.SSHAgentBuildWrapper_CredentialsNotFound());
            return null;
        }
        listener.getLogger().println(Messages.SSHAgentBuildWrapper_UsingCredentials(description(userPrivateKey)));
        try {
            return new SSHAgentEnvironment(launcher, listener, userPrivateKey);
        } catch (Throwable e) {
            listener.fatalError(Messages.SSHAgentBuildWrapper_CouldNotStartAgent());
            e.printStackTrace(listener.getLogger());
            return null;
        }
    }

    /**
     * Helper method that returns a safe description of a {@link SSHUser}.
     *
     * @param sshUser the credentials.
     * @return the description.
     */
    @NonNull
    private static String description(@NonNull SSHUser sshUser) {
        return StringUtils.isEmpty(sshUser.getDescription()) ? sshUser.getUsername() : sshUser.getDescription();
    }

    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.SSHAgentBuildWrapper_DisplayName();
        }

        /**
         * Populate the list of credentials available to the job.
         *
         * @return the list box model.
         */
        @SuppressWarnings("unused") // used by stapler
        public ListBoxModel doFillUserItems() {
            ListBoxModel m = new ListBoxModel();

            Item item = Stapler.getCurrentRequest().findAncestorObject(Item.class);
            // we only want the users with private keys as they are the only ones valid for an agent
            for (SSHUser u : CredentialsProvider.lookupCredentials(SSHUserPrivateKey.class, item, ACL.SYSTEM)) {
                m.add(description(u), u.getId());
            }

            return m;
        }

    }

    /**
     * Need an interface in order to export the object from the channel.
     */
    public static interface RemoteAgent {
        /**
         * Returns the value that the environment variable should be set to.
         *
         * @return
         */
        String getSocket();

        /**
         * Adds the provided identity to the agent.
         *
         * @param privateKey the private key.
         * @param passphrase the passphrase or {@code null}.
         * @param comment    the comment to give to the key.
         * @throws IOException if something went wrong.
         */
        void addIdentity(String privateKey, String passphrase, String comment) throws IOException;

        /**
         * Stops the agent.
         */
        void stop();
    }

    /**
     * An implementation that uses Apache SSH to provide the Agent. This requires Tomcat-Native.
     */
    public static class RemoteAgentImpl implements RemoteAgent {
        /**
         * Our agent.
         */
        private final AgentServer agent;
        /**
         * The socket bound by the agent.
         */
        private final String socket;
        /**
         * The listener in case we need to report exceptions
         */
        private final BuildListener listener;

        /**
         * Constructor.
         *
         * @param listener the listener.
         * @throws Exception if the agent could not start.
         */
        public RemoteAgentImpl(BuildListener listener) throws Exception {
            this.listener = listener;
            agent = new AgentServer();
            socket = agent.start();
        }

        /**
         * {@inheritDoc}
         */
        public String getSocket() {
            return socket;
        }

        /**
         * {@inheritDoc}
         */
        public void addIdentity(String privateKey, final String passphrase, String comment) throws IOException {
            if (!SecurityUtils.isBouncyCastleRegistered()) {
                throw new IllegalStateException("BouncyCastle must be registered as a JCE provider");
            }
            try {
                PEMReader r = new PEMReader(new StringReader(privateKey),
                        passphrase == null ? null : new PasswordFinder() {
                            public char[] getPassword() {
                                return passphrase.toCharArray();
                            }
                        });
                try {
                    Object o = r.readObject();
                    if (o instanceof KeyPair) {
                        agent.getAgent().addIdentity((KeyPair) o, comment);
                    }
                } finally {
                    r.close();
                }
            } catch (Exception e) {
                listener.getLogger().println(Messages.SSHAgentBuildWrapper_UnableToReadKey(e.getMessage()));
                e.printStackTrace(listener.getLogger());
            }
        }

        /**
         * {@inheritDoc}
         */
        public void stop() {
            agent.close();
        }
    }

    /**
     * Callable to start the remote agent.
     */
    public static class RemoteAgentStarter implements Callable<RemoteAgent, Throwable> {
        /**
         * Need to pass this through.
         */
        private final BuildListener listener;

        /**
         * Constructor.
         *
         * @param listener the listener to pass to the agent.
         */
        public RemoteAgentStarter(BuildListener listener) {
            this.listener = listener;
        }

        /**
         * {@inheritDoc}
         */
        public RemoteAgent call() throws Throwable {
            return Channel.current().export(RemoteAgent.class, new RemoteAgentImpl(listener));
        }
    }

    /**
     * The SSH Agent environment.
     */
    private class SSHAgentEnvironment extends Environment {
        /**
         * The proxy for the real remote agent that is on the other side of the channel (as the agent needs to
         * run on a remote machine)
         */
        private final RemoteAgent agent;

        /**
         * Construct the environment and initialize on the remote node.
         *
         * @param launcher          the launcher for the remote node.
         * @param listener          the listener for reporting progress.
         * @param sshUserPrivateKey the private key to add to the agent.
         * @throws Throwable if things go wrong.
         */
        public SSHAgentEnvironment(Launcher launcher, final BuildListener listener,
                                   final SSHUserPrivateKey sshUserPrivateKey) throws Throwable {
            agent = launcher.getChannel().call(new RemoteAgentStarter(listener));
            final Secret passphrase = sshUserPrivateKey.getPassphrase();
            agent.addIdentity(sshUserPrivateKey.getPrivateKey(),
                    passphrase == null ? null : passphrase.getPlainText(),
                    description(sshUserPrivateKey));
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_Started());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void buildEnvVars(Map<String, String> env) {
            env.put("SSH_AUTH_SOCK", agent.getSocket());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean tearDown(AbstractBuild build, BuildListener listener)
                throws IOException, InterruptedException {
            if (agent != null) {
                agent.stop();
                listener.getLogger().println(Messages.SSHAgentBuildWrapper_Stopped());
            }
            return true;
        }
    }
}
