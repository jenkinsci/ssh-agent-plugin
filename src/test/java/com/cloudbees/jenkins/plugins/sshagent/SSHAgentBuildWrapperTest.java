package com.cloudbees.jenkins.plugins.sshagent;

import hudson.model.FreeStyleProject;
import hudson.tasks.Shell;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
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

    @Before
    public void starting () throws Exception {
        startMockSSHServer();
    }

    @Test
    public void sshAgentAvailable() throws Exception {
        List<String> credentialIds = new ArrayList<String>();
        credentialIds.add("84822271-02d5-47b8-b8ff-c40fef175c29");

        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();

        FreeStyleProject job = r.jenkins.createProject(FreeStyleProject.class, "p");

        SSHAgentBuildWrapper sshAgent = new SSHAgentBuildWrapper(credentialIds, false);
        job.getBuildWrappersList().add(sshAgent);

        Shell shell = new Shell("ssh -o StrictHostKeyChecking=no -p " + SSH_SERVER_PORT + " -v -l cloudbees " + SSH_SERVER_HOST);
        job.getBuildersList().add(shell);

        r.assertBuildStatusSuccess(job.scheduleBuild2(0));
    }

    @After
    public void finishing () throws InterruptedException {
        stopMockSSHServer();
    }
}
