package com.cloudbees.jenkins.plugins.sshagent;

import hudson.Launcher;

import java.io.IOException;

/**
 * Provider of launcher objects. Needed in situations where the launcher object
 * might change in between usages and where it is not possible to propagate
 * the object in other ways.
 */
public interface LauncherProvider {

    /**
     * Provides an up to date launcher
     */
    Launcher getLauncher() throws IOException, InterruptedException;
}
