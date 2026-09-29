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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import java.io.File;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

class ExecRemoteAgentTest {

    private static final String VALID_OUTPUT = "SSH_AUTH_SOCK=/tmp/ssh-abcdef/agent.123; export SSH_AUTH_SOCK;\n"
            + "SSH_AGENT_PID=456; export SSH_AGENT_PID;\n"
            + "echo Agent pid 456;\n";

    @Test
    void parsesValuesFromWellFormedOutput() throws Exception {
        assertEquals("/tmp/ssh-abcdef/agent.123", ExecRemoteAgent.getAgentValue(VALID_OUTPUT, "SSH_AUTH_SOCK"));
        assertEquals("456", ExecRemoteAgent.getAgentValue(VALID_OUTPUT, "SSH_AGENT_PID"));
    }

    @Test
    void reportsMissingVariableInsteadOfIndexOutOfBounds() {
        AbortException e = assertThrows(
                AbortException.class, () -> ExecRemoteAgent.getAgentValue("no environment here", "SSH_AUTH_SOCK"));
        assertThat(e.getMessage(), containsString("SSH_AUTH_SOCK"));
    }

    @Test
    void reportsUnterminatedValueInsteadOfIndexOutOfBounds() {
        // Variable is present but is not terminated by ';', which previously threw
        // StringIndexOutOfBoundsException from substring(pos, -1). See issue #280.
        AbortException e = assertThrows(
                AbortException.class, () -> ExecRemoteAgent.getAgentValue("SSH_AUTH_SOCK=/tmp/ssh/agent.1", "SSH_AUTH_SOCK"));
        assertThat(e.getMessage(), containsString("SSH_AUTH_SOCK"));
    }

    @Test
    void toWindowsPathConvertsADriveLetterMount() {
        assertEquals(
                "C:\\Users\\jenkins\\AppData\\Local\\Temp\\ssh-abc\\agent.123",
                ExecRemoteAgent.toWindowsPath("/c/Users/jenkins/AppData/Local/Temp/ssh-abc/agent.123"));
    }

    @Issue("https://github.com/jenkinsci/ssh-agent-plugin/issues/309")
    @Test
    void toWindowsPathLeavesAnMsysVirtualMountUnchanged() {
        // /tmp is a virtual MSYS mount point (defined in /etc/fstab inside the git installation),
        // not a drive-letter path, so it has no real location that string substitution can find.
        // Converting it used to produce a driveless, relative path that ssh-add could not resolve.
        assertEquals(
                "/tmp/ssh-kiwKu7uzgZkX/agent.792", ExecRemoteAgent.toWindowsPath("/tmp/ssh-kiwKu7uzgZkX/agent.792"));
    }

    // -----------------------------------------------------------------------
    // extractGitSSHAgentExe – cygwin / Git-for-Windows path resolution
    // -----------------------------------------------------------------------

    /**
     * Simulates a Git-for-Windows installation where ssh-agent.exe lives at
     * {@code <git-home>/usr/bin/ssh-agent.exe}. The method must find it via
     * the {@code usr/bin} candidate path (existing behaviour, unaffected by
     * the Cygwin fix).
     */
    @Issue("https://github.com/jenkinsci/ssh-agent-plugin/pull/319")
    @Test
    void findsSSHAgentUnderUsrBinForGitForWindows(@TempDir File tempDir) throws Exception {
        // Build: <tempDir>/usr/bin/ssh-agent.exe
        File usrBin = new File(tempDir, "usr" + File.separator + "bin");
        assertTrue(usrBin.mkdirs());
        assertTrue(new File(usrBin, "ssh-agent.exe").createNewFile());

        Launcher launcher = new Launcher.LocalLauncher(hudson.model.TaskListener.NULL);
        Optional<FilePath> result = ExecRemoteAgent.extractGitSSHAgentExe(
                List.of(tempDir.getAbsolutePath()), launcher);

        assertTrue(result.isPresent(), "ssh-agent should be found under usr/bin (Git-for-Windows layout)");
        assertTrue(result.get().getRemote().endsWith("ssh-agent.exe"));
    }

    /**
     * Simulates a Cygwin installation where ssh-agent.exe lives directly at
     * {@code <cygwin-root>/bin/ssh-agent.exe} (no {@code usr\} prefix).
     * Without the fix this returned empty; with the fix it must return the
     * correct path.
     */
    @Issue("https://github.com/jenkinsci/ssh-agent-plugin/pull/319")
    @Test
    void findsSSHAgentUnderBinForCygwin(@TempDir File tempDir) throws Exception {
        // Build: <tempDir>/bin/ssh-agent.exe  (no usr/ prefix – Cygwin layout)
        File bin = new File(tempDir, "bin");
        assertTrue(bin.mkdirs());
        assertTrue(new File(bin, "ssh-agent.exe").createNewFile());

        Launcher launcher = new Launcher.LocalLauncher(hudson.model.TaskListener.NULL);
        Optional<FilePath> result = ExecRemoteAgent.extractGitSSHAgentExe(
                List.of(tempDir.getAbsolutePath()), launcher);

        assertTrue(result.isPresent(), "ssh-agent should be found under bin/ (Cygwin layout)");
        assertTrue(result.get().getRemote().endsWith("ssh-agent.exe"));
    }

    /**
     * When neither candidate path contains ssh-agent.exe the method must
     * return empty (no match).
     */
    @Issue("https://github.com/jenkinsci/ssh-agent-plugin/pull/319")
    @Test
    void returnsEmptyWhenSSHAgentNotFound(@TempDir File tempDir) throws Exception {
        Launcher launcher = new Launcher.LocalLauncher(hudson.model.TaskListener.NULL);
        Optional<FilePath> result = ExecRemoteAgent.extractGitSSHAgentExe(
                List.of(tempDir.getAbsolutePath()), launcher);

        assertFalse(result.isPresent(), "should return empty when no ssh-agent.exe exists");
    }

    /**
     * When the path ends in {@code /bin/git.exe} the method strips two segments
     * to reach the git home before probing. Verifies the Cygwin fallback still
     * works after path normalisation when given {@code <cygwin-root>/bin/git.exe}
     * as input (as returned by {@code where git} on a Cygwin node).
     */
    @Issue("https://github.com/jenkinsci/ssh-agent-plugin/pull/319")
    @Test
    @EnabledOnOs(OS.WINDOWS) // GIT_EXE_PATH regex matches backslash separators only; path stripping is Windows-only
    void findsSSHAgentWhenGivenGitExePathInsteadOfGitHome(@TempDir File tempDir) throws Exception {
        // Cygwin layout: <tempDir>/bin/git.exe and <tempDir>/bin/ssh-agent.exe
        File bin = new File(tempDir, "bin");
        assertTrue(bin.mkdirs());
        assertTrue(new File(bin, "git.exe").createNewFile());
        assertTrue(new File(bin, "ssh-agent.exe").createNewFile());

        String gitExePath = new File(bin, "git.exe").getAbsolutePath();
        Launcher launcher = new Launcher.LocalLauncher(hudson.model.TaskListener.NULL);
        Optional<FilePath> result = ExecRemoteAgent.extractGitSSHAgentExe(
                List.of(gitExePath), launcher);

        assertTrue(result.isPresent(), "ssh-agent should be found when given a git.exe path (Cygwin layout)");
        assertTrue(result.get().getRemote().endsWith("ssh-agent.exe"));
    }
}
