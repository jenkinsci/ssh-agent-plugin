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
import hudson.EnvVars;
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
    private static final String SSHAGENT_USR_BIN_PATH_EXTENSION = "PATH+SSH_AGENT_USR_BIN";

    /** Default timeout, in minutes, applied to the agent commands when none is configured. */
    public static final int DEFAULT_TIMEOUT_MINUTES = 1;

    /** Agent environment used for {@code ssh-add} and {@code ssh-agent -k}. */
    private final Map<String, String> agentEnv;

    /** Environment of the step or build the agent was started for. Never {@code null}. */
    private final EnvVars contextEnv;

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
        this(launcher, listener, timeoutMinutes, executablePath, new EnvVars());
    }

    /**
     * Launches a native {@code ssh-agent}.
     *
     * @param launcher       launches the agent process.
     * @param listener       for logging.
     * @param timeoutMinutes how long, in minutes, to wait for each of the {@code ssh-agent},
     *                       {@code ssh-add} and {@code ssh-agent -k} commands before giving up.
     * @param executablePath the path to the ssh-agent executable (may be relative) or null to use the default.
     * @param contextEnv     the environment of the step or build the agent is started for, or {@code null}.
     *                       A custom {@code PATH} in it (e.g. set via {@code withEnv}) is honored when
     *                       looking up the default {@code ssh-agent} executable, and its other variables
     *                       are passed to the {@code ssh-agent}/{@code ssh-add} commands.
     * @since 431
     */
    public ExecRemoteAgent(Launcher launcher, TaskListener listener, int timeoutMinutes, String executablePath,
            EnvVars contextEnv) throws IOException, InterruptedException {
        // A timeout of zero (or less) comes from job configurations persisted before the timeout
        // was configurable; treat it as the default rather than timing out immediately.
        this.timeoutMinutes = timeoutMinutes > 0 ? timeoutMinutes : DEFAULT_TIMEOUT_MINUTES;
        this.contextEnv = contextEnv != null ? contextEnv : new EnvVars();
        this.isWindowsAgent = isWindowsAgent(launcher, listener);
        boolean isGitSSHAgentUsed = executablePath == null && isWindowsAgent;
        FilePath sshAgentPath = isGitSSHAgentUsed ? searchSSHAgentExeForWindows(launcher, listener)
                : toSSHAgentPath(executablePath, launcher);
        String bin = toBinPrefix(sshAgentPath, launcher);

        String agentOut;
        try {
            agentOut = executeCommand(p -> p.cmds("ssh-agent").envs(this.contextEnv), launcher, listener, true, bin);
        } catch (IOException e) {
            // "ssh-agent" could not even be started via the default lookup, which resolves a bare
            // command name against the launcher process' own ambient PATH, not the environment
            // passed to the launched process (see issue #227). If the step/build has a custom PATH
            // (e.g. set via withEnv) that was not considered so far, retry once by resolving
            // ssh-agent against that PATH explicitly, instead of failing outright. Only attempted
            // for the non-Windows default case: an explicit executablePath is already resolved
            // above, and Windows already has its own git-based search independent of PATH.
            FilePath resolved = executablePath == null && !isGitSSHAgentUsed
                    ? findOnPath("ssh-agent", this.contextEnv.get("PATH"), launcher) : null;
            if (resolved == null) {
                throw e;
            }
            bin = toBinPrefix(resolved, launcher);
            agentOut = executeCommand(p -> p.cmds("ssh-agent").envs(this.contextEnv), launcher, listener, true, bin);
        }
        this.sshAgentBin = bin;
        agentEnv = parseAgentEnv(agentOut, listener); // TODO could include local filenames, better to look up remote charset
        if (isGitSSHAgentUsed) {
            // Prepend <git-home>\\usr\\bin to PATH to ease ssh tool usage within sshagent block
            agentEnv.put(SSHAGENT_USR_BIN_PATH_EXTENSION, this.sshAgentBin);
            listener.getLogger().println(SSHAGENT_USR_BIN_PATH_EXTENSION + "=" + this.sshAgentBin);
        }
    }

    private static String toBinPrefix(FilePath sshAgentPath, Launcher launcher) {
        return Optional.ofNullable(sshAgentPath).map(FilePath::getParent)
                .map(p -> p.getRemote() + (launcher.isUnix() ? '/' : '\\')).orElse("");
    }

    /**
     * Locates {@code executableName} on the directories listed in {@code pathValue}.
     *
     * @return the found executable, or {@code null} if {@code pathValue} is unset/blank or no match
     *         was found, in which case the caller keeps the previous default behaviour of launching
     *         the bare command name (resolved against the launcher process' own ambient PATH).
     */
    static FilePath findOnPath(String executableName, String pathValue, Launcher launcher)
            throws IOException, InterruptedException {
        if (pathValue == null || pathValue.isBlank()) {
            return null;
        }
        for (String dir : pathValue.split(":", -1)) {
            if (dir.isBlank()) {
                continue;
            }
            FilePath candidate = new FilePath(launcher.getChannel(), dir).child(executableName);
            if (candidate.exists()) {
                return candidate;
            }
        }
        return null;
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
        String gitPaths = executeCommand(p -> p.cmds("where", "git").quiet(true), launcher, listener, true, "");
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
        return executeCommand(processConfig, launcher, listener, failOnError, sshAgentBin);
    }

    private String executeCommand(Consumer<ProcStarter> processConfig, Launcher launcher, TaskListener listener,
            boolean failOnError, String binPrefix) throws IOException, InterruptedException {
        ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ProcStarter starter = launcher.launch().stdout(stdOut).stderr(stderr);
        processConfig.accept(starter);
        String cmd = starter.cmds().get(0); // assume first argument is program name
        if (cmd.startsWith("ssh-")) {
            starter.cmds().set(0, binPrefix + cmd); // Prefix ssh agent commands with user-specified prefix
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

            FilePath askpass = null;
            try {
                Map<String,String> env = new HashMap<>(contextEnv);
                env.putAll(agentEnv); // ssh-agent's own values take precedence over same-named pipeline vars
                if (passphrase != null) {
                    askpass = createAskpassScript(temp, isWindowsAgent);
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
        Map<String, String> env = new HashMap<>(contextEnv);
        env.putAll(agentEnv); // ssh-agent's own values take precedence over same-named pipeline vars
        executeCommand(p -> p.cmds("ssh-agent", "-k").envs(env).stdout(listener), launcher, listener, false);
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
    static FilePath createAskpassScript(FilePath temp, boolean isWindowsAgent)
            throws IOException, InterruptedException {
        FilePath askpass;
        if (isWindowsAgent) {
            boolean pathContainsSpaces = temp.getRemote().contains(" ");
            // Delayed expansion (!VAR! instead of %VAR%) defers substitution of SSH_PASSPHRASE until
            // after cmd.exe has already tokenized the line, so characters in the passphrase such as
            // & or | are no longer re-parsed as command separators.
            // See https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/setlocal#parameters
            // Using echo( ensures empty passphrases or those containing ! work too
            askpass = temp.createTextTempFile("askpass_", ".bat", """
                    @SetLocal EnableDelayedExpansion
                    @ECHO(!SSH_PASSPHRASE!
                    @start /b cmd /c del "%~f0" & exit /b
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
