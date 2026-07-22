package com.cloudbees.jenkins.plugins.sshagent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloudbees.jenkins.plugins.sshagent.exec.ExecRemoteAgent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

@Issue("JENKINS-74823")
class SSHAgentStepTest {

    @Test
    void defaultsToDefaultTimeout() {
        assertEquals(ExecRemoteAgent.DEFAULT_TIMEOUT_MINUTES, newStep().getTimeoutMinutes());
    }

    @Test
    void retainsConfiguredTimeout() {
        SSHAgentStep step = newStep();
        step.setTimeoutMinutes(5);
        assertEquals(5, step.getTimeoutMinutes());
    }

    @Test
    void clampsNonPositiveTimeoutToOne() {
        SSHAgentStep step = newStep();
        step.setTimeoutMinutes(0);
        assertEquals(1, step.getTimeoutMinutes());
        step.setTimeoutMinutes(-3);
        assertEquals(1, step.getTimeoutMinutes());
    }

    private static SSHAgentStep newStep() {
        return new SSHAgentStep(List.of("dummy-credential-id"));
    }
}
