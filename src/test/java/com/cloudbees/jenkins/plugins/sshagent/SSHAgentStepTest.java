package com.cloudbees.jenkins.plugins.sshagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.jenkins.plugins.sshagent.exec.ExecRemoteAgent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class SSHAgentStepTest {

    @Issue("JENKINS-74823")
    @Test
    void defaultsToDefaultTimeout() {
        assertEquals(ExecRemoteAgent.DEFAULT_TIMEOUT_MINUTES, newStep().getTimeoutMinutes());
    }

    @Issue("JENKINS-74823")
    @Test
    void retainsConfiguredTimeout() {
        SSHAgentStep step = newStep();
        step.setTimeoutMinutes(5);
        assertEquals(5, step.getTimeoutMinutes());
    }

    @Issue("JENKINS-74823")
    @Test
    void clampsNonPositiveTimeoutToOne() {
        SSHAgentStep step = newStep();
        step.setTimeoutMinutes(0);
        assertEquals(1, step.getTimeoutMinutes());
        step.setTimeoutMinutes(-3);
        assertEquals(1, step.getTimeoutMinutes());
    }

    @Test
    void trimsUsernameVariable() {
        SSHAgentStep step = newStep();
        step.setUsernameVariable("  SSH_USER  ");
        assertEquals("SSH_USER", step.getUsernameVariable());
    }

    @Test
    void blankUsernameVariableBecomesNull() {
        SSHAgentStep step = newStep();
        step.setUsernameVariable("");
        assertNull(step.getUsernameVariable());
        step.setUsernameVariable("   ");
        assertNull(step.getUsernameVariable());
    }

    @Test
    void usernameVariableIsUnsetByDefault() {
        assertNull(newStep().getUsernameVariable());
    }

    @Test
    void ignoreMissingDefaultsToFalseAndToggles() {
        SSHAgentStep step = newStep();
        assertFalse(step.isIgnoreMissing());
        step.setIgnoreMissing(true);
        assertTrue(step.isIgnoreMissing());
    }

    @Test
    void hostKeyVerificationDefaultsToFalseAndToggles() {
        SSHAgentStep step = newStep();
        assertFalse(step.isHostKeyVerification());
        step.setHostKeyVerification(true);
        assertTrue(step.isHostKeyVerification());
    }

    private static SSHAgentStep newStep() {
        return new SSHAgentStep(List.of("dummy-credential-id"));
    }
}
