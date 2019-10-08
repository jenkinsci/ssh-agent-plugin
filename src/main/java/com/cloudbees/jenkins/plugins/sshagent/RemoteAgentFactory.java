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

package com.cloudbees.jenkins.plugins.sshagent;

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.TaskListener;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Extension point for ssh-agent providers.
 */
public abstract class RemoteAgentFactory implements ExtensionPoint {
    /**
     * The display name of the factory.
     *
     * @return The display name of the factory.
     */
    public abstract String getDisplayName();

    /**
     * Checks if the supplied launcher is supported by this factory.
     *
     * @param launcher the launcher on which the factory would be asked to start a ssh-agent.
     * @param listener a listener in case any user diagnostics are to be printed.
     * @return {@code false} if the factory does not want to try and start an ssh-agent on the launcher.
     */
    public abstract boolean isSupported(Launcher launcher, TaskListener listener);

    @Deprecated
    public RemoteAgent start(@Nonnull Launcher launcher, TaskListener listener) throws Throwable {
        return start(launcher, listener, null);
    }

    /**
     * Start a ssh-agent on the specified launcher.
     *
     * @param launcher the launcher on which to start a ssh-agent.
     * @param listener a listener for any diagnostics.
     * @param temp a temporary directory to use; null if unspecified
     * @return the agent.
     * @throws Throwable if the agent cannot be started.
     */
    public /*abstract*/ RemoteAgent start(@Nonnull Launcher launcher, TaskListener listener, @CheckForNull FilePath temp) throws Throwable {
        if (Util.isOverridden(RemoteAgentFactory.class, getClass(), "start", Launcher.class, TaskListener.class)) {
            return start(launcher, listener);
        } else {
            throw new AbstractMethodError("you must implement the start method");
        }
    }
}
