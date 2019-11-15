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

import hudson.Launcher;
import hudson.model.TaskListener;

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
     * @param launcher   the launcher for the remote node.
     * @param listener   for logging.
     * @throws java.io.IOException if something went wrong.
     */
    void addIdentity(String privateKey, String passphrase, String comment, Launcher launcher,
                     TaskListener listener) throws IOException, InterruptedException;

    /**
     * Stops the agent.
     *
     * @param launcher the launcher for the remote node.
     * @param listener for logging.
     */
    void stop(Launcher launcher, TaskListener listener) throws IOException, InterruptedException;
}
