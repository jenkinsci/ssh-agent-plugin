package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

public class SSHAgentStep extends Step {

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
    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new SSHAgentStepExecution(this, context);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

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

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Launcher.class, TaskListener.class, Run.class, FilePath.class);
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
                            item instanceof Queue.Task ? Tasks.getAuthenticationOf2((Queue.Task)item) : ACL.SYSTEM2,
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
