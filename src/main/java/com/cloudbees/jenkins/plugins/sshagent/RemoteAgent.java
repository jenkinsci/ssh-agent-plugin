package com.cloudbees.jenkins.plugins.sshagent;

import java.io.IOException;

/**
 * Need an interface in order to export the object from the channel.
 */
public interface RemoteAgent {
    /**
     * Returns the value that the environment variable should be set to.
     *
     * @return
     */
    String getSocket();

    /**
     * Adds the provided identity to the agent.
     *
     * @param privateKey the private key.
     * @param passphrase the passphrase or {@code null}.
     * @param comment    the comment to give to the key.
     * @throws java.io.IOException if something went wrong.
     */
    void addIdentity(String privateKey, String passphrase, String comment) throws IOException;

    /**
     * Stops the agent.
     */
    void stop();
}
