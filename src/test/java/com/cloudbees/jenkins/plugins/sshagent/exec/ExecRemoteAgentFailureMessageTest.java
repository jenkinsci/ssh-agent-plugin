/*
 * The MIT License
 *
 * Copyright (c) 2014, Eccam s.r.o., Milan Kriz, CloudBees Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import org.junit.Test;

public class ExecRemoteAgentFailureMessageTest {

    @Test
    public void includesStderrWhenAvailable() {
        String message = ExecRemoteAgent.describeFailure("ssh-agent", 1, "", "ssh-agent: command not found\n");
        assertThat(message, containsString("exit code 1"));
        assertThat(message, containsString("ssh-agent: command not found"));
    }

    @Test
    public void fallsBackToStdoutWhenStderrEmpty() {
        String message = ExecRemoteAgent.describeFailure("ssh-agent", 2, "diagnostic on stdout\n", "");
        assertThat(message, containsString("exit code 2"));
        assertThat(message, containsString("diagnostic on stdout"));
    }

    @Test
    public void stillReportsExitCodeWhenNoOutput() {
        // Previously an empty reason produced "Failed to run ssh-agent: " with nothing useful
        // after the colon (issue #278). The exit code must always be reported.
        String message = ExecRemoteAgent.describeFailure("ssh-agent", 127, "", "");
        assertThat(message, containsString("exit code 127"));
    }

    @Test
    public void namesTheFailingCommand() {
        // ssh-add and ssh-agent -k share the same diagnostics, so the message must identify
        // which command failed rather than always saying ssh-agent.
        String message = ExecRemoteAgent.describeFailure("ssh-add", 1, "", "Bad passphrase, try again\n");
        assertThat(message, containsString("Failed to run ssh-add"));
        assertThat(message, containsString("exit code 1"));
        assertThat(message, containsString("Bad passphrase"));
    }
}
