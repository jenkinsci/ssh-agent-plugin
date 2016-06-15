/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

import java.io.PrintStream;

import javax.annotation.Nonnull;

import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import jenkins.bouncycastle.api.InstallBouncyCastleJCAProvider;

/**
 * Helper class for common remote tasks
 */
public class RemoteHelper {

    /**
     * Registers Bouncy Castle on a remote node logging the result.
     * 
     * @param channel to communicate with the agent
     * @param logger to log the messages
     */
    public static void registerBouncyCastle(@Nonnull VirtualChannel channel, @Nonnull final PrintStream logger) {
        if (channel instanceof Channel) {
            try {
                InstallBouncyCastleJCAProvider.on((Channel) channel);
                logger.println("[ssh-agent] Registered BouncyCastle on the remote agent");
            } catch (Exception e) {
                logger.println("[ssh-agent] WARNING: could not register BouncyCastle on the remote agent." + e.getMessage());
                e.printStackTrace(logger);
            }
        } else {
            logger.println("[ssh-agent] Skipped registering BouncyCastle, not running on the remote agent");
        }
    }
}