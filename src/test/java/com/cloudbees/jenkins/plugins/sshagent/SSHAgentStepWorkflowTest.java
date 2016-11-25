package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.Util;
import hudson.model.Fingerprint;
import hudson.util.Secret;
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
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.*;

public class SSHAgentStepWorkflowTest extends SSHAgentBase {

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
                        + "  sshagent (credentials: ['" + CREDENTIAL_ID + "']) {\n"
                        + "    sh 'ls -l $SSH_AUTH_SOCK && ssh -o StrictHostKeyChecking=no -o HostKeyAlgorithms=+ssh-dss -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST + "'\n"
                        + "  }\n"
                        + "}\n", true)
                );
                story.j.assertBuildStatusSuccess(job.scheduleBuild2(0));

                stopMockSSHServer();
            }
        });
    }

    /**
     * This test verifies:
     *
     * 1. The Job is executed successfully
     * 2. SSH_AUTH_SOCK is available before and after Jenkins was restarted
     * 3. SSH_AUTH_SOCK has different values before and after Jenkins was restarted
     *
     * It verifies that {@link SSHAgentStepExecution#onResume()} method is invoked and a new SSH Agent is launched after Jenkins is restarted.
     *
     * @throws Exception
     */
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
                        + "  sshagent (credentials: ['" + CREDENTIAL_ID + "']) {\n"
                        + "    sh 'ssh -o StrictHostKeyChecking=no -o HostKeyAlgorithms=+ssh-dss -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST + "'\n"
                        + "    echo \"SSH Agent before restart ${env.SSH_AUTH_SOCK}\"\n"
                        + "    semaphore 'sshAgentAvailableAfterRestart'\n"
                        + "    sh 'ssh -o StrictHostKeyChecking=no -o HostKeyAlgorithms=+ssh-dss -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST + "'\n"
                        + "    echo \"SSH Agent after restart ${env.SSH_AUTH_SOCK}\"\n"
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

                Pattern pattern = Pattern.compile("(?:SSH Agent (?:before|after) restart )/.+/ssh([0-9])+");
                Scanner sc = new Scanner(b.getLogFile());
                List<String> socketFile = new ArrayList<String>();
                while (sc.hasNextLine()) {
                    String match = sc.findInLine(pattern);
                    if (match != null) {
                        socketFile.add(match);
                    } else {
                        sc.nextLine();
                    }
                }
                sc.close();

                assertEquals(socketFile.toString(), 2, socketFile.size());
                assertNotEquals(socketFile.get(0), socketFile.get(1));
                stopMockSSHServer();
            }
        });

    }

    @Issue("JENKINS-38830")
    @Test
    public void testTrackingOfCredential() {


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

                Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(key);

                WorkflowJob job = story.j.jenkins.createProject(WorkflowJob.class, "sshAgentAvailable");
                job.setDefinition(new CpsFlowDefinition(""
                  + "node {\n"
                  + "  sshagent (credentials: ['" + CREDENTIAL_ID + "']) {\n"
                  + "    sh 'ls -l $SSH_AUTH_SOCK && ssh -o StrictHostKeyChecking=no -o HostKeyAlgorithms=+ssh-dss -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST + "'\n"
                  + "  }\n"
                  + "}\n", true)
                );

                assertThat("No fingerprint created until first use", fingerprint, nullValue());

                story.j.assertBuildStatusSuccess(job.scheduleBuild2(0));

                fingerprint = CredentialsProvider.getFingerprintOf(key);
                assertThat(fingerprint, notNullValue());
                assertThat(fingerprint.getJobs(), hasItem(is(job.getFullName())));

                stopMockSSHServer();
            }
        });
    }
}
