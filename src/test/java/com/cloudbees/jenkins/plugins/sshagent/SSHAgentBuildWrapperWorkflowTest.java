package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class SSHAgentBuildWrapperWorkflowTest extends SSHAgentBase {

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Test
    public void sshAgentAvailable() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                startMockSSHServer();

                List<String> credentialIds = new ArrayList<String>();
                credentialIds.add(CREDENTIAL_ID);

                SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                        new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
                SystemCredentialsProvider.getInstance().getCredentials().add(key);
                SystemCredentialsProvider.getInstance().save();

                WorkflowJob job = story.j.jenkins.createProject(WorkflowJob.class, "sshAgentAvailable");
                job.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  wrap([$class: 'SSHAgentBuildWrapper', credentialHolders: [[id: '" + CREDENTIAL_ID + "']], ignoreMissing: false]) {\n"
                        + "    sh 'ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST + "'\n"
                        + "  }\n"
                        + "}\n", true)
                );
                story.j.assertBuildStatusSuccess(job.scheduleBuild2(0));

                stopMockSSHServer();
            }
        });
    }

    @Test
    public void sshAgentAvailableAfterRestart() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                startMockSSHServer();

                List<String> credentialIds = new ArrayList<String>();
                credentialIds.add(CREDENTIAL_ID);

                SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                        new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
                SystemCredentialsProvider.getInstance().getCredentials().add(key);
                SystemCredentialsProvider.getInstance().save();

                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "sshAgentAvailableAfterRestart");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  wrap([$class: 'SSHAgentBuildWrapper', credentialHolders: [[id: '" + CREDENTIAL_ID + "']], ignoreMissing: false]) {\n"
                        + "    sh 'ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST + "'\n"
                        + "    semaphore 'sshAgentAvailableAfterRestart'\n"
                        + "    sh 'ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST + "'\n"
                        + "  }\n"
                        + "}\n", true));
                // get the build going
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // wait until the executor gets assigned and the execution pauses
                SemaphoreStep.waitForStart("sshAgentAvailableAfterRestart/1", b);
                assertTrue(JenkinsRule.getLog(b), b.isBuilding());
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("sshAgentAvailableAfterRestart", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);

                SemaphoreStep.success("sshAgentAvailableAfterRestart/1", null);

                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));

                stopMockSSHServer();
            }
        });

    }

}