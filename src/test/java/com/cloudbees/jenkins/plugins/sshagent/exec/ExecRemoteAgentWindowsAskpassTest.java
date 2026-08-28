package com.cloudbees.jenkins.plugins.sshagent.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.Functions;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

/**
 * Exercises {@link ExecRemoteAgent#WINDOWS_ASKPASS_SCRIPT} against a real {@code cmd.exe},
 * since the whole point of the script is safely surviving cmd.exe's own re-parsing of the
 * passphrase. Only meaningful on Windows; skipped everywhere else.
 */
class ExecRemoteAgentWindowsAskpassTest {

    @Issue("https://github.com/jenkinsci/ssh-agent-plugin/issues/311")
    @Test
    void printsAPassphraseContainingBatchMetacharactersVerbatim(@TempDir File temp) throws Exception {
        assumeTrue(Functions.isWindows());

        String trickyPassphrase = "p@ss&word|with^special<chars>too";

        File askpass = new File(temp, "askpass_test.bat");
        Files.writeString(askpass.toPath(), ExecRemoteAgent.WINDOWS_ASKPASS_SCRIPT, StandardCharsets.UTF_8);

        // Redirect to a file rather than reading the process's stdout pipe: the script's last
        // line detaches a "start /b" child to self-delete, which can inherit the stdout handle
        // and keep it open after the parent exits, hanging a pipe read waiting for EOF that
        // never comes. A file has no such handle-inheritance hazard.
        File outputFile = new File(temp, "output.txt");
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", askpass.getAbsolutePath());
        pb.environment().put("SSH_PASSPHRASE", trickyPassphrase);
        pb.redirectOutput(outputFile);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        String output = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);

        assertTrue(finished, "askpass script did not exit within the timeout");
        assertEquals(trickyPassphrase, output.strip(), "cmd.exe output: " + output);
    }
}
