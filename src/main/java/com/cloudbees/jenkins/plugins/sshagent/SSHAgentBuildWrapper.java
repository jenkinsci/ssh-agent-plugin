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

import com.cloudbees.jenkins.plugins.sshagent.exec.ExecRemoteAgent;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A build wrapper that provides an SSH agent using supplied credentials
 */
public class SSHAgentBuildWrapper extends BuildWrapper {
    /**
     * The {@link StandardUsernameCredentials#getId()} of the credentials to use.
     */
    private transient String user;

    /**
     * The {@link StandardUsernameCredentials#getId()}s of the credentials
     * to use.
     *
     * @since 1.5
     */
    private final List<String> credentialIds;

    /**
     * When {@code true} then any missing credentials will be ignored. When {@code false} then the build will be failed
     * if any of the required credentials cannot be resolved.
     *
     * @since 1.5
     */
    private final boolean ignoreMissing;

    /**
     * Constructs a new instance.
     *
     * @param user the {@link SSHUserPrivateKey#getId()} of the credentials to use.
     * @deprecated use {@link #SSHAgentBuildWrapper(java.util.List,boolean)}
     */
    @Deprecated
    @SuppressWarnings("unused") // used via stapler
    public SSHAgentBuildWrapper(String user) {
        this(Collections.singletonList(user), false);
    }

    /**
     * Constructs a new instance.
     *
     * @param credentialHolders the {@link com.cloudbees.jenkins.plugins.sshagent.SSHAgentBuildWrapper.CredentialHolder}s of the credentials to use.
     * @param ignoreMissing {@code true} missing credentials will not cause a build failure.
     * @since 1.5
     */
    @DataBoundConstructor
    @SuppressWarnings("unused") // used via stapler
    public SSHAgentBuildWrapper(CredentialHolder[] credentialHolders, boolean ignoreMissing) {
        this(CredentialHolder.toIdList(credentialHolders), ignoreMissing);
    }

    /**
     * Constructs a new instance.
     *
     * @param credentialIds the {@link com.cloudbees.plugins.credentials.common.StandardUsernameCredentials#getId()}s
     *                      of the credentials to use.
     * @param ignoreMissing {@code true} missing credentials will not cause a build failure.
     * @since 1.5
     */
    @SuppressWarnings("unused") // used via stapler
    public SSHAgentBuildWrapper(List<String> credentialIds, boolean ignoreMissing) {
        this.credentialIds = new ArrayList<>(new LinkedHashSet<>(credentialIds));
        this.ignoreMissing = ignoreMissing;
    }

    /**
     * Migrate legacy data format.
     *
     * @since 1.5
     */
    private Object readResolve() throws ObjectStreamException {
        if (user != null) {
            return new SSHAgentBuildWrapper(Collections.singletonList(user),false);
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
     * When {@code true} then any missing credentials will be ignored. When {@code false} then the build will be failed
     * if any of the required credentials cannot be resolved.
     * @return {@code true} missing credentials will not cause a build failure.
     */
    @SuppressWarnings("unused") // used via stapler
    public boolean isIgnoreMissing() {
        return ignoreMissing;
    }

    /**
     * Returns the value objects used to hold the credential ids.
     *
     * @return the value objects used to hold the credential ids.
     * @since 1.5
     */
    @SuppressWarnings("unused") // used via stapler
    public CredentialHolder[] getCredentialHolders() {
        List<CredentialHolder> result = new ArrayList<>(credentialIds.size());
        for (String id : credentialIds) {
            result.add(new CredentialHolder(id));
        }
        return result.toArray(new CredentialHolder[0]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preCheckout(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        // first collect all the keys (this is so we can bomb out before starting an agent
        List<SSHUserPrivateKey> keys = new ArrayList<>();
        for (String id : new LinkedHashSet<>(getCredentialIds())) {
            final SSHUserPrivateKey c = CredentialsProvider.findCredentialById(
                    id,
                    SSHUserPrivateKey.class,
                    build
            );
            CredentialsProvider.track(build, c);
            if (c == null && !ignoreMissing) {
                IOException ioe = new IOException(Messages.SSHAgentBuildWrapper_CredentialsNotFound(id));
                ioe.printStackTrace(listener.fatalError(""));
                throw ioe;
            }
            if (c != null && !keys.contains(c)) {
                keys.add(c);
            }
        }

        SSHAgentEnvironment environment = null;
        for (hudson.model.Environment env: build.getEnvironments()) {
            if (env instanceof SSHAgentEnvironment) {
                environment = (SSHAgentEnvironment) env;
                // strictly speaking we should break here, but we continue in case there are multiples
                // the last one wins, so we want the last one
            }
        }
        if (environment == null) {
            // none so let's add one
            environment = createSSHAgentEnvironment(build, launcher, listener);
            build.getEnvironments().add(environment);
        }
        for (SSHUserPrivateKey key : keys) {
            environment.add(key);
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_UsingCredentials(description(key)));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        // Jenkins needs this:
        // null would stop the build, and super implementation throws UnsupportedOperationException
        return new NoOpEnvironment();
    }

    private SSHAgentEnvironment createSSHAgentEnvironment(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        try {
            return new SSHAgentEnvironment(launcher, listener, build.getWorkspace());
        } catch (InterruptedException e) {
            e.printStackTrace(listener.fatalError(Messages.SSHAgentBuildWrapper_CouldNotStartAgent()));
            throw e;
        } catch (Throwable e) {
            throw new IOException(Messages.SSHAgentBuildWrapper_CouldNotStartAgent(), e);
        }
    }

    /**
     * Helper method that returns a safe description of a {@link StandardUsernameCredentials}.
     *
     * @param c the credentials.
     * @return the description.
     */
    @NonNull
    public static String description(@NonNull StandardUsernameCredentials c) {
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
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SSHAgentBuildWrapper_DisplayName();
        }

    }

    /**
     * The SSH Agent environment.
     */
    private class SSHAgentEnvironment extends Environment {
        private final ExecRemoteAgent agent;

        private final Launcher launcher;

        private final FilePath workspace;

        private final BuildListener listener;

        SSHAgentEnvironment(Launcher launcher, BuildListener listener, FilePath workspace) throws Throwable {
            this.launcher = launcher;
            this.workspace = Objects.requireNonNull(workspace);
            this.listener = listener;
            listener.getLogger().println("[ssh-agent] Looking for ssh-agent implementation...");
            agent = new ExecRemoteAgent(launcher, listener);
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_Started());
        }

        /**
         * Adds a key to the agent.
         *
         * @param key the key.
         * @throws IOException if the key cannot be added.
         * @since 1.9
         */
        public void add(SSHUserPrivateKey key) throws IOException, InterruptedException {
            final Secret passphrase = key.getPassphrase();
            final String effectivePassphrase = passphrase == null ? null : passphrase.getPlainText();
            for (String privateKey : key.getPrivateKeys()) {
                agent.addIdentity(privateKey, effectivePassphrase, description(key), workspace, launcher, listener);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void buildEnvVars(Map<String, String> env) {
            env.putAll(agent.getEnv());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean tearDown(AbstractBuild build, BuildListener listener)
                throws IOException, InterruptedException {
            if (agent != null) {
                agent.stop(launcher, listener);
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
            List<String> result = new ArrayList<>(credentialHolders == null ? 0 : credentialHolders.length);
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
            @NonNull
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
            public ListBoxModel doFillIdItems(@AncestorInPath Item item) {
                AccessControlled contextToCheck = item == null ? Jenkins.get() : item;
                if (!contextToCheck.hasPermission(CredentialsProvider.VIEW)) {
                        return new StandardUsernameListBoxModel();
                }
                return new StandardUsernameListBoxModel()
                        .includeMatchingAs(
                                item instanceof Queue.Task ? Tasks.getAuthenticationOf2((Queue.Task) item) : ACL.SYSTEM2,
                                item,
                                SSHUserPrivateKey.class,
                                Collections.emptyList(),
                                SSHAuthenticator.matcher()
                        );
            }
        }
    }

    private class NoOpEnvironment extends Environment {
    }

}
