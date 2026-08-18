package com.cloudbees.jenkins.plugins.sshagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.util.XStream2;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class SSHAgentBuildWrapperExecutableTest {

    @Issue("https://github.com/jenkinsci/ssh-agent-plugin/issues/303")
    @Test
    void blankExecutableFromConstructorIsNormalizedToNull() {
        assertNull(new SSHAgentBuildWrapper(List.of("dummy"), false, 1, "").getExecutable());
        assertNull(new SSHAgentBuildWrapper(List.of("dummy"), false, 1, "   ").getExecutable());
        assertNull(new SSHAgentBuildWrapper(List.of("dummy"), false, 1, null).getExecutable());
    }

    @Test
    void nonBlankExecutableFromConstructorIsTrimmedAndRetained() {
        assertEquals(
                "/usr/bin/ssh-agent",
                new SSHAgentBuildWrapper(List.of("dummy"), false, 1, "/usr/bin/ssh-agent").getExecutable());
        assertEquals(
                "/usr/bin/ssh-agent",
                new SSHAgentBuildWrapper(List.of("dummy"), false, 1, "  /usr/bin/ssh-agent  ").getExecutable());
    }

    @Issue("https://github.com/jenkinsci/ssh-agent-plugin/issues/303")
    @Test
    void legacyConfigurationWithBlankExecutableDeserializesToNull() {
        // The "Executable" field's f:textbox submits an empty string rather than omitting the
        // element when left blank, so job configs saved through the UI before this fix can have
        // an <executable></executable> element on disk. ExecRemoteAgent rejects any non-null
        // value that is not a valid ssh-agent(.exe) path, so this used to break every build.
        String legacyXml = "<com.cloudbees.jenkins.plugins.sshagent.SSHAgentBuildWrapper>\n"
                + "  <credentialIds>\n"
                + "    <string>dummy</string>\n"
                + "  </credentialIds>\n"
                + "  <ignoreMissing>false</ignoreMissing>\n"
                + "  <timeout>1</timeout>\n"
                + "  <executable></executable>\n"
                + "</com.cloudbees.jenkins.plugins.sshagent.SSHAgentBuildWrapper>";
        SSHAgentBuildWrapper wrapper = (SSHAgentBuildWrapper) new XStream2().fromXML(legacyXml);
        assertNull(wrapper.getExecutable());
    }

    @Test
    void configurationWithValidExecutableDeserializesUnchanged() {
        // A config already carrying a valid, non-blank executable must survive readResolve()
        // unchanged, confirming the blank-normalization check does not also touch valid values.
        String xml = "<com.cloudbees.jenkins.plugins.sshagent.SSHAgentBuildWrapper>\n"
                + "  <credentialIds>\n"
                + "    <string>dummy</string>\n"
                + "  </credentialIds>\n"
                + "  <ignoreMissing>false</ignoreMissing>\n"
                + "  <timeout>1</timeout>\n"
                + "  <executable>/usr/bin/ssh-agent</executable>\n"
                + "</com.cloudbees.jenkins.plugins.sshagent.SSHAgentBuildWrapper>";
        SSHAgentBuildWrapper wrapper = (SSHAgentBuildWrapper) new XStream2().fromXML(xml);
        assertEquals("/usr/bin/ssh-agent", wrapper.getExecutable());
    }

    @Issue("https://github.com/jenkinsci/ssh-agent-plugin/issues/303")
    @Test
    void legacyConfigurationWithoutExecutableDeserializesToNull() {
        // Configurations persisted before the executable property existed have no <executable>
        // element at all, which XStream leaves as the Java default (null).
        String legacyXml = "<com.cloudbees.jenkins.plugins.sshagent.SSHAgentBuildWrapper>\n"
                + "  <credentialIds>\n"
                + "    <string>dummy</string>\n"
                + "  </credentialIds>\n"
                + "  <ignoreMissing>false</ignoreMissing>\n"
                + "</com.cloudbees.jenkins.plugins.sshagent.SSHAgentBuildWrapper>";
        SSHAgentBuildWrapper wrapper = (SSHAgentBuildWrapper) new XStream2().fromXML(legacyXml);
        assertNull(wrapper.getExecutable());
    }
}
