package com.cloudbees.jenkins.plugins.sshagent;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.annotation.Nonnull;

public class SSHAgentStepExecution extends AbstractStepExecutionImpl {

    /**
     * Serial Version UID.
     */
    private static final long serialVersionUID = -1912198498615126332L;

    @StepContextParameter
    private transient TaskListener listener;

    @StepContextParameter
    private transient FilePath ws;

    @StepContextParameter
    private transient Run build;

    @StepContextParameter
    private transient Launcher launcher;

    /**
     * TODO: Add description.
     *
     * @return
     * @throws Exception
     */
    public boolean start() throws Exception {
        return true;
    }
    /**
     * TODO: Add description.
     *
     * @param cause
     * @throws Exception
     */
    public void stop(@Nonnull Throwable cause) throws Exception {

    }
}
