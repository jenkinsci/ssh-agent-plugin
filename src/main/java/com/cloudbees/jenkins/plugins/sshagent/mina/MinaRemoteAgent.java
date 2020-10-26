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
import hudson.Launcher;
import hudson.model.TaskListener;
import jenkins.bouncycastle.api.PEMEncodable;

import org.apache.commons.io.IOUtils;
import org.apache.sshd.agent.unix.AgentServer;

import java.io.IOException;
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
     * Constructor.
     *
     * @param listener the listener.
     * @throws Exception if the agent could not start.
     */
    public MinaRemoteAgent(TaskListener listener) throws Exception {
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
    public void addIdentity(String privateKey, final String passphrase, String comment, TaskListener listener)
            throws IOException {
        try {
            KeyPair keyPair = PEMEncodable.decode(privateKey, passphrase == null ? null : passphrase.toCharArray()).toKeyPair();
            agent.getAgent().addIdentity(keyPair, comment);
        } catch (Exception e) {
            e.printStackTrace(listener.error(Messages.SSHAgentBuildWrapper_UnableToReadKey(e.getMessage())));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stop(TaskListener listener) {
        IOUtils.closeQuietly(agent);
    }
}
