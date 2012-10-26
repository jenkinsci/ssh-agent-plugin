/*
 * The MIT License
 *
 * Copyright (c) 2012, CloudBees, Inc., Stephen Connolly.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.plugins.sshagent.mina;

import com.cloudbees.jenkins.plugins.sshagent.Messages;
import com.cloudbees.jenkins.plugins.sshagent.RemoteAgent;
import hudson.model.TaskListener;
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
public class MinaRemoteAgent implements RemoteAgent {
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
    private final TaskListener listener;

    /**
     * Constructor.
     *
     * @param listener the listener.
     * @throws Exception if the agent could not start.
     */
    public MinaRemoteAgent(TaskListener listener) throws Exception {
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
