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

import com.cloudbees.jenkins.plugins.sshagent.RemoteAgent;
import com.cloudbees.jenkins.plugins.sshagent.RemoteAgentFactory;
import com.cloudbees.jenkins.plugins.sshagent.RemoteHelper;

import com.cloudbees.jenkins.plugins.sshagent.LauncherProvider;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;

import jenkins.security.MasterToSlaveCallable;
import org.apache.tomcat.jni.Library;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "We always require nonnull channel when we initialize this launcher")
    public boolean isSupported(Launcher launcher, final TaskListener listener) {
        if (launcher == null || launcher.getChannel() == null) {
            throw new IllegalStateException("RemoteLauncher has been initialized with Null channel. It should not happen");
        }
        try {
            return launcher.getChannel().call(new TomcatNativeInstalled(listener));
        } catch (Throwable throwable) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "We always require nonnull channel when we initialize this launcher")
    public RemoteAgent start(LauncherProvider launcherProvider, final TaskListener listener, FilePath temp)
        throws Throwable {

        if (launcherProvider == null || launcherProvider.getLauncher() == null){
            throw new IllegalStateException("RemoteLauncher has been initialized with null launcher provider. It should not happen");
        }

        RemoteHelper.registerBouncyCastle(launcherProvider.getLauncher().getChannel(), listener);

        // TODO temp directory currently ignored
        return launcherProvider.getLauncher().getChannel().call(new MinaRemoteAgentStarter(listener));
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
                Library.initialize(null);
                return true;
            } catch (Exception e) {
                listener.getLogger().println("[ssh-agent] Could not find Tomcat Native library");
                return false;
            }
        }
    }
}
