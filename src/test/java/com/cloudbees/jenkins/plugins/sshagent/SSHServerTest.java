package com.cloudbees.jenkins.plugins.sshagent;

import org.apache.sshd.SshServer;
import org.apache.sshd.common.Factory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.junit.Test;

import java.security.PublicKey;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

public class SSHServerTest {

    @Test
    public void sshServerAvailable() throws Exception {
        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setPort(22999);
        sshd.setHost("localhost");
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("./sshkey", SimpleGeneratorHostKeyProvider.SSH_RSA));
        sshd.setShellFactory(new MyEchoShellFactory());

        sshd.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            @Override
            public boolean authenticate(String s, PublicKey publicKey, ServerSession serverSession) {
                return true;
            }
        });
        sshd.start();
        while (true);
    }

    public static class MyEchoShellFactory extends EchoShellFactory {

        @Override
        public Command create() {
            return new MyEchoShell();
        }

        public static class MyEchoShell extends EchoShell {

            public static CountDownLatch latch;

            @Override
            public void destroy() {
                if (latch != null) {
                    latch.countDown();
                }
                super.destroy();
            }
        }
    }
}
