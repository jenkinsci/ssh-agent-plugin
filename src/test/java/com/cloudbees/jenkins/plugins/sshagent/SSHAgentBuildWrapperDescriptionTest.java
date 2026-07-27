package com.cloudbees.jenkins.plugins.sshagent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import org.junit.jupiter.api.Test;

class SSHAgentBuildWrapperDescriptionTest {

    @Test
    void appendsDescriptionWhenPresent() {
        assertEquals("alice (prod key)", SSHAgentBuildWrapper.description(key("alice", "prod key")));
    }

    @Test
    void usesUsernameOnlyWhenDescriptionBlank() {
        assertEquals("bob", SSHAgentBuildWrapper.description(key("bob", "")));
        assertEquals("bob", SSHAgentBuildWrapper.description(key("bob", null)));
    }

    private static BasicSSHUserPrivateKey key(String username, String description) {
        return new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL,
                "id",
                username,
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource("dummy-key"),
                null,
                description);
    }
}
