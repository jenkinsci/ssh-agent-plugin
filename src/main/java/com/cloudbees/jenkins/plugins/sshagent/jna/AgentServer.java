/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.cloudbees.jenkins.plugins.sshagent.jna;

import jnr.posix.POSIXFactory;
import jnr.unixsocket.UnixServerSocket;
import jnr.unixsocket.UnixServerSocketChannel;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;
import org.apache.commons.io.FileUtils;
import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.common.AbstractAgentClient;
import org.apache.sshd.agent.local.AgentImpl;
import org.apache.sshd.common.util.Buffer;
import org.apache.sshd.common.util.OsUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * A server for an SSH Agent. Portions of this code were copied directly from Apache MINA's SSH implementation.
 */
public class AgentServer {

    private final SshAgent agent;
    private String authSocket;
    private Thread thread;
    private UnixSocketAddress address;
    private UnixServerSocketChannel channel;
    private UnixServerSocket socket;
    private volatile boolean stopped = false;

    public AgentServer() {
        this(new AgentImpl());
    }

    public AgentServer(SshAgent agent) {
        this.agent = agent;
    }

    public SshAgent getAgent() {
        return agent;
    }

    public String start() throws Exception {
        authSocket = createLocalSocketAddress();
        address = new UnixSocketAddress(new File(authSocket));
        channel = UnixServerSocketChannel.open();
        channel.configureBlocking(true);
        socket = channel.socket();
        socket.bind(address);
        stopped = false;
        POSIXFactory.getPOSIX().chmod(authSocket, 0600);
        thread = new Thread() {
            public void run() {
                try {
                    while (!stopped) {
                        final UnixSocketChannel clientSock = channel.accept();
                        clientSock.configureBlocking(true);
                        new SshAgentSession(clientSock, agent);
                    }
                } catch (Exception e) {
                    if (!stopped) {
                        e.printStackTrace();
                    }
                }
            }
        };
        thread.start();
        return authSocket;
    }

    static String createLocalSocketAddress() throws IOException {
        String name;
        if (OsUtils.isUNIX()) {
            File socket = File.createTempFile("jenkins", ".jnr");
            FileUtils.deleteQuietly(socket);
            name = socket.getAbsolutePath();
        } else {
            File socket = File.createTempFile("jenkins", ".jnr");
            FileUtils.deleteQuietly(socket);
            name = "\\\\.\\pipe\\" + socket.getName();
        }
        return name;
    }

    public void close() {
        stopped = true;
        agent.close();
        safelyClose(channel);
        if (authSocket != null) {
            FileUtils.deleteQuietly(new File(authSocket));
        }
    }

    protected class SshAgentSession extends AbstractAgentClient implements Runnable {

        private final UnixSocketChannel channel;

        public SshAgentSession(UnixSocketChannel channel, SshAgent agent) {
            super(agent);
            this.channel = channel;
            new Thread(this).start();
        }

        public void run() {
            try {
                ByteBuffer buf = ByteBuffer.allocate(1024);
                while (!stopped) {
                    buf.rewind();
                    int result = channel.read(buf);
                    if (result > 0) {
                        buf.flip();
                        messageReceived(new Buffer(buf.array(), buf.position(), buf.remaining()));
                    } else {
                        break;
                    }
                }
            } catch (Exception e) {
                if (!stopped) {
                    e.printStackTrace();
                }
            } finally {
                safelyClose(channel);
            }
        }

        protected void reply(Buffer buf) throws IOException {
            ByteBuffer b = ByteBuffer.wrap(buf.array(), buf.rpos(), buf.available());
            int result = channel.write(b);
            if (result < 0) {
                throw new IOException("Could not write response to socket");
            }
        }

    }

    private static void safelyClose(Closeable channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

}
