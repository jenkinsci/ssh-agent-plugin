package com.cloudbees.jenkins.plugins.sshagent;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.List;

public class SSHAgentStep extends AbstractStepImpl implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The {@link com.cloudbees.plugins.credentials.common.StandardUsernameCredentials#getId()}s of the credentials
     * to use.
     */
    private List<String> credentials;

    /**
     * If a credentials is missed, the SSH Agent is launched anyway.
     */
    private boolean ignoreMissing;

    /**
     * Default parameterized constructor.
     *
     * @param credentials
     * @param ignoreMissing
     */
    @DataBoundConstructor
    public SSHAgentStep(final List<String> credentials, final boolean ignoreMissing) {
        this.credentials = credentials;
        this.ignoreMissing = ignoreMissing;
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

        @Override
        public String getDisplayName() {
            return Messages.SSHAgentBuildWrapper_DisplayName();
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

    }

    @DataBoundSetter
    public void setIgnoreMissing(final boolean ignoreMissing) {
        this.ignoreMissing = ignoreMissing;
    }

    public boolean isIgnoreMissing() {
        return ignoreMissing;
    }

    @DataBoundSetter
    public void setCredentials(final List<String> credentials) {
        this.credentials = credentials;
    }

    public List<String> getCredentials() {
        return credentials;
    }

}
