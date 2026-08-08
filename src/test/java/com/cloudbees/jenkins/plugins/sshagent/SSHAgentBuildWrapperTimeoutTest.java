package com.cloudbees.jenkins.plugins.sshagent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloudbees.jenkins.plugins.sshagent.exec.ExecRemoteAgent;
import hudson.util.XStream2;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class SSHAgentBuildWrapperTimeoutTest {

    @Issue("https://github.com/jenkinsci/ssh-agent-plugin/issues/299")
    @Test
    void nonPositiveTimeoutFromConstructorFallsBackToDefault() {
        assertEquals(
                ExecRemoteAgent.DEFAULT_TIMEOUT_MINUTES,
                new SSHAgentBuildWrapper(List.of("dummy"), false, 0, null).getTimeout());
        assertEquals(
                ExecRemoteAgent.DEFAULT_TIMEOUT_MINUTES,
                new SSHAgentBuildWrapper(List.of("dummy"), false, -5, null).getTimeout());
    }

    @Test
    void positiveTimeoutFromConstructorIsRetained() {
        assertEquals(7, new SSHAgentBuildWrapper(List.of("dummy"), false, 7, null).getTimeout());
    }

    @Issue("https://github.com/jenkinsci/ssh-agent-plugin/issues/299")
    @Test
    void legacyConfigurationWithoutTimeoutDeserializesToDefault() {
        // Job configurations persisted before the timeout became configurable have no <timeout>
        // element. That deserializes to zero, which previously made ssh-add time out immediately.
        String legacyXml = "<com.cloudbees.jenkins.plugins.sshagent.SSHAgentBuildWrapper>\n"
                + "  <credentialIds>\n"
                + "    <string>dummy</string>\n"
                + "  </credentialIds>\n"
                + "  <ignoreMissing>false</ignoreMissing>\n"
                + "</com.cloudbees.jenkins.plugins.sshagent.SSHAgentBuildWrapper>";
        SSHAgentBuildWrapper wrapper = (SSHAgentBuildWrapper) new XStream2().fromXML(legacyXml);
        assertEquals(ExecRemoteAgent.DEFAULT_TIMEOUT_MINUTES, wrapper.getTimeout());
    }
}
