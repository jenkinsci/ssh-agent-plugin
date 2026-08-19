package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.Fingerprint;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import hudson.util.StreamTaskListener;
import java.io.IOException;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.docker.workflow.client.DockerClient;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class SSHAgentStepWorkflowTest extends SSHAgentBase {

    @RegisterExtension
    private final JenkinsSessionExtension story = new JenkinsSessionExtension();

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    @Test
    void sshAgentAvailable() throws Throwable {
        story.then(j -> {
                startMockSSHServer();

                List<String> credentialIds = new ArrayList<>();
                credentialIds.add(CREDENTIAL_ID);

                SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                        new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
                SystemCredentialsProvider.getInstance().getCredentials().add(key);
                SystemCredentialsProvider.getInstance().save();

                WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "sshAgentAvailable");
                job.setDefinition(new CpsFlowDefinition(""
                        + "node('" + j.createSlave().getNodeName() + "') {\n"
                        + "  sshagent (credentials: ['" + CREDENTIAL_ID + "']) {\n"
                        + "    sh 'ls -l $SSH_AUTH_SOCK && ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST + "'\n"
                        + "  }\n"
                        + "}\n", true)
                );
                j.assertBuildStatusSuccess(job.scheduleBuild2(0));

                stopMockSSHServer();
            }
        );
    }

    /**
     * Verifies that a failed {@code ssh-agent -k} during teardown does not fail an otherwise
     * successful build. The build stops the agent from inside the block, so the automatic teardown
     * finds it already gone; with best-effort teardown the build still succeeds and the failure is
     * only logged (JENKINS-43716).
     */
    @Test
    void teardownDoesNotFailBuildWhenAgentAlreadyStopped() throws Throwable {
        story.then(j -> {
                List<String> credentialIds = new ArrayList<>();
                credentialIds.add(CREDENTIAL_ID);

                SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                        new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
                SystemCredentialsProvider.getInstance().getCredentials().add(key);
                SystemCredentialsProvider.getInstance().save();

                WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "teardownBestEffort");
                job.setDefinition(new CpsFlowDefinition(""
                        + "node('" + j.createSlave().getNodeName() + "') {\n"
                        + "  sshagent (credentials: ['" + CREDENTIAL_ID + "']) {\n"
                        + "    sh 'ssh-agent -k'\n"
                        + "  }\n"
                        + "}\n", true)
                );
                // The build must still succeed even though the agent was already killed inside the
                // block. Whether teardown's own ssh-agent -k reports a failure depends on whether the
                // environment reaps the orphaned agent process (an init running as PID 1); with no
                // reaper the stale PID lingers and the kill reports success instead. So we only assert
                // that the build is not failed, not on the teardown message.
                j.assertBuildStatusSuccess(job.scheduleBuild2(0));
            }
        );
    }

    /**
     * Verifies that the optional usernameVariable exposes the credential's SSH username inside the
     * block, so it can be passed to {@code ssh -l} (JENKINS-45312).
     */
    @Test
    void exposesCredentialUsernameViaUsernameVariable() throws Throwable {
        story.then(j -> {
                List<String> credentialIds = new ArrayList<>();
                credentialIds.add(CREDENTIAL_ID);

                SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                        new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
                SystemCredentialsProvider.getInstance().getCredentials().add(key);
                SystemCredentialsProvider.getInstance().save();

                WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "usernameVariable");
                job.setDefinition(new CpsFlowDefinition(""
                        + "node('" + j.createSlave().getNodeName() + "') {\n"
                        + "  sshagent (credentials: ['" + CREDENTIAL_ID + "'], usernameVariable: 'SSH_USER') {\n"
                        + "    sh 'echo user=$SSH_USER'\n"
                        + "  }\n"
                        + "}\n", true)
                );
                WorkflowRun run = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
                j.assertLogContains("user=cloudbees", run);
            }
        );
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
    void sshAgentAvailableAfterRestart() throws Throwable {
        story.then(j -> {
                startMockSSHServer();

                List<String> credentialIds = new ArrayList<>();
                credentialIds.add(CREDENTIAL_ID);

                SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                        new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
                SystemCredentialsProvider.getInstance().getCredentials().add(key);
                SystemCredentialsProvider.getInstance().save();

                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "sshAgentAvailableAfterRestart");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  sshagent (credentials: ['" + CREDENTIAL_ID + "']) {\n"
                        + "    sh 'ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST + "'\n"
                        + "    echo \"SSH Agent before restart ${env.SSH_AUTH_SOCK}\"\n"
                        + "    semaphore 'sshAgentAvailableAfterRestart'\n"
                        + "    sh 'ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST + "'\n"
                        + "    echo \"SSH Agent after restart ${env.SSH_AUTH_SOCK}\"\n"
                        + "  }\n"
                        + "}\n", true));
                // get the build going
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // wait until the executor gets assigned and the execution pauses
                SemaphoreStep.waitForStart("sshAgentAvailableAfterRestart/1", b);
                assertTrue(b.isBuilding(), JenkinsRule.getLog(b));
            }
        );
        story.then(j -> {
                WorkflowJob p = j.jenkins.getItemByFullName("sshAgentAvailableAfterRestart", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);

                SemaphoreStep.success("sshAgentAvailableAfterRestart/1", null);

                j.assertBuildStatusSuccess(j.waitForCompletion(b));

                // The socket path layout is an ssh-agent implementation detail and varies by platform
                // (for example macOS uses ~/.ssh/agent/s.<id>.agent.<id> rather than /tmp/ssh-XXXX/agent.<pid>),
                // so match on the log label and capture whatever socket path follows.
                Pattern pattern = Pattern.compile("(?:SSH Agent (?:before|after) restart )\\S+");
                Scanner sc = new Scanner(b.getLogFile());
                List<String> socketFile = new ArrayList<>();
                while (sc.hasNextLine()) {
                    String match = sc.findInLine(pattern);
                    if (match != null) {
                        socketFile.add(match);
                    } else {
                        sc.nextLine();
                    }
                }
                sc.close();

                assertEquals(2, socketFile.size(), socketFile.toString());
                assertNotEquals(socketFile.get(0), socketFile.get(1));
                stopMockSSHServer();
            }
        );

    }

    /**
     * This test verifies that sshAgent step handles that the build agent
     * disconnects and reconnects during the step execution.
     */
    @Issue("JENKINS-59259")
    @Test
    void agentConnectionDropTest() throws Throwable {
        story.then(r -> {
            List<String> credentialIds = new ArrayList<>();
            credentialIds.add(CREDENTIAL_ID);
            SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                    new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
            SystemCredentialsProvider.getInstance().getCredentials().add(key);
            SystemCredentialsProvider.getInstance().save();

            DumbSlave agent = r.createSlave(true);
            WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "sshAgentAvailable");
            job.setDefinition(new CpsFlowDefinition(""
                    + "node('" + agent.getNodeName() + "') {\n"
                    + "  sshagent (credentials: ['" + CREDENTIAL_ID + "']) {\n"
                    + "    semaphore 'upAndRunning'\n"
                    + "  }\n"
                    + "}\n", true)
            );

            WorkflowRun run = job.scheduleBuild2(0).getStartCondition().get();

            SemaphoreStep.waitForStart("upAndRunning/1", run);

            r.disconnectSlave(agent);
            r.waitOnline(agent);

            SemaphoreStep.success("upAndRunning/1", null);

            r.waitForCompletion(run);
            r.assertBuildStatusSuccess(run);
        });
    }

    @Issue("JENKINS-38830")
    @Test
    void testTrackingOfCredential() throws Throwable {
        story.then(j -> {
                startMockSSHServer();

                List<String> credentialIds = new ArrayList<>();
                credentialIds.add(CREDENTIAL_ID);

                SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                  new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
                SystemCredentialsProvider.getInstance().getCredentials().add(key);
                SystemCredentialsProvider.getInstance().save();

                Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(key);

                WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "sshAgentAvailable");
                job.setDefinition(new CpsFlowDefinition(""
                  + "node {\n"
                  + "  sshagent (credentials: ['" + CREDENTIAL_ID + "']) {\n"
                  + "    sh 'ls -l $SSH_AUTH_SOCK && ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST + "'\n"
                  + "  }\n"
                  + "}\n", true)
                );

                assertThat("No fingerprint created until first use", fingerprint, nullValue());

                j.assertBuildStatusSuccess(job.scheduleBuild2(0));

                fingerprint = CredentialsProvider.getFingerprintOf(key);
                assertThat(fingerprint, notNullValue());
                assertThat(fingerprint.getJobs(), hasItem(is(job.getFullName())));

                stopMockSSHServer();
            }
        );
    }

    @Issue("SECURITY-704")
    @Test
    void sshAgentDocker() throws Throwable {
        assumeFalse(Functions.isWindows());
        story.then(j -> {
            // From org.jenkinsci.plugins.docker.workflow.DockerTestUtil:
            Launcher.LocalLauncher localLauncher = new Launcher.LocalLauncher(StreamTaskListener.NULL);
            try {
                assumeTrue(localLauncher.launch().cmds(DockerTool.getExecutable(null, null, null, null), "ps").start().joinWithTimeout(DockerClient.CLIENT_TIMEOUT, TimeUnit.SECONDS, localLauncher.getListener()) == 0,
                        "Docker working");
            } catch (IOException x) {
                assumeTrue(false, "have Docker installed:" + x);
            }

            List<String> credentialIds = new ArrayList<>();
            credentialIds.add(CREDENTIAL_ID);

            SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "x",
                    new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
            SystemCredentialsProvider.getInstance().getCredentials().add(key);
            SystemCredentialsProvider.getInstance().save();

            WorkflowJob job = j.createProject(WorkflowJob.class, "sshAgentDocker");
            job.setDefinition(new CpsFlowDefinition(""
                + "node('" + j.createSlave().getNodeName() + "') {\n"
                + "  withDockerContainer('kroniak/ssh-client:3.22') {\n"
                + "    sh 'ssh-agent -k || :'\n"
                + "    sshagent(credentials: ['" + CREDENTIAL_ID + "']) {\n"
                + "      sh 'env | sort'\n"
                + "    }\n"
                + "  }\n"
                + "}\n", true)
            );
            WorkflowRun b = j.buildAndAssertSuccess(job);
            j.assertLogNotContains("SSH_PASSPHRASE=cloudbees", b);
        });
    }

    @Issue("JENKINS-32104")
    @Test
    void testMissingCredential() throws Throwable {
        story.then(j -> {
                WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "sshAgentAvailable");
                job.setDefinition(new CpsFlowDefinition("""
                        \
                        node {
                          sshagent (credentials: ['nonexistent']) {
                          }
                        }
                        """, true)
                );
                WorkflowRun b = j.buildAndAssertStatus(Result.FAILURE, job);
                j.assertLogContains("Could not find specified credentials", b);
            }
        );
    }

    @Test
    void testIgnoreMissing() throws Throwable {
        story.then(j -> {
                WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "sshAgentAvailable");
                job.setDefinition(new CpsFlowDefinition("""
                        \
                        node {
                          sshagent (credentials: ['nonexistent'], ignoreMissing: true) {
                          }
                        }
                        """, true)
                );
                j.buildAndAssertSuccess(job);
            }
        );
    }

}
