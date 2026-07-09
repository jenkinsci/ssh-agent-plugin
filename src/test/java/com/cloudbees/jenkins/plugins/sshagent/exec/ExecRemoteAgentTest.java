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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import hudson.AbortException;
import org.junit.Test;

public class ExecRemoteAgentTest {

    private static final String VALID_OUTPUT = "SSH_AUTH_SOCK=/tmp/ssh-abcdef/agent.123; export SSH_AUTH_SOCK;\n"
            + "SSH_AGENT_PID=456; export SSH_AGENT_PID;\n"
            + "echo Agent pid 456;\n";

    @Test
    public void parsesValuesFromWellFormedOutput() throws Exception {
        assertEquals("/tmp/ssh-abcdef/agent.123", ExecRemoteAgent.getAgentValue(VALID_OUTPUT, "SSH_AUTH_SOCK"));
        assertEquals("456", ExecRemoteAgent.getAgentValue(VALID_OUTPUT, "SSH_AGENT_PID"));
    }

    @Test
    public void reportsMissingVariableInsteadOfIndexOutOfBounds() {
        AbortException e = assertThrows(
                AbortException.class, () -> ExecRemoteAgent.getAgentValue("no environment here", "SSH_AUTH_SOCK"));
        assertThat(e.getMessage(), containsString("SSH_AUTH_SOCK"));
    }

    @Test
    public void reportsUnterminatedValueInsteadOfIndexOutOfBounds() {
        // Variable is present but is not terminated by ';', which previously threw
        // StringIndexOutOfBoundsException from substring(pos, -1). See issue #280.
        AbortException e = assertThrows(
                AbortException.class, () -> ExecRemoteAgent.getAgentValue("SSH_AUTH_SOCK=/tmp/ssh/agent.1", "SSH_AUTH_SOCK"));
        assertThat(e.getMessage(), containsString("SSH_AUTH_SOCK"));
    }
}
