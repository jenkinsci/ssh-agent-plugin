package com.cloudbees.jenkins.plugins.sshagent;

import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class SSHAgentSimpleBuildWrapperWorkflowTest {

    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    public JenkinsRule r = new JenkinsRule();

    public void sshAgentAvailableAfterRestart() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  wrap([$class: 'SSHAgentBuildWrapper', credentialHolders: [[id: '84822271-02d5-47b8-b8ff-c40fef175c29']], ignoreMissing: false]) {\n"
                        + "    sh 'ssh -v -l cloudbees 192.168.1.117 uname'\n"
                        + "    semaphore 'sshAgentAvailableAfterRestart'\n"
                        + "    sh 'ssh -v -l cloudbees 192.168.1.117 uname'\n"
                        + "  }\n"
                        + "}\n" , true));
                // get the build going
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // wait until the executor gets assigned and the execution pauses
                SemaphoreStep.waitForStart("sshAgentAvailableAfterRestart/1", b);
                e.waitForSuspension();
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // resume from where it left off
                SemaphoreStep.success("sshAgentAvailableAfterRestart/1", null);

                // wait until the completion
                while (b.isBuilding()) {
                    e.waitForSuspension();
                }
            }
        });
    }
}
