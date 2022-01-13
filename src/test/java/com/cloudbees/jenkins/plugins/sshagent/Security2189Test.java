package com.cloudbees.jenkins.plugins.sshagent;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

/**
 * Test if there is any information disclosure
 */
public class Security2189Test {

    private static final String SECURE_DATA = "SecureData";
    private static final String TEST_NAME = "test";
    private static final String ADMINISTER_NAME = "administer";
    private static final String WITHOUT_ANY_PERMISSION_USER_NAME = "WithoutAnyPermissionUser";

    @Rule
    public JenkinsRule r = new JenkinsRule();

    private WorkflowJob job;
    private FreeStyleProject project;

    @Issue("SECURITY-2189")
    @Test
    public void doFillCredentialsItemsWhenUserWithoutAnyCredentialsThenListNotPopulated() throws Exception {
        setUpAuthorizationAndWorkflowJob();
        initCredentials(SECURE_DATA, TEST_NAME);

        try(ACLContext aclContext = ACL.as(User.getOrCreateByIdOrFullName(WITHOUT_ANY_PERMISSION_USER_NAME))) {
            SSHAgentStep.DescriptorImpl descriptor = (SSHAgentStep.DescriptorImpl) Jenkins.get().getDescriptorOrDie(SSHAgentStep.class);
            ListBoxModel secureData = descriptor.doFillCredentialsItems(job);

            assertThat(secureData, is(empty()));
        }
    }

    @Issue("SECURITY-2189")
    @Test
    public void doFillIdItemsWhenUserWithoutAnyPermissionThenListNotPopulated() throws Exception {
        setUpAuthorizationAndWorkflowJob();
        initCredentials(SECURE_DATA, TEST_NAME);

        try(ACLContext aclContext = ACL.as(User.getOrCreateByIdOrFullName(WITHOUT_ANY_PERMISSION_USER_NAME))) {
            SSHAgentBuildWrapper.CredentialHolder.DescriptorImpl descriptor = (SSHAgentBuildWrapper.CredentialHolder.DescriptorImpl) Jenkins.get().getDescriptorOrDie(SSHAgentBuildWrapper.CredentialHolder.class);
            ListBoxModel secureData = descriptor.doFillIdItems(project);

            assertThat(secureData, is(empty()));
        }
    }

    private void setUpAuthorizationAndWorkflowJob() throws IOException {
        job = r.jenkins.createProject(WorkflowJob.class, "j");
        setUpAuthorization(job);
    }

    private void setUpAuthorizationAndFreestyleProject() throws IOException {
        project = r.jenkins.createProject(FreeStyleProject.class, "p");
        setUpAuthorization(project);
    }

    private void setUpAuthorization(Item... items) {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to(ADMINISTER_NAME)
                .grant().onItems(items).to(WITHOUT_ANY_PERMISSION_USER_NAME));
    }

    private static void assertListBoxModel(ListBoxModel actual, ListBoxModel expected) {
        assertThat(actual, is(not(empty())));
        assertThat(actual, hasSize(expected.size()));
        assertThat(actual.get(0).name, is(expected.get(0).name));
        assertThat(actual.get(0).value, is(expected.get(0).value));
    }

    private static void initCredentials(String credentialsId, String name) throws IOException {
        SSHUserPrivateKey key = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialsId, "cloudbees",
                null, "*  .*", name);
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();
    }
}
