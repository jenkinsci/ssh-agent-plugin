package com.cloudbees.jenkins.plugins.sshagent;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;

public class SSHAgentStep extends AbstractStepImpl {

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

    }
}
