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

package com.cloudbees.jenkins.plugins.sshagent.jna;

import jenkins.security.MasterToSlaveCallable;

import com.cloudbees.jenkins.plugins.sshagent.RemoteAgent;

import hudson.model.TaskListener;
import hudson.remoting.Channel;

/**
 * Callable to start the remote agent.
 */
public class JNRRemoteAgentStarter extends MasterToSlaveCallable<RemoteAgent, Throwable> {

    /**
     * Ensure consistent serialization. Value generated from the 1.7 release.
     * @since 1.8
     */
    private static final long serialVersionUID = 5020446864184061252L;

    /**
     * Need to pass this through.
     */
    private final TaskListener listener;

    /**
     * Constructor.
     *
     * @param listener the listener to pass to the agent.
     */
    public JNRRemoteAgentStarter(TaskListener listener) {
        this.listener = listener;
    }

    /**
     * {@inheritDoc}
     */
    public RemoteAgent call() throws Throwable {
        final JNRRemoteAgent instance = new JNRRemoteAgent(listener);
        final Channel channel = Channel.current();
        return channel == null ? instance : channel.export(RemoteAgent.class, instance);
    }

}
