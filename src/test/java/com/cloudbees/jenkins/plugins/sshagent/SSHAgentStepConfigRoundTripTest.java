package com.cloudbees.jenkins.plugins.sshagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import java.util.List;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SSHAgentStepConfigRoundTripTest {

    @Test
    void optionsSurviveConfigRoundTrip(JenkinsRule r) throws Exception {
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new BasicSSHUserPrivateKey(
                        CredentialsScope.GLOBAL,
                        "my-cred",
                        "user",
                        new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource("dummy"),
                        null,
                        "desc"));

        SSHAgentStep step = new SSHAgentStep(List.of("my-cred"));
        step.setIgnoreMissing(true);
        step.setUsernameVariable("SSH_USER");
        step.setTimeoutMinutes(5);

        SSHAgentStep round = new StepConfigTester(r).configRoundTrip(step);

        assertTrue(round.isIgnoreMissing());
        assertEquals("SSH_USER", round.getUsernameVariable());
        assertEquals(5, round.getTimeoutMinutes());
    }
}
