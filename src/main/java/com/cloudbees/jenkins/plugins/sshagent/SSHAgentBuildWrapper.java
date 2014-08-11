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
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.IOException2;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A build wrapper that provides an SSH agent using supplied credentials
 */
public class SSHAgentBuildWrapper extends BuildWrapper {
    /**
     * The {@link com.cloudbees.jenkins.plugins.sshcredentials.SSHUser#getId()} of the credentials to use.
     */
    private transient String user;

    /**
     * The {@link com.cloudbees.plugins.credentials.common.StandardUsernameCredentials#getId()}s of the credentials
     * to use.
     *
     * @since 1.5
     */
    private final List<String> credentialIds;

    /**
     * Constructs a new instance.
     *
     * @param user the {@link SSHUserPrivateKey#getId()} of the credentials to use.
     * @deprecated use {@link #SSHAgentBuildWrapper(java.util.List)}
     */
    @Deprecated
    @SuppressWarnings("unused") // used via stapler
    public SSHAgentBuildWrapper(String user) {
        this(Collections.singletonList(user));
    }

    /**
     * Constructs a new instance.
     *
     * @param credentialHolders the {@link com.cloudbees.plugins.credentials.common.StandardUsernameCredentials#getId
     * ()}s
     *                          of the credentials to use.
     * @since 1.5
     */
    @DataBoundConstructor
    @SuppressWarnings("unused") // used via stapler
    public SSHAgentBuildWrapper(CredentialHolder[] credentialHolders) {
        this(CredentialHolder.toIdList(credentialHolders));
    }

    /**
     * Constructs a new instance.
     *
     * @param credentialIds the {@link com.cloudbees.plugins.credentials.common.StandardUsernameCredentials#getId()}s
     *                      of the credentials to use.
     * @since 1.5
     */
    @SuppressWarnings("unused") // used via stapler
    public SSHAgentBuildWrapper(List<String> credentialIds) {
        this.credentialIds = new ArrayList<String>(new LinkedHashSet<String>(credentialIds));
    }

    /**
     * Migrate legacy data format.
     *
     * @since 1.5
     */
    private Object readResolve() throws ObjectStreamException {
        if (user != null) {
            return new SSHAgentBuildWrapper(Collections.singletonList(user));
        }
        return this;
    }

    /**
     * Gets the {@link SSHUserPrivateKey#getId()} of the credentials to use.
     *
     * @return the {@link SSHUserPrivateKey#getId()} of the credentials to use.
     */
    @SuppressWarnings("unused") // used via stapler
    @Deprecated
    public String getUser() {
        return credentialIds.isEmpty() ? null : credentialIds.get(0);
    }

    /**
     * Gets the {@link com.cloudbees.plugins.credentials.common.StandardUsernameCredentials#getId()}s of the
     * credentials to use.
     *
     * @return the {@link com.cloudbees.plugins.credentials.common.StandardUsernameCredentials#getId()}s of the
     * credentials to use.
     * @since 1.5
     */
    public List<String> getCredentialIds() {
        return Collections.unmodifiableList(credentialIds);
    }

    /**
     * Returns the value objects used to hold the credential ids.
     *
     * @return the value objects used to hold the credential ids.
     * @since 1.5
     */
    @SuppressWarnings("unused") // used via stapler
    public CredentialHolder[] getCredentialHolders() {
        List<CredentialHolder> result = new ArrayList<CredentialHolder>(credentialIds.size());
        for (String id : credentialIds) {
            result.add(new CredentialHolder(id));
        }
        return result.toArray(new CredentialHolder[result.size()]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preCheckout(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        build.getEnvironments().add(createSSHAgentEnvironment(build, launcher, listener));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        // Jenkins needs this:
        // null would stop the build, and super implementation throws UnsupportedOperationException
        return new Environment() {
        };
    }

    private Environment createSSHAgentEnvironment(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        List<SSHUserPrivateKey> userPrivateKeys = new ArrayList<SSHUserPrivateKey>();
        Set<String> ids = new LinkedHashSet<String>(getCredentialIds());
        for (SSHUserPrivateKey u : CredentialsProvider.lookupCredentials(SSHUserPrivateKey.class, build.getProject(),
                ACL.SYSTEM, Collections.<DomainRequirement>emptyList())) {
            if (ids.contains(u.getId())) {
                userPrivateKeys.add(u);
            }
        }
        if (userPrivateKeys.isEmpty()) {
            listener.fatalError(Messages.SSHAgentBuildWrapper_CredentialsNotFound());
            return null;
        }
        for (SSHUserPrivateKey userPrivateKey : userPrivateKeys) {
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_UsingCredentials(description(userPrivateKey)));
        }
        try {
            return new SSHAgentEnvironment(launcher, listener, userPrivateKeys);
        } catch (IOException e) {
            throw new IOException2(Messages.SSHAgentBuildWrapper_CouldNotStartAgent(), e);
        } catch (InterruptedException e) {
            e.printStackTrace(listener.fatalError(Messages.SSHAgentBuildWrapper_CouldNotStartAgent()));
            throw e;
        } catch (Throwable e) {
            throw new IOException2(Messages.SSHAgentBuildWrapper_CouldNotStartAgent(), e);
        }
    }

    /**
     * Helper method that returns a safe description of a {@link SSHUser}.
     *
     * @param c the credentials.
     * @return the description.
     */
    @NonNull
    private static String description(@NonNull StandardUsernameCredentials c) {
        String description = Util.fixEmptyAndTrim(c.getDescription());
        return c.getUsername() + (description != null ? " (" + description + ")" : "");
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
         * @deprecated use {@link #SSHAgentEnvironment(hudson.Launcher, hudson.model.BuildListener, java.util.List)}
         */
        @Deprecated
        public SSHAgentEnvironment(Launcher launcher, final BuildListener listener,
                                   final SSHUserPrivateKey sshUserPrivateKey) throws Throwable {
            this(launcher, listener, Collections.singletonList(sshUserPrivateKey));
        }

        /**
         * Construct the environment and initialize on the remote node.
         *
         * @param launcher           the launcher for the remote node.
         * @param listener           the listener for reporting progress.
         * @param sshUserPrivateKeys the private keys to add to the agent.
         * @throws Throwable if things go wrong.
         * @since 1.5
         */
        public SSHAgentEnvironment(Launcher launcher, final BuildListener listener,
                                   final List<SSHUserPrivateKey> sshUserPrivateKeys) throws Throwable {
            RemoteAgent agent = null;
            listener.getLogger().println("[ssh-agent] Looking for ssh-agent implementation...");
            Map<String, Throwable> faults = new LinkedHashMap<String, Throwable>();
            for (RemoteAgentFactory factory : Hudson.getInstance().getExtensionList(RemoteAgentFactory.class)) {
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
            this.agent = agent;
            for (SSHUserPrivateKey sshUserPrivateKey : sshUserPrivateKeys) {
                final Secret passphrase = sshUserPrivateKey.getPassphrase();
                final String effectivePassphrase = passphrase == null ? null : passphrase.getPlainText();
                for (String privateKey : sshUserPrivateKey.getPrivateKeys()) {
                    agent.addIdentity(privateKey, effectivePassphrase, description(sshUserPrivateKey));
                }
            }
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

    /**
     * A value object to make it possible to pass back multiple credentials via the UI.
     *
     * @since 1.5
     */
    public static class CredentialHolder extends AbstractDescribableImpl<CredentialHolder> {

        /**
         * The id.
         */
        private final String id;

        /**
         * Stapler's constructor.
         *
         * @param id the ID.
         */
        @DataBoundConstructor
        public CredentialHolder(String id) {
            this.id = id;
        }

        /**
         * Gets the id.
         *
         * @return the id.
         */
        public String getId() {
            return id;
        }

        /**
         * Converts an array of value objects into a list of ids.
         *
         * @param credentialHolders the array of value objects.
         * @return the possibly empty but never null list of ids.
         */
        @NonNull
        public static List<String> toIdList(@Nullable CredentialHolder[] credentialHolders) {
            List<String> result = new ArrayList<String>(credentialHolders == null ? 0 : credentialHolders.length);
            if (credentialHolders != null) {
                for (CredentialHolder h : credentialHolders) {
                    result.add(h.getId());
                }
            }
            return result;
        }

        /**
         * Our descriptor.
         */
        @Extension
        public static class DescriptorImpl extends Descriptor<CredentialHolder> {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.SSHAgentBuildWrapper_CredentialHolder_DisplayName();
            }

            /**
             * Populate the list of credentials available to the job.
             *
             * @return the list box model.
             */
            @SuppressWarnings("unused") // used by stapler
            public ListBoxModel doFillIdItems() {
                Item item = Stapler.getCurrentRequest().findAncestorObject(Item.class);
                return new SSHUserListBoxModel().withAll(
                        CredentialsProvider.lookupCredentials(SSHUserPrivateKey.class, item, ACL.SYSTEM,
                                Collections.<DomainRequirement>emptyList())
                );
            }

        }
    }
}
