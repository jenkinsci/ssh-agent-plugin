package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class SSHAgentStep extends AbstractStepImpl implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The {@link com.cloudbees.plugins.credentials.common.StandardUsernameCredentials#getId()}s of the credentials
     * to use.
     */
    private final List<String> credentials;

    /**
     * If a credentials is missed, the SSH Agent is launched anyway.
     * By the fault is false. Initialized in the constructor.
     */
    private boolean ignoreMissing;

    /**
     * Default parameterized constructor.
     *
     * @param credentials
     */
    @DataBoundConstructor
    public SSHAgentStep(final List<String> credentials) {
        this.credentials = credentials;
        this.ignoreMissing = false;
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(SSHAgentStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "sshagent";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SSHAgentBuildWrapper_DisplayName();
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        /**
         * Populate the list of credentials available to the job.
         *
         * @return the list box model.
         */
        @SuppressWarnings("unused") // used by stapler
        public ListBoxModel doFillCredentialsItems(@AncestorInPath Item item) {
            AccessControlled contextToCheck = item == null ? Jenkins.get() : item;
            if (!contextToCheck.hasPermission(CredentialsProvider.VIEW)) {
                return new StandardUsernameListBoxModel();
            }
            return new StandardUsernameListBoxModel()
                    .includeMatchingAs(
                            item instanceof Queue.Task ? Tasks.getAuthenticationOf((Queue.Task)item) : ACL.SYSTEM,
                            item,
                            SSHUserPrivateKey.class,
                            Collections.emptyList(),
                            SSHAuthenticator.matcher()
                    );
        }

    }

    @DataBoundSetter
    public void setIgnoreMissing(final boolean ignoreMissing) {
        this.ignoreMissing = ignoreMissing;
    }

    public boolean isIgnoreMissing() {
        return ignoreMissing;
    }

    public List<String> getCredentials() {
        return credentials;
    }

}
