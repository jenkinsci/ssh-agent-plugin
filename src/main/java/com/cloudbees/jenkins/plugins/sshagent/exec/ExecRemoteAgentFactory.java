/*
 * The MIT License
 *
 * Copyright (c) 2014, Eccam s.r.o., Milan Kriz
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

package com.cloudbees.jenkins.plugins.sshagent.exec;

import com.cloudbees.jenkins.plugins.sshagent.RemoteAgent;
import com.cloudbees.jenkins.plugins.sshagent.RemoteAgentFactory;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.concurrent.TimeUnit;



/**
 * A factory that uses the native SSH agent installed on a remote system. SSH agent has to be in PATH environment variable.
 */
@Extension
public class ExecRemoteAgentFactory extends RemoteAgentFactory {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return "Exec ssh-agent (binary ssh-agent on a remote machine)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSupported(Launcher launcher, final TaskListener listener) {
        try {             
            int status = launcher.launch().cmds("ssh-agent", "-k").envs("SSH_AGENT_PID=").quiet(true).start().joinWithTimeout(1, TimeUnit.MINUTES, listener);
            /* 
             * `ssh-agent -k` returns 0 if terminates running agent or 1 if
             * it fails to terminate it. On Linux, 
             */
            return (status == 0) || (status == 1);
        } catch (IOException e) {
            e.printStackTrace();
            listener.getLogger().println("Could not find ssh-agent: IOException: " + e.getMessage());
            listener.getLogger().println("Check if ssh-agent is installed and in PATH");
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            listener.getLogger().println("Could not find ssh-agent: InterruptedException: " + e.getMessage());          
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RemoteAgent start(Launcher launcher, final TaskListener listener, FilePath temp) throws Throwable {
        return new ExecRemoteAgent(launcher, listener, temp);
    }
}
