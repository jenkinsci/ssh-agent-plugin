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

import com.cloudbees.jenkins.plugins.sshagent.LauncherProvider;
import com.cloudbees.jenkins.plugins.sshagent.RemoteAgent;
import com.cloudbees.jenkins.plugins.sshagent.RemoteAgentFactory;
import com.cloudbees.jenkins.plugins.sshagent.RemoteHelper;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.security.MasterToSlaveCallable;
import io.netty.internal.tcnative.Library;

/**
 * A factory that uses the Apache Mina/SSH library support to (semi-)natively provide a ssh-agent implementation
 */
@Extension
public class MinaRemoteAgentFactory extends RemoteAgentFactory {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return "Java/tomcat-native ssh-agent";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSupported(Launcher launcher, final TaskListener listener) {
        VirtualChannel channel = (launcher == null) ? null : launcher.getChannel(); 
        if (channel == null) {
            throw new IllegalStateException("RemoteLauncher has been initialized with null launcher/channel. It should not happen");
        }
        try {
            return channel.call(new TomcatNativeInstalled(listener));
        } catch (Throwable throwable) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RemoteAgent start(LauncherProvider launcherProvider, final TaskListener listener, FilePath temp)
        throws Throwable {
        Launcher launcher = (launcherProvider == null) ? null : launcherProvider.getLauncher();
        if (launcher == null){
            throw new IllegalStateException("RemoteLauncher has been initialized with null launcher. It should not happen");
        }

        VirtualChannel channel = launcher.getChannel();
        if (channel == null) {
            throw new IllegalStateException("RemoteLauncher has been initialized with null channel. It should not happen");
        }
        RemoteHelper.registerBouncyCastle(channel, listener);

        // TODO temp directory currently ignored
        return channel.call(new MinaRemoteAgentStarter(listener));
    }

    private static class TomcatNativeInstalled extends MasterToSlaveCallable<Boolean, Throwable> {

        /**
         * Ensure consistent serialization. Value generated from the 1.7 release.
         * @since 1.8
         */
        private static final long serialVersionUID = 3234893369850673438L;

        private final TaskListener listener;

        public TomcatNativeInstalled(TaskListener listener) {
            this.listener = listener;
        }

        public Boolean call() throws Throwable {
            try {
                Library.initialize();
                return true;
            } catch (Exception e) {
                listener.getLogger().println("[ssh-agent] Could not find Tomcat Native library");
                return false;
            }
        }
    }
}
