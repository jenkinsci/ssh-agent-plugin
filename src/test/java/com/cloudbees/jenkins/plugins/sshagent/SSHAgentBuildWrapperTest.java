package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.model.Fingerprint;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.Shell;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;

@WithJenkins
class SSHAgentBuildWrapperTest extends SSHAgentBase {

    private JenkinsRule r;

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void sshAgentAvailable() throws Exception {
        startMockSSHServer();

        List<String> credentialIds = new ArrayList<>();
        credentialIds.add(CREDENTIAL_ID);

        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(KEY_WITHOUT_PASSWORD_AND_NO_NEWLINE), "", "test");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();

        FreeStyleProject job = r.createFreeStyleProject();
        job.setAssignedNode(r.createSlave());

        SSHAgentBuildWrapper sshAgent = new SSHAgentBuildWrapper(credentialIds, false);
        job.getBuildWrappersList().add(sshAgent);

        Shell shell = new Shell("set | grep SSH_AUTH_SOCK "
                + "&& ssh-add -l "
                + "&& ssh -o NoHostAuthenticationForLocalhost=yes -o StrictHostKeyChecking=no -p " + getAssignedPort()
                + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        r.assertBuildStatusSuccess(job.scheduleBuild2(0));

        stopMockSSHServer();
    }

    @Test
    void sshAgentDoesNotDieAfterFirstUse() throws Exception {
        startMockSSHServer();

        List<String> credentialIds = new ArrayList<>();
        credentialIds.add(CREDENTIAL_ID);

        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();

        FreeStyleProject job = r.createFreeStyleProject();

        SSHAgentBuildWrapper sshAgent = new SSHAgentBuildWrapper(credentialIds, false);
        job.getBuildWrappersList().add(sshAgent);

        Shell shell = new Shell(
                "set | grep SSH_AUTH_SOCK "
                        + "&& ssh-add -l "
                        + "&& ssh -o NoHostAuthenticationForLocalhost=yes -o StrictHostKeyChecking=no -p "
                        + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        shell = new Shell("set | grep SSH_AUTH_SOCK "
                + "&& ssh-add -l "
                + "&& ssh -o NoHostAuthenticationForLocalhost=yes -o StrictHostKeyChecking=no -p "
                + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        shell = new Shell("set | grep SSH_AUTH_SOCK "
                + "&& ssh-add -l "
                + "&& ssh -o NoHostAuthenticationForLocalhost=yes -o StrictHostKeyChecking=no -p "
                + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        r.assertBuildStatusSuccess(job.scheduleBuild2(0));

        stopMockSSHServer();
    }

    @Test
    void sshAgentUnavailable() throws Exception {
        startMockSSHServer();

        List<String> credentialIds = new ArrayList<>();
        credentialIds.add(CREDENTIAL_ID);

        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();

        FreeStyleProject job = r.createFreeStyleProject();

        Shell shell = new Shell("ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        r.assertLogContains("Permission denied (keyboard-interactive,publickey).", r.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get()));

        stopMockSSHServer();
    }

    @Test
    void sshAgentWithInvalidCredentials() throws Exception {
        startMockSSHServer();

        List<String> credentialIds = new ArrayList<>();
        credentialIds.add(CREDENTIAL_ID);

        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "BAD-passphrase-cloudbees", "test");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();

        FreeStyleProject job = r.createFreeStyleProject();

        SSHAgentBuildWrapper sshAgent = new SSHAgentBuildWrapper(credentialIds, false);
        job.getBuildWrappersList().add(sshAgent);

        Shell shell = new Shell("ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        r.assertLogContains("Failed to run ssh-add", r.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get()));

        stopMockSSHServer();
    }

    @Issue("JENKINS-38830")
    @Test
    void testTrackingOfCredential() throws Exception {
        startMockSSHServer();

        List<String> credentialIds = new ArrayList<>();
        credentialIds.add(CREDENTIAL_ID);

        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
          new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();

        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(key);
        assertThat("No fingerprint created until first use", fingerprint, nullValue());

        FreeStyleProject job = r.createFreeStyleProject();

        SSHAgentBuildWrapper sshAgent = new SSHAgentBuildWrapper(credentialIds, false);
        job.getBuildWrappersList().add(sshAgent);

        Shell shell = new Shell("set | grep SSH_AUTH_SOCK "
          + "&& ssh-add -l "
          + "&& ssh -o NoHostAuthenticationForLocalhost=yes -o StrictHostKeyChecking=no -p " + getAssignedPort()
          + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        r.assertBuildStatusSuccess(job.scheduleBuild2(0));

        fingerprint = CredentialsProvider.getFingerprintOf(key);
        assertThat(fingerprint, notNullValue());
        assertThat(fingerprint.getJobs(), hasItem(is(job.getFullName())));
        Fingerprint.RangeSet rangeSet = fingerprint.getRangeSet(job);
        assertThat(rangeSet, notNullValue());
        assertThat(rangeSet.includes(job.getLastBuild().getNumber()), is(true));

        stopMockSSHServer();
    }

    @Issue("JENKINS-42093")
    @Test
    void sshAgentWithSpacesInWorkspacePath() throws Exception {
        startMockSSHServer();

        List<String> credentialIds = new ArrayList<>();
        credentialIds.add(CREDENTIAL_ID);

        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();

        FreeStyleProject job = r.createFreeStyleProject("name with spaces");
        job.setAssignedNode(r.createSlave());

        SSHAgentBuildWrapper sshAgent = new SSHAgentBuildWrapper(credentialIds, false);
        job.getBuildWrappersList().add(sshAgent);

        Shell shell = new Shell("set | grep SSH_AUTH_SOCK "
                + "&& ssh-add -l "
                + "&& ssh -o NoHostAuthenticationForLocalhost=yes -o StrictHostKeyChecking=no -p " + getAssignedPort()
                + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        Future<? extends FreeStyleBuild> build = job.scheduleBuild2(0);
        r.assertBuildStatusSuccess(build);
        r.assertLogNotContains("rm: ", build.get());

        stopMockSSHServer();
    }

    @Test
    void sshAgentWithTrickyPassphrase() throws Exception {
        startMockSSHServer();

        List<String> credentialIds = new ArrayList<>();
        credentialIds.add(CREDENTIAL_ID);

        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey2()), "*  .*", "test");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();

        FreeStyleProject job = r.createFreeStyleProject();
        job.setAssignedNode(r.createSlave());

        SSHAgentBuildWrapper sshAgent = new SSHAgentBuildWrapper(credentialIds, false);
        job.getBuildWrappersList().add(sshAgent);

        Shell shell = new Shell("set | grep SSH_AUTH_SOCK "
                + "&& ssh-add -l "
                + "&& ssh -o NoHostAuthenticationForLocalhost=yes -o StrictHostKeyChecking=no -p " + getAssignedPort()
                + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        r.assertBuildStatusSuccess(job.scheduleBuild2(0));

        stopMockSSHServer();
    }

}
