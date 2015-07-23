package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

public class SSHAgentBuildWrapperWorkflowTest extends SSHAgentBase {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before
    public void starting () throws Exception {
        startMockSSHServer();
        List<String> credentialIds = new ArrayList<String>();
        credentialIds.add("84822271-02d5-47b8-b8ff-c40fef175c29");

        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialIds.get(0), "cloudbees",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(getPrivateKey()), "cloudbees", "test");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();
    }

    @Test
    public void sshAgentAvailable() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "sshAgentAvailable");
        job.setDefinition(new CpsFlowDefinition(""
                + "node {\n"
                + "  wrap([$class: 'SSHAgentBuildWrapper', credentialHolders: [[id: '84822271-02d5-47b8-b8ff-c40fef175c29']], ignoreMissing: false]) {\n"
                + "    sh 'ssh -o StrictHostKeyChecking=no -p " + SSH_SERVER_PORT + " -v -l cloudbees " + SSH_SERVER_HOST + "'\n"
                + "  }\n"
                + "}\n", true));
        r.assertBuildStatusSuccess(job.scheduleBuild2(0));
    }

    @After
    public void finishing () throws InterruptedException {
        stopMockSSHServer();
    }

}
