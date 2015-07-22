package com.cloudbees.jenkins.plugins.sshagent;

import com.trilead.ssh2.crypto.Base64;
import com.trilead.ssh2.packets.TypesWriter;
import hudson.model.User;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.server.UserAuth;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.auth.UserAuthPublicKey;
import org.apache.sshd.server.keyprovider.PEMGeneratorHostKeyProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.jenkinsci.main.modules.cli.auth.ssh.UserPropertyImpl;
import org.junit.Test;

import javax.rmi.CORBA.Util;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public class SSHServerTest {

    @Test
    public void sshServerAvailable() throws Exception {
        File hostKey = new File(System.getProperty("java.io.tmpdir") + "/key.ser");
        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setPort(22999);
        sshd.setHost("localhost");
        sshd.getProperties().put(SshServer.WELCOME_BANNER, "Welcome to the Mock SSH Server\n");
        //sshd.getProperties().put(SshServer.AUTH_METHODS, "publickey");
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKey.getPath()));
        sshd.setShellFactory(new ProcessShellFactory(new String[]{"uname", "-a"}, EnumSet.of(ProcessShellFactory.TtyOptions.ONlCr)));
        /*List<NamedFactory<UserAuth>> factories = new ArrayList<NamedFactory<UserAuth>>();
        factories.add(new UserAuthPublicKey.Factory());
        sshd.setUserAuthFactories(factories);*/

        sshd.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            @Override
            public boolean authenticate(String username, PublicKey publicKey, ServerSession session) {
                // Recover the public key in authorized_keys

                User u = User.get(username, false);

                UserPropertyImpl sshKey = u.getProperty(UserPropertyImpl.class);
                if (sshKey == null) {
                    return false;
                }

                if (!sshKey.isAuthorizedKey(getPublicKeySignature(publicKey))) {
                    return false;
                }

                return true;
            }

            private String getPublicKeySignature(PublicKey pk) {
                TypesWriter tw = new TypesWriter();
                if (pk instanceof RSAPublicKey) {
                    RSAPublicKey rpk = (RSAPublicKey) pk;
                    tw.writeString("ssh-rsa");
                    tw.writeMPInt(rpk.getPublicExponent());
                    tw.writeMPInt(rpk.getModulus());
                    return new String(Base64.encode(tw.getBytes()));
                }
                if (pk instanceof DSAPublicKey) {
                    DSAPublicKey rpk = (DSAPublicKey) pk;
                    tw.writeString("ssh-dss");
                    DSAParams p = rpk.getParams();
                    tw.writeMPInt(p.getP());
                    tw.writeMPInt(p.getQ());
                    tw.writeMPInt(p.getG());
                    tw.writeMPInt(rpk.getY());
                    return new String(Base64.encode(tw.getBytes()));
                }
                throw new IllegalArgumentException("Unknown key type: " + pk);
            }

            /**
             *
             * @param sig
             * @return
             */
            public boolean isAuthorizedKey(String sig) {
                try {
                    final BufferedReader r = new BufferedReader(new StringReader(authorizedKeys));
                    String s;
                    while ((s=r.readLine()) != null) {
                        String[] tokens = s.split("\\s+");
                        if (tokens.length>=2 && tokens[1].equals(sig))
                            return true;
                    }
                    return false;
                } catch (IOException e) {
                    return false;
                }
            }

        });

        sshd.start();
        Thread.sleep(Long.MAX_VALUE);
    }
}
