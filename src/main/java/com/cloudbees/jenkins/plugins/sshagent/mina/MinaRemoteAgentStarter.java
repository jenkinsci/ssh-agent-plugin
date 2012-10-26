package com.cloudbees.jenkins.plugins.sshagent.mina;

import com.cloudbees.jenkins.plugins.sshagent.RemoteAgent;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;

/**
 * Callable to start the remote agent.
 */
public class MinaRemoteAgentStarter implements Callable<RemoteAgent, Throwable> {
    /**
     * Need to pass this through.
     */
    private final TaskListener listener;

    /**
     * Constructor.
     *
     * @param listener the listener to pass to the agent.
     */
    public MinaRemoteAgentStarter(TaskListener listener) {
        this.listener = listener;
    }

    /**
     * {@inheritDoc}
     */
    public RemoteAgent call() throws Throwable {
        return Channel.current().export(RemoteAgent.class, new MinaRemoteAgent(listener));
    }
}
