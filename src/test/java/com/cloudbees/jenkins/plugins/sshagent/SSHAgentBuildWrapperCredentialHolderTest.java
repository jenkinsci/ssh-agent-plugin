package com.cloudbees.jenkins.plugins.sshagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.jenkins.plugins.sshagent.SSHAgentBuildWrapper.CredentialHolder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class SSHAgentBuildWrapperCredentialHolderTest {

    @Issue("https://github.com/jenkinsci/ssh-agent-plugin/issues/231")
    @Test
    void blankOrNullHolderIdsAreDroppedFromTheIdList() {
        assertTrue(CredentialHolder.toIdList(new CredentialHolder[] {new CredentialHolder(null)})
                .isEmpty());
        assertTrue(CredentialHolder.toIdList(new CredentialHolder[] {new CredentialHolder("")})
                .isEmpty());
        assertTrue(CredentialHolder.toIdList(new CredentialHolder[] {new CredentialHolder("   ")})
                .isEmpty());
    }

    @Test
    void nonBlankHolderIdsAreRetainedAndTrimmed() {
        assertEquals(
                List.of("dummy"),
                CredentialHolder.toIdList(new CredentialHolder[] {new CredentialHolder("dummy")}));
        assertEquals(
                List.of("dummy"),
                CredentialHolder.toIdList(new CredentialHolder[] {new CredentialHolder("  dummy  ")}));
    }

    @Issue("https://github.com/jenkinsci/ssh-agent-plugin/issues/231")
    @Test
    void savingTheFormWithoutSelectingACredentialDoesNotAddOne() {
        // The f:repeatableProperty widget has minimum="1", so opening the config form for a
        // wrapper with no credentials and saving it unchanged submits a single CredentialHolder
        // with a null id, exactly as the DataBoundConstructor below simulates.
        SSHAgentBuildWrapper wrapper =
                new SSHAgentBuildWrapper(new CredentialHolder[] {new CredentialHolder(null)}, false, 1, null);
        assertTrue(wrapper.getCredentialIds().isEmpty());
    }

    @Test
    void savingTheFormWithASelectedCredentialKeepsIt() {
        SSHAgentBuildWrapper wrapper =
                new SSHAgentBuildWrapper(new CredentialHolder[] {new CredentialHolder("dummy")}, false, 1, null);
        assertEquals(List.of("dummy"), wrapper.getCredentialIds());
    }
}
