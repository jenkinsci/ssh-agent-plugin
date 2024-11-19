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

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs a native SSH agent installed on a system.
 */
public final class ExecRemoteAgent implements Serializable {
    private static final String AuthSocketVar = "SSH_AUTH_SOCK";
    private static final String AgentPidVar = "SSH_AGENT_PID";

    /** Agent environment used for {@code ssh-add} and {@code ssh-agent -k}. */
    private final Map<String, String> agentEnv;

    public ExecRemoteAgent(Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (launcher.launch().cmds("ssh-agent").stdout(baos).start()
                .joinWithTimeout(1, TimeUnit.MINUTES, listener) != 0) {
            String reason = new String(baos.toByteArray(), StandardCharsets.US_ASCII);
            throw new AbortException("Failed to run ssh-agent: " + reason);
        }
        agentEnv = parseAgentEnv(new String(baos.toByteArray(), StandardCharsets.US_ASCII), listener); // TODO could include local filenames, better to look up remote charset
    }

    /**
     * Adds the provided identity to the agent.
     *
     * @param privateKey the private key.
     * @param passphrase the passphrase or {@code null}.
     * @param comment    the comment to give to the key.
     * @param listener   for logging.
     */
    public void addIdentity(String privateKey, final String passphrase, String comment, FilePath ws, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        FilePath temp = WorkspaceList.tempDir(ws);
        if (temp == null) {
            throw new IOException("No temp dir in " + ws);
        }
        FilePath keyFile = temp.createTextTempFile("private_key_", ".key", privateKey);
        try {
            keyFile.chmod(0600);

            FilePath askpass = passphrase != null ? createAskpassScript(temp) : null;
            try {

                Map<String,String> env = new HashMap<>(agentEnv);
                if (passphrase != null) {
                    env.put("SSH_PASSPHRASE", passphrase);
                    env.put("DISPLAY", "bogus"); // just to force using SSH_ASKPASS
                    env.put("SSH_ASKPASS", askpass.getRemote());
                }
                
                // as the next command is in quiet mode, we just add a message to the log
                listener.getLogger().println("Running ssh-add (command line suppressed)");
                
                if (launcher.launch().quiet(true).cmds("ssh-add", keyFile.getRemote()).envs(env)
                        .stdout(listener).start().joinWithTimeout(1, TimeUnit.MINUTES, listener) != 0) {
                    throw new AbortException("Failed to run ssh-add");
                }
            } finally {
                if (askpass != null && askpass.exists()) { // the ASKPASS script is self-deleting, anyway rather try to delete it in case of some error
                    askpass.delete();
                }
            }
        } finally {
            keyFile.delete();
        }
    }

    public Map<String, String> getEnv() {
        return agentEnv;
    }

    /**
     * Stops the agent.
     *
     * @param listener for logging.
     */
    public void stop(Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        if (launcher.launch().cmds("ssh-agent", "-k").envs(agentEnv).stdout(listener)
                .start().joinWithTimeout(1, TimeUnit.MINUTES, listener) != 0) {
            throw new AbortException("Failed to run ssh-agent -k");
        }
    }
    
    /**
     * Parses ssh-agent output.
     */
    private Map<String,String> parseAgentEnv(String agentOutput, TaskListener listener) throws IOException, InterruptedException {
        Map<String, String> env = new HashMap<>();

        // TODO better to just parse all env vars and keep them without picking out individual keys

        // get SSH_AUTH_SOCK
        env.put(AuthSocketVar, getAgentValue(agentOutput, AuthSocketVar));
        listener.getLogger().println(AuthSocketVar + "=" + env.get(AuthSocketVar));

        // get SSH_AGENT_PID
        env.put(AgentPidVar, getAgentValue(agentOutput, AgentPidVar));
        listener.getLogger().println(AgentPidVar + "=" + env.get(AgentPidVar));
        
        return env;
    }
    
    /**
     * Parses a value from ssh-agent output.
     */
    private String getAgentValue(String agentOutput, String envVar) {
        int pos = agentOutput.indexOf(envVar) + envVar.length() + 1; // +1 for '='
        int end = agentOutput.indexOf(';', pos);
        if(end < 0) end = agentOutput.length();
        return agentOutput.substring(pos, end);
    }
    
    /**
     * Creates a self-deleting script for SSH_ASKPASS. Self-deleting to be able to detect a wrong passphrase. 
     */
    private FilePath createAskpassScript(FilePath temp) throws IOException, InterruptedException {
        // TODO: assuming that ssh-add runs the script in shell even on Windows, not cmd
        //       for cmd following could work
        //       suffix = ".bat";
        //       script = "@ECHO %SSH_PASSPHRASE%\nDEL \"" + askpass.getAbsolutePath() + "\"\n";
        
        FilePath askpass = temp.createTextTempFile("askpass_", ".sh", "#!/bin/sh\necho \"$SSH_PASSPHRASE\"\nrm \"$0\"\n");

        // executable only for a current user
        askpass.chmod(0700);
        return askpass;
    }
}
