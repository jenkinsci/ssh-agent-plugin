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
import hudson.Functions;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.slaves.WorkspaceList;
import jenkins.security.MasterToSlaveCallable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Runs a native SSH agent installed on a system.
 */
public final class ExecRemoteAgent implements Serializable {
    private static final String AuthSocketVar = "SSH_AUTH_SOCK";
    private static final String AgentPidVar = "SSH_AGENT_PID";

    /** Default timeout, in minutes, applied to the agent commands when none is configured. */
    public static final int DEFAULT_TIMEOUT_MINUTES = 1;

    /** Agent environment used for {@code ssh-add} and {@code ssh-agent -k}. */
    private final Map<String, String> agentEnv;

    /** Timeout, in minutes, for the {@code ssh-agent}, {@code ssh-add} and {@code ssh-agent -k} commands. */
    private final int timeoutMinutes;

    /** The path of the ssh-agent executable. */
    private final String sshAgentBin;

    private final boolean isWindowsAgent;

    /**
     * Launches a native {@code ssh-agent}.
     *
     * @param launcher       launches the agent process.
     * @param listener       for logging.
     * @param timeoutMinutes how long, in minutes, to wait for each of the {@code ssh-agent},
     *                       {@code ssh-add} and {@code ssh-agent -k} commands before giving up.
     * @param executablePath the path to the ssh-agent executable (may be relative) or null to use the default.
     * @since 405
     */
    public ExecRemoteAgent(Launcher launcher, TaskListener listener, int timeoutMinutes, String executablePath)
            throws IOException, InterruptedException {
        // A timeout of zero (or less) comes from job configurations persisted before the timeout
        // was configurable; treat it as the default rather than timing out immediately.
        this.timeoutMinutes = timeoutMinutes > 0 ? timeoutMinutes : DEFAULT_TIMEOUT_MINUTES;
        this.isWindowsAgent = isWindowsAgent(launcher, listener);
        FilePath sshAgentPath = executablePath == null && isWindowsAgent
                ? searchSSHAgentExeForWindows(launcher, listener)
                : toSSHAgentPath(executablePath, launcher);
        this.sshAgentBin = Optional.ofNullable(sshAgentPath).map(FilePath::getParent)
                .map(p -> p.getRemote() + (launcher.isUnix() ? '/' : '\\')).orElse("");

        String agentOut = executeCommand(p -> p.cmds("ssh-agent"), launcher, listener, true);
        agentEnv = parseAgentEnv(agentOut, listener); // TODO could include local filenames, better to look up remote charset
    }

    private static FilePath toSSHAgentPath(String executable, Launcher launcher) {
        if (executable == null || executable.isBlank()) {
            return null;
        }
        FilePath path = new FilePath(launcher.getChannel(), executable);
        if (!Set.of("ssh-agent", "ssh-agent.exe").contains(path.getName())) {
            throw new IllegalArgumentException(
                    "Not an ssh-agent executable path (filename must be ssh-agent(.exe)): " + executable);
        }
        return path;
    }

    private FilePath searchSSHAgentExeForWindows(Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        try { // Git plugin is optional, handle a potential absence
            // Search for the absolute path to the home directory of a git installation at
            // the agent computer. Just git(.exe) is insufficient, as it only searches PATH.
            Optional<String> defaultGitHome = Optional.ofNullable(hudson.plugins.git.GitTool.getDefaultInstallation())
                    .map(hudson.plugins.git.GitTool::getHome)
                    .filter(home -> !"git".equals(home) && !"git.exe".equals(home));
            if (defaultGitHome.isPresent()) {
                Optional<FilePath> sshAgentExe = extractGitSSHAgentExe(List.of(defaultGitHome.get()), launcher);
                if (sshAgentExe.isPresent()) {
                    return sshAgentExe.get();
                }
            }
        } catch (NoClassDefFoundError e) { // git plugin is absent -> ignore
        }
        // Search a local git installation
        String gitPaths = executeCommand(p -> p.cmds("where", "git").quiet(true), launcher, listener, true);
        Optional<FilePath> sshAgentExe = extractGitSSHAgentExe(gitPaths.lines().filter(l -> !l.isEmpty())::iterator,
                launcher);
        return sshAgentExe.orElseThrow(() -> new IllegalStateException(
                "Executing with default ssh-agent on Windows is not supported and an alternative implementation from a git installation is not available."));
    }

    private static final Pattern GIT_EXE_PATH = Pattern.compile("\\\\(cmd|bin)\\\\git(\\.exe)?$");
    private static final String GIT_SSH_AGENT_PATH_WINDOWS = "usr\\bin\\ssh-agent.exe";

    private static Optional<FilePath> extractGitSSHAgentExe(Iterable<String> gitHomeOrExePaths, Launcher launcher)
            throws IOException, InterruptedException {
        for (String path : gitHomeOrExePaths) {
            Optional<FilePath> git = Optional.of(new FilePath(launcher.getChannel(), path));
            if (GIT_EXE_PATH.matcher(path).find()) {
                git = git.map(FilePath::getParent).map(FilePath::getParent);
            }
            Optional<FilePath> sshAgentExe = git.map(p -> p.child(GIT_SSH_AGENT_PATH_WINDOWS));
            if (sshAgentExe.isPresent() && sshAgentExe.get().exists()) {
                return sshAgentExe;
            }
        }
        return Optional.empty();
    }

    private String executeCommand(Consumer<ProcStarter> processConfig, Launcher launcher, TaskListener listener,
            boolean failOnError) throws IOException, InterruptedException {
        ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ProcStarter starter = launcher.launch().stdout(stdOut).stderr(stderr);
        processConfig.accept(starter);
        String cmd = starter.cmds().get(0); // assume first argument is program name
        if (cmd.startsWith("ssh-")) {
            starter.cmds().set(0, sshAgentBin + cmd); // Prefix ssh agent commands with user-specified prefix
        }
        int status = starter.start().joinWithTimeout(timeoutMinutes, TimeUnit.MINUTES, listener);
        if (status != 0) {
            String failure = (describeFailure(String.join(" ", starter.cmds()), status,
                    stdOut.toString(StandardCharsets.US_ASCII), stderr.toString(StandardCharsets.US_ASCII)));
            if (failOnError) {
                throw new AbortException(failure);
            } else {
                listener.getLogger().println(failure);
            }
        }
        return stdOut.toString(StandardCharsets.US_ASCII);
    }

    /**
     * Builds a diagnostic message for a command that exited with a non-zero status.
     * The exit code is always included so the failure is never reported with an empty reason.
     *
     * @param command the command that failed, e.g. {@code ssh-add}.
     * @param status  the process exit code.
     * @param stdout  the captured standard output.
     * @param stderr  the captured standard error.
     * @return a message including the command, the exit code and whichever of standard error or
     *         standard output is available.
     * @since 405
     */
    static String describeFailure(String command, int status, String stdout, String stderr) {
        String detail = stderr.strip();
        if (detail.isEmpty()) {
            detail = stdout.strip();
        }
        String message = "Failed to run " + command + " (exit code " + status + ")";
        return detail.isEmpty() ? message : message + ": " + detail;
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
                    env.put("SSH_ASKPASS_REQUIRE", "force"); // force using SSH_ASKPASS
                    env.put("DISPLAY", "bogus"); // legacy (and backwards compatible) way to force using SSH_ASKPASS
                    env.put("SSH_ASKPASS", askpass.getRemote());
                }
                
                // as the next command is in quiet mode, we just add a message to the log
                listener.getLogger().println("Running ssh-add (command line suppressed)");
                
                executeCommand(p -> p.quiet(true).cmds("ssh-add", keyFile.getRemote()).envs(env).stdout(listener),
                        launcher, listener, true);
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
     * <p>Stopping is best-effort cleanup: if the {@code ssh-agent} process is already gone (for example
     * killed during a long build), a failed {@code ssh-agent -k} is logged rather than thrown, so that
     * it does not fail an otherwise successful build.
     *
     * @param listener for logging.
     */
    public void stop(Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        executeCommand(p -> p.cmds("ssh-agent", "-k").envs(agentEnv).stdout(listener), launcher, listener, false);
    }
    
    /**
     * Parses ssh-agent output.
     */
    private Map<String,String> parseAgentEnv(String agentOutput, TaskListener listener) throws IOException, InterruptedException {
        Map<String, String> env = new HashMap<>();

        // TODO better to just parse all env vars and keep them without picking out individual keys

        // get SSH_AUTH_SOCK
        String socketPath = getAgentValue(agentOutput, AuthSocketVar);
        if (isWindowsAgent) {
            // Convert socket path (originally in linux-style) into Windows-style.
            // The ssh-agent tools of a git installation can handle Windows (and Linux)
            // style paths. But other Windows tools can only handle Windows-style.
            socketPath = toWindowsPath(socketPath);
        }
        env.put(AuthSocketVar, socketPath);
        listener.getLogger().println(AuthSocketVar + "=" + env.get(AuthSocketVar));

        // get SSH_AGENT_PID
        env.put(AgentPidVar, getAgentValue(agentOutput, AgentPidVar));
        listener.getLogger().println(AgentPidVar + "=" + env.get(AgentPidVar));
        
        return env;
    }

    private static final Pattern WINDOWS_DRIVE_LETTER_IN_UNIX_PATH = Pattern.compile("^/[a-zA-Z]/");

    /**
     * Converts a POSIX-style path reported by an MSYS {@code ssh-agent} into a Windows-style
     * absolute path, when possible.
     *
     * <p>Only paths under a drive-letter mount (e.g. {@code /c/Users/...}) can be converted this
     * way. A path under a virtual MSYS mount point such as {@code /tmp} or {@code /usr} has no
     * real location that can be derived by string substitution, so it is returned unchanged
     * rather than turned into a driveless, relative Windows path that {@code ssh-add} cannot
     * resolve. The ssh-agent tools of a git installation accept the POSIX form just as well.
     *
     * @since 424
     */
    static String toWindowsPath(String socketPath) {
        if (!WINDOWS_DRIVE_LETTER_IN_UNIX_PATH.matcher(socketPath).find()) {
            return socketPath;
        }
        char driveLetter = Character.toUpperCase(socketPath.charAt(1));
        socketPath = driveLetter + ":\\" + socketPath.substring(3);
        return socketPath.replace('/', '\\');
    }

    /**
     * Parses a value from ssh-agent output.
     *
     * @param agentOutput the raw output produced by {@code ssh-agent}.
     * @param envVar      the environment variable to extract, e.g. {@code SSH_AUTH_SOCK}.
     * @return the value assigned to {@code envVar}.
     * @throws AbortException if {@code envVar} is absent or is not terminated by {@code ';'}, which
     *                        happens when {@code ssh-agent} produced unexpected output.
     * @since 405
     */
    static String getAgentValue(String agentOutput, String envVar) throws AbortException {
        int keyIndex = agentOutput.indexOf(envVar);
        if (keyIndex == -1) {
            throw new AbortException("Unexpected ssh-agent output, missing " + envVar + ": " + agentOutput);
        }
        int pos = keyIndex + envVar.length() + 1; // +1 for '='
        int end = agentOutput.indexOf(';', pos);
        if (end == -1) {
            throw new AbortException(
                    "Unexpected ssh-agent output, " + envVar + " was not terminated by ';': " + agentOutput);
        }
        return agentOutput.substring(pos, end);
    }
    
    /**
     * Creates a self-deleting script for SSH_ASKPASS. Self-deleting to be able to detect a wrong passphrase.
     */
    private FilePath createAskpassScript(FilePath temp) throws IOException, InterruptedException {
        FilePath askpass;
        if (isWindowsAgent) {
            boolean pathContainsSpaces = temp.getRemote().contains(" ");
            askpass = temp.createTextTempFile("askpass_", ".bat", """
                    @ECHO %SSH_PASSPHRASE%
                    start /b cmd /c del "%~f0" & exit /b
                    """, !pathContainsSpaces);
        } else {
            askpass = temp.createTextTempFile("askpass_", ".sh", """
                    #!/bin/sh
                    echo "$SSH_PASSPHRASE"
                    rm "$0"
                    """);
            // executable only for a current user
            askpass.chmod(0700);
        }
        return askpass;
    }

    // --- utility methods ---

    /**
     * Returns {@code true} if the executor is running on Windows (the controler's
     * OS is ignored).
     */
    private boolean isWindowsAgent(Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        if (launcher.isUnix()) {
            return false;
        }
        VirtualChannel channel = launcher.getChannel();
        if (channel == null) {
            listener.getLogger().println("Failed to determine OS of non UNIX system: Channel is null");
            return false;
        }
        return channel.call(new IsWindows());
    }

    private static final class IsWindows extends MasterToSlaveCallable<Boolean, RuntimeException> {
        private static final long serialVersionUID = -2033363399440315941L;

        @Override
        public Boolean call() {
            return Functions.isWindows();
        }
    }
}
