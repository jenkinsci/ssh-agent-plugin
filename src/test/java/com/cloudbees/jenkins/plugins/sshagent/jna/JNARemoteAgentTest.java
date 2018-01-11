/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
import hudson.Launcher;
import hudson.model.Label;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.slaves.DumbSlave;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests for {@link JNRRemoteAgent}.
 */
public class JNARemoteAgentTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void shouldInitJNARemoteAgent() throws Throwable {
        JNRRemoteAgentFactory factory = j.jenkins.getExtensionList(RemoteAgentFactory.class).get(JNRRemoteAgentFactory.class);
        TaskListener testListener = j.createTaskListener();

        Label label = new LabelAtom("myTestAgent");
        DumbSlave agent = j.createOnlineSlave(label);
        Launcher launcher = agent.createLauncher(testListener);

        // Actually it means "not Unix" in the current impl
        Assume.assumeTrue("Agent is not compatible with JNRRemoteAgentFactory",factory.isSupported(launcher, testListener));
        RemoteAgent created = factory.start(launcher, testListener, null);
        created.stop();
    }
}
