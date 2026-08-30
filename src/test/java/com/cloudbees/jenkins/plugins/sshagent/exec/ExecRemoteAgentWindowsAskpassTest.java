package com.cloudbees.jenkins.plugins.sshagent.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

import hudson.FilePath;
import hudson.Functions;


/**
 * Exercises {@link ExecRemoteAgent#createAskpassScript} against a real {@code cmd.exe},
 * since the whole point of the script is safely surviving cmd.exe's own re-parsing of the
 * passphrase. Only meaningful on Windows; skipped everywhere else.
 */
class ExecRemoteAgentWindowsAskpassTest {

    @Issue("https://github.com/jenkinsci/ssh-agent-plugin/issues/311")
    @Test
    void printsPassphraseContainingWindowsBatchMetacharactersVerbatim() throws Exception {
        assumeTrue(Functions.isWindows());

        // & | < > are cmd.exe command-separator/redirect operators; ! is the delayed-expansion
        // trigger character. All five previously had a way to corrupt the printed passphrase.
        String trickyPassphrase = "p@ss&word|with^special<chars>!and!bangs!too";

        testVerbatimPassphrasePrinting(trickyPassphrase);
    }

    @Test
    void printsPassphraseContainingWindowsEchoHelpCommand() throws Exception {
        // Under Windows the help for the echo command is printed by: echo /?
        testVerbatimPassphrasePrinting("/?");
    }

    @Test
    void printsEmptyPassphrase() throws Exception {
        testVerbatimPassphrasePrinting("");
    }

    @TempDir
    Path temp;

    private void testVerbatimPassphrasePrinting(String passphrase) throws Exception {
        boolean isWindows = Functions.isWindows();
        FilePath tempFile = new FilePath(temp.toFile());
        FilePath askpass = ExecRemoteAgent.createAskpassScript(tempFile, isWindows);

        // Redirect to a file rather than reading the process's stdout pipe: the script's last
        // line detaches a "start /b" child to self-delete, which can inherit the stdout handle
        // and keep it open after the parent exits, hanging a pipe read waiting for EOF that
        // never comes. A file has no such handle-inheritance hazard.
        Path outputFile = Files.createTempFile("output", "txt");
        ProcessBuilder pb = new ProcessBuilder(askpass.getRemote());
        pb.environment().put("SSH_PASSPHRASE", passphrase);
        pb.redirectOutput(outputFile.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        String output = Files.readString(outputFile);

        assertTrue(finished, "askpass script did not exit within the timeout");
        assertEquals(passphrase + System.lineSeparator(), output, "askpass output: " + output);

        // Await sub-process termination.
        // On Windows script deletion is done in a sub-process.
        for (var childTermination : p.descendants().map(ProcessHandle::onExit).toList()) {
            childTermination.get();
        }
        assertFalse(askpass.exists(), "askpass script did not delete itself");
    }
}
