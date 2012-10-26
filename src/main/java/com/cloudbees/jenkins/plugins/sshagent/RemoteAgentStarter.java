package com.cloudbees.jenkins.plugins.sshagent;

import hudson.model.BuildListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;

/**
 * Callable to start the remote agent.
 */
public class RemoteAgentStarter implements Callable<RemoteAgent, Throwable> {
    /**
     * Need to pass this through.
     */
    private final BuildListener listener;

    /**
     * Constructor.
     *
     * @param listener the listener to pass to the agent.
     */
    public RemoteAgentStarter(BuildListener listener) {
        this.listener = listener;
    }

    /**
     * {@inheritDoc}
     */
    public RemoteAgent call() throws Throwable {
        return Channel.current().export(RemoteAgent.class, new RemoteAgentImpl(listener));
    }
}
