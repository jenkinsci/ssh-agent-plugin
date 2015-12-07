package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Project;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.JobDecorator;
import jenkins.branch.ProjectDecorator;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A SSH Agent providing {@link BranchProperty} that is aware of any other {@link SSHAgentBuildWrapper} injected into
 * the environment, for example by an SCM provider, and will preserve their credentials in the new agent that
 * is injected into the build environment.
 *
 * @since 1.9
 */
public class SSHAgentBranchProperty extends BranchProperty {

    /**
     * The {@link StandardUsernameCredentials#getId()}s of the credentials
     * to use.
     */
    private final List<String> credentialIds;

    /**
     * The {@link StandardUsernameCredentials#getId()}s of the credentials
     * to use.
     */
    private final boolean ignoreMissing;

    @DataBoundConstructor
    public SSHAgentBranchProperty(SSHAgentBuildWrapper.CredentialHolder[] credentialHolders, boolean ignoreMissing) {
        this.credentialIds = SSHAgentBuildWrapper.CredentialHolder.toIdList(credentialHolders);
        this.ignoreMissing = ignoreMissing;
    }

    public SSHAgentBranchProperty(List<String> credentialIds, boolean ignoreMissing) {
        this.credentialIds = credentialIds;
        this.ignoreMissing = ignoreMissing;
    }

    /**
     * Gets the {@link com.cloudbees.plugins.credentials.common.StandardUsernameCredentials#getId()}s of the
     * credentials to use.
     *
     * @return the {@link com.cloudbees.plugins.credentials.common.StandardUsernameCredentials#getId()}s of the
     * credentials to use.
     */
    public List<String> getCredentialIds() {
        return Collections.unmodifiableList(credentialIds);
    }

    /**
     * When {@code true} then any missing credentials will be ignored. When {@code false} then the build will be failed
     * if any of the required credentials cannot be resolved.
     *
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
     */
    @SuppressWarnings("unused") // used via stapler
    public SSHAgentBuildWrapper.CredentialHolder[] getCredentialHolders() {
        return SSHAgentBuildWrapper.CredentialHolder.toCredentialHolders(credentialIds);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public <P extends Job<P, B>, B extends Run<P, B>> JobDecorator<P, B> jobDecorator(Class<P> clazz) {
        return AbstractProject.class.isAssignableFrom(clazz) ? new ProjectDecoratorImpl() : null;
    }

    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends BranchPropertyDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.SSHAgentBranchProperty_DisplayName();
        }
    }

    private class ProjectDecoratorImpl<P extends Project<P, B>, B extends Build<P, B>> extends ProjectDecorator<P,B> {
        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public List<BuildWrapper> buildWrappers(@NonNull List<BuildWrapper> wrappers) {
            // because you can only ever have one agent active (as they all fight over SSH_AUTH_SOCK
            // we need to catch any other ones that might be there and merge their credentials into ours
            // but we need to ensure that
            List<BuildWrapper> result = new ArrayList<BuildWrapper>(wrappers.size() + 1);
            SSHAgentBuildWrapper agent = null;
            for (BuildWrapper w : wrappers) {
                if (w instanceof BranchSSHAgentBuildWrapper) {
                    // we injected this, so drop it
                    continue;
                }
                if (w instanceof SSHAgentBuildWrapper) {
                    agent = (SSHAgentBuildWrapper) w;
                } else {
                    result.add(w);
                }
            }
            if (agent == null) {
                result.add(new BranchSSHAgentBuildWrapper(getCredentialIds(), isIgnoreMissing()));
            } else {
                Set<String> dedup = new LinkedHashSet<String>(agent.getCredentialIds());
                dedup.addAll(getCredentialIds());
                result.add(new BranchSSHAgentBuildWrapper(new ArrayList<String>(dedup), agent.isIgnoreMissing() || isIgnoreMissing()));
            }
            return result;
        }
    }

    /**
     * A marking subclass of {@link BranchSSHAgentBuildWrapper} to allow detection of manually added additional build
     * wrappers.
     */
    public static class BranchSSHAgentBuildWrapper extends SSHAgentBuildWrapper {

        @DataBoundConstructor
        public BranchSSHAgentBuildWrapper(
                CredentialHolder[] credentialHolders, boolean ignoreMissing) {
            super(credentialHolders, ignoreMissing);
        }

        public BranchSSHAgentBuildWrapper(List<String> credentialIds, boolean ignoreMissing) {
            super(credentialIds, ignoreMissing);
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        @Override
        public Descriptor<BuildWrapper> getDescriptor() {
            return Jenkins.getActiveInstance().getDescriptorOrDie(SSHAgentBuildWrapper.class);
        }
    }

}
