package com.cloudbees.jenkins.plugins.sshagent;

import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.TaskListener;

/**
 * @author stephenc
 * @since 26/10/2012 14:32
 */
public abstract class RemoteAgentFactory implements ExtensionPoint {
    public abstract String getDisplayName();

    public abstract boolean isSupported(Launcher launcher, TaskListener listener);

    public abstract RemoteAgent start(Launcher launcher, TaskListener listener) throws Throwable;
}
