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

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractDescribableImpl;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import jenkins.tasks.SimpleBuildWrapper;

/**
 * A build wrapper that provides an SSH agent using supplied credentials
 */
public class SSHAgentBuildWrapper extends SimpleBuildWrapper {
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
     * @param ignoreMissing
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
     * @since 1.5
     */
    @SuppressWarnings("unused") // used via stapler
    public SSHAgentBuildWrapper(List<String> credentialIds, boolean ignoreMissing) {
        this.credentialIds = new ArrayList<String>(new LinkedHashSet<String>(credentialIds));
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
        List<CredentialHolder> result = new ArrayList<CredentialHolder>(credentialIds.size());
        for (String id : credentialIds) {
            result.add(new CredentialHolder(id));
        }
        return result.toArray(new CredentialHolder[result.size()]);
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener,
            EnvVars initialEnvironment) throws IOException, InterruptedException {
        context.setDisposer(new SSHAgentDisposer(context, build, launcher, listener, getCredentialIds() ,ignoreMissing));
    }

    /**
      * {@inheritDoc}
      */
    @Override
    protected boolean runPreCheckout() {
        return true;
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
