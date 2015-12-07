package com.cloudbees.jenkins.plugins.sshagent;

import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.Shell;

import java.util.ArrayList;
import java.util.List;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;

public class SSHAgentBuildWrapperTest extends SSHAgentBase {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Test
    public void sshAgentAvailable() throws Exception {
        startMockSSHServer();

        List<String> credentialIds = new ArrayList<String>();
        credentialIds.add(CREDENTIAL_ID);

        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();

        FreeStyleProject job = r.createFreeStyleProject();

        SSHAgentBuildWrapper sshAgent = new SSHAgentBuildWrapper(credentialIds, false);
        job.getBuildWrappersList().add(sshAgent);

        Shell shell = new Shell("ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        r.assertBuildStatusSuccess(job.scheduleBuild2(0));

        stopMockSSHServer();
    }

    @Test
    public void sshAgentDoesNotDieAfterFirstUse() throws Exception {
        startMockSSHServer();

        List<String> credentialIds = new ArrayList<String>();
        credentialIds.add(CREDENTIAL_ID);

        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();

        FreeStyleProject job = r.createFreeStyleProject();

        SSHAgentBuildWrapper sshAgent = new SSHAgentBuildWrapper(credentialIds, false);
        job.getBuildWrappersList().add(sshAgent);

        Shell shell = new Shell("ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        shell = new Shell("ssh-add -l");
        job.getBuildersList().add(shell);

        shell = new Shell("ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        r.assertBuildStatusSuccess(job.scheduleBuild2(0));

        stopMockSSHServer();
    }

    @Test
    public void sshAgentUnavailable() throws Exception {
        startMockSSHServer();

        List<String> credentialIds = new ArrayList<String>();
        credentialIds.add(CREDENTIAL_ID);

        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();

        FreeStyleProject job = r.createFreeStyleProject();

        Shell shell = new Shell("ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        r.assertLogContains("Permission denied (publickey).", r.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get()));

        stopMockSSHServer();
    }

    @Test
    public void sshAgentWithInvalidCredentials() throws Exception {
        startMockSSHServer();

        List<String> credentialIds = new ArrayList<String>();
        credentialIds.add(CREDENTIAL_ID);

        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "BAD-passphrase-cloudbees", "test");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();

        FreeStyleProject job = r.createFreeStyleProject();

        Shell shell = new Shell("ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        r.assertLogContains("Permission denied (publickey).", r.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get()));

        stopMockSSHServer();
    }
}
