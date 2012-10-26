package com.cloudbees.jenkins.plugins.sshagent.unix;

import com.cloudbees.jenkins.plugins.sshagent.RemoteAgent;
import hudson.Launcher;
import hudson.model.TaskListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Use the ssh-agent command.
 *
 * @todo support passphrase
 */
public class UnixRemoteAgent implements RemoteAgent {
    /**
     * The socket bound by the agent.
     */
    private final String socket;

    /**
     * The pid of the ssh-agent process
     */
    private final int pid;

    /**
     * Our launcher;
     */
    private final Launcher launcher;

    /**
     * Our listener.
     */
    private final TaskListener listener;

    public UnixRemoteAgent(Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        this.launcher = launcher;
        this.listener = listener;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        this.launcher.launch()
                .cmds("/usr/bin/ssh-agent")
                .stderr(listener.getLogger())
                .stdout(out)
                .start()
                .joinWithTimeout(5, TimeUnit.SECONDS, listener);
        String output = out.toString("UTF-8");
        listener.getLogger().println(output);
        Matcher pidMatcher = Pattern.compile("SSH_AGENT_PID=(\\d+)[; ]").matcher(output);
        Matcher sockMatcher = Pattern.compile("SSH_AUTH_SOCK=([^ ]+)[; ]").matcher(output);
        if (pidMatcher.find() && sockMatcher.find()) {
            pid = Integer.parseInt(pidMatcher.group(1));
            socket = sockMatcher.group(1);
        } else {
            listener.getLogger().println("Could not determine PID");
            throw new IOException("Could not start ssh-agent");
        }
    }

    public String getSocket() {
        return socket;
    }

    public void addIdentity(String privateKey, String passphrase, String comment) throws IOException {
        this.launcher.launch().cmds("ssh-add", "-")
                .envs("SSH_AUTH_SOCK=" + socket, "SSH_AGENT_PID=" + Integer.toString(pid))
                .stdin(new ByteArrayInputStream(privateKey.getBytes("UTF-8")))
                .stdout(listener)
                .stderr(listener.getLogger())
                .start();
    }

    public void stop() {
        try {
            launcher.launch()
                    .cmds("ssh-agent", "-k")
                    .envs("SSH_AUTH_SOCK=" + socket, "SSH_AGENT_PID=" + Integer.toString(pid))
                    .start()
                    .joinWithTimeout(10, TimeUnit.SECONDS, listener);
        } catch (IOException e) {
            e.printStackTrace(listener.getLogger());
        } catch (InterruptedException e) {
            e.printStackTrace(listener.getLogger());
        }
    }
}
