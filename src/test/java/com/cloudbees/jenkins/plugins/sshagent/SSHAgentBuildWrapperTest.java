package com.cloudbees.jenkins.plugins.sshagent;

import hudson.model.FreeStyleProject;
import hudson.tasks.Shell;

import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;

public class SSHAgentBuildWrapperTest extends SSHAgentBase {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void sshAgentAvailable() throws Exception {
        startMockSSHServer();

        List<String> credentialIds = new ArrayList<String>();
        credentialIds.add(CREDENTIAL_ID);

        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();

        FreeStyleProject job = r.jenkins.createProject(FreeStyleProject.class, "p");

        SSHAgentBuildWrapper sshAgent = new SSHAgentBuildWrapper(credentialIds, false);
        job.getBuildWrappersList().add(sshAgent);

        Shell shell = new Shell("ssh -o StrictHostKeyChecking=no -p " + getAssignedPort() + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        r.assertBuildStatusSuccess(job.scheduleBuild2(0));

        stopMockSSHServer();
    }

}
