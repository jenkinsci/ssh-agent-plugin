package com.cloudbees.jenkins.plugins.sshagent;

import hudson.Extension;
import hudson.model.BuildListener;
import org.apache.sshd.agent.unix.AgentServer;
import org.apache.sshd.common.util.SecurityUtils;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;

import java.io.IOException;
import java.io.StringReader;
import java.security.KeyPair;

/**
 * An implementation that uses Apache SSH to provide the Agent. This requires Tomcat-Native.
 */
@Extension(ordinal = 0.0)
public class RemoteAgentImpl implements RemoteAgent {
    /**
     * Our agent.
     */
    private final AgentServer agent;
    /**
     * The socket bound by the agent.
     */
    private final String socket;
    /**
     * The listener in case we need to report exceptions
     */
    private final BuildListener listener;

    /**
     * Constructor.
     *
     * @param listener the listener.
     * @throws Exception if the agent could not start.
     */
    public RemoteAgentImpl(BuildListener listener) throws Exception {
        this.listener = listener;
        agent = new AgentServer();
        socket = agent.start();
    }

    /**
     * {@inheritDoc}
     */
    public String getSocket() {
        return socket;
    }

    /**
     * {@inheritDoc}
     */
    public void addIdentity(String privateKey, final String passphrase, String comment) throws IOException {
        if (!SecurityUtils.isBouncyCastleRegistered()) {
            throw new IllegalStateException("BouncyCastle must be registered as a JCE provider");
        }
        try {
            PEMReader r = new PEMReader(new StringReader(privateKey),
                    passphrase == null ? null : new PasswordFinder() {
                        public char[] getPassword() {
                            return passphrase.toCharArray();
                        }
                    });
            try {
                Object o = r.readObject();
                if (o instanceof KeyPair) {
                    agent.getAgent().addIdentity((KeyPair) o, comment);
                }
            } finally {
                r.close();
            }
        } catch (Exception e) {
            listener.getLogger().println(Messages.SSHAgentBuildWrapper_UnableToReadKey(e.getMessage()));
            e.printStackTrace(listener.getLogger());
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stop() {
        agent.close();
    }
}
