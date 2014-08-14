/*
 * The MIT License
 *
 * Copyright (c) 2012, CloudBees, Inc., Stephen Connolly.
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

import com.cloudbees.jenkins.plugins.sshagent.Messages;
import com.cloudbees.jenkins.plugins.sshagent.RemoteAgent;
import hudson.model.TaskListener;
import org.apache.sshd.common.util.SecurityUtils;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;

import java.io.IOException;
import java.io.StringReader;
import java.security.KeyPair;

import java.lang.Runtime;
import java.lang.Process;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.lang.ProcessBuilder;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import java.io.File;
import java.io.FileWriter;

import org.apache.commons.io.IOUtils;

/**
 * An implementation that uses native SSH agent installed on a system.
 */
public class ExecRemoteAgent implements RemoteAgent {
    private final String AuthSocketVar = "SSH_AUTH_SOCK";
	private final String AgentPidVar = "SSH_AGENT_PID";
	
	/** Process builder keeping environment for all ExecRemoteAgent related processes. */
	private final ProcessBuilder processBuilder;
	
	/**
     * The listener in case we need to report exceptions
     */
    private final TaskListener listener;
	
	/**
	 * Process in which the ssh-agent is running.
	 */
	private final Process agent;
	
	/**
     * The socket bound by the agent.
     */
    private final String socket;

    /**
     * Constructor.
     *
     * @param listener the listener.
     * @throws Exception if the agent could not start.
     */
    public ExecRemoteAgent(TaskListener listener) throws Exception {
        this.processBuilder = new ProcessBuilder();
		this.listener = listener;
		
		listener.getLogger().println("ExecRemoteAgent::ExecRemoteAgent - starting native ssh-agent");
		
		this.agent = execProcess("ssh-agent");
		Map<String, String> agentEnv = parseAgentEnv(this.agent);
		
		if (agentEnv.containsKey(AuthSocketVar))
			socket = agentEnv.get(AuthSocketVar);
		else
			socket = ""; // socket is not set
		
		// set agent environment to the process builder to provide it to ssh-add which will be executed later
		processBuilder.environment().putAll(agentEnv);
    }
	
	private Process execProcess(String command) throws IOException {
		listener.getLogger().println("ExecRemoteAgent::execProcess - command: " + command);
		
		List<String> command_list = Arrays.asList(command.split(" "));
		processBuilder.command(command_list);
		Process process = processBuilder.start();
		processBuilder.command("");
		return process;
	}
	
	private String getAgentValue(String agentOutput, String envVar) {
		int pos = agentOutput.indexOf(envVar) + envVar.length() + 1; // +1 for '='
		int end = agentOutput.indexOf(";", pos);
		return agentOutput.substring(pos, end);
	}
	
	private Map<String,String> parseAgentEnv(Process agent) throws Exception{
		Map<String, String> env = new HashMap<String, String>();
		
		InputStream agentOutputReader = agent.getInputStream();
		ByteArrayOutputStream agentOutputStream = new ByteArrayOutputStream();
		IOUtils.copy(agentOutputReader, agentOutputStream);
		String agentOutput = agentOutputStream.toString();
		
		// get SSH_AUTH_SOCK
		env.put(AuthSocketVar, getAgentValue(agentOutput, AuthSocketVar));
		listener.getLogger().println(AuthSocketVar + "=" + env.get(AuthSocketVar));
		
		// get SSH_AGENT_PID
		env.put(AgentPidVar, getAgentValue(agentOutput, AgentPidVar));
		listener.getLogger().println(AgentPidVar + "=" + env.get(AgentPidVar));
		
		return env;
	}

    /**
     * {@inheritDoc}
     */
    public String getSocket() {
        listener.getLogger().println("ExecRemoteAgent::getSocket - socket: " + socket);
		return socket;
    }
	
	private boolean setReadOnlyForOwner(File file) {
		boolean ok = file.setExecutable(false, false);
		ok &= file.setWritable(false, false);
		ok &= file.setReadable(false, false);
		ok &= file.setReadable(true, true);
		return ok;
	}
	
    /**
     * {@inheritDoc}
     */
    public void addIdentity(String privateKey, final String passphrase, String comment) throws IOException {
        // TODO: handle keys with password
		if (!passphrase.isEmpty()) {
			throw new IOException("ExecRemoteAgent::addIdentity - a non empty passphrase, not implemented yet!");
		}
		
		File keyFile = File.createTempFile("private_key_", ".key");
		FileWriter keyWriter = new FileWriter(keyFile);
		keyWriter.write(privateKey);
		keyWriter.close();
		
		setReadOnlyForOwner(keyFile);
		
		Process sshAdd = execProcess("ssh-add " + keyFile.getPath());
		IOUtils.copy(sshAdd.getInputStream(), listener.getLogger());
		IOUtils.copy(sshAdd.getErrorStream(), listener.getLogger());
		
		try {
			sshAdd.waitFor();
		}
		catch (InterruptedException e) {
			// waiting or process somehow interrupted
		}
		
		if (!keyFile.delete()) {
			listener.getLogger().println("ExecRemoteAgent::addIdentity - could NOT delete a temp file with a private key!");
		}
    }

    /**
     * {@inheritDoc}
     */
    public void stop() {
		listener.getLogger().println("ExecRemoteAgent::stop");
		try {
			execProcess("ssh-agent -k");
		}
		catch (IOException e) {
			listener.error("ExecRemoteAgent::stop - " + e.getCause());
		}
		agent.destroy();
    }
}
