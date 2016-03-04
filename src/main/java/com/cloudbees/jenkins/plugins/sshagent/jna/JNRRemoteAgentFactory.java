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

import com.cloudbees.jenkins.plugins.sshagent.RemoteAgent;
import com.cloudbees.jenkins.plugins.sshagent.RemoteAgentFactory;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;

/**
 * A factory that uses the Apache Mina/SSH library support to natively provide a ssh-agent implementation on platforms
 * supported by jnr-unixsocket
 */
@Extension
public class JNRRemoteAgentFactory extends RemoteAgentFactory {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return "Java/JNR ssh-agent";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSupported(Launcher launcher, final TaskListener listener) {
        return launcher.isUnix();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RemoteAgent start(Launcher launcher, final TaskListener listener, FilePath temp) throws Throwable {
        return launcher.getChannel().call(new JNRRemoteAgentStarter(listener, temp != null ? temp.getRemote() : null));
    }

}