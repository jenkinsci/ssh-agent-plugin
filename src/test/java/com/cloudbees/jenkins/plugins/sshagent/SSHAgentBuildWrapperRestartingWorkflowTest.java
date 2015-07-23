package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class SSHAgentBuildWrapperRestartingWorkflowTest extends SSHAgentBase {

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Inject
    public Jenkins jenkins;

    @Test
    public void sshAgentAvailableAfterRestart() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                startMockSSHServer();

                List<String> credentialIds = new ArrayList<String>();
                credentialIds.add("84822271-02d5-47b8-b8ff-c40fef175c29");

                SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                        new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
                SystemCredentialsProvider.getInstance().getCredentials().add(key);
                SystemCredentialsProvider.getInstance().save();

                WorkflowJob p = jenkins.createProject(WorkflowJob.class, "sshAgentAvailableAfterRestart");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  wrap([$class: 'SSHAgentBuildWrapper', credentialHolders: [[id: '84822271-02d5-47b8-b8ff-c40fef175c29']], ignoreMissing: false]) {\n"
                        + "    sh 'ssh -o StrictHostKeyChecking=no -p " + SSH_SERVER_PORT + " -v -l cloudbees " + SSH_SERVER_HOST + "'\n"
                        + "    semaphore 'sshAgentAvailableAfterRestart'\n"
                        + "    sh 'ssh -o StrictHostKeyChecking=no -p " + SSH_SERVER_PORT + " -v -l cloudbees " + SSH_SERVER_HOST + "'\n"
                        + "  }\n"
                        + "}\n", true));
                // get the build going
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // wait until the executor gets assigned and the execution pauses
                SemaphoreStep.waitForStart("sshAgentAvailableAfterRestart/1", b);
                //e.waitForSuspension();
                assertTrue(JenkinsRule.getLog(b), b.isBuilding());
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = jenkins.getItemByFullName("sshAgentAvailableAfterRestart", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // resume from where it left off
                SemaphoreStep.success("sshAgentAvailableAfterRestart/1", null);

                // wait until the completion
                while (b.isBuilding()) {
                    e.waitForSuspension();
                }

                System.out.println(JenkinsRule.getLog(b));
                story.j.assertBuildStatusSuccess(b);
                stopMockSSHServer();
            }
        });

    }

}
