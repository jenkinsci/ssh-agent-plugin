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
import org.junit.*;

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

public class SSHAgentBase {

    public static int SSH_SERVER_PORT = 22999;

    public static String SSH_SERVER_HOST = "localhost";

    private SshServer sshd = null;

    protected void startMockSSHServer() throws Exception {
        File hostKey = new File(System.getProperty("java.io.tmpdir") + "/key.ser");
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(SSH_SERVER_PORT);
        sshd.setHost(SSH_SERVER_HOST);
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
                if (!isAuthorizedKey(getPublicKeySignature(publicKey))) {
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


            public boolean isAuthorizedKey(String sig) {
                try {
                    final BufferedReader r = new BufferedReader(new StringReader(getAuthorizedPublicKey()));
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
        System.out.println("Mock SSH Server is started");
    }

    protected void stopMockSSHServer() throws InterruptedException {
        if (sshd != null) {
            sshd.stop();
            System.out.println("Mock SSH Server is shutdown");
        }
    }

    /**
     * Returns a string with the authorized Public Key.
     *
     * @return
     */
    public String getAuthorizedPublicKey() {
        return  "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQDB+TU0nnxVKh+m4zV2DhPm8SM5dBWQW2maU2VQp/sKHdgMuGep422eKbNfm9u82kyh1gImJzQVFQaWX+h+SewxiT9Xm7yD4D2RYXuIXgxp5x5WBpQBIHcWgV9v/a+O0cIUnDJYCh5j3O2RT4CpqnrseRrcVoMFSI+sdSAseYC2CKFAIua1x4cUykEH0kE/vkt4WPDJCb7+mIhNpjJEhHW7etsSCcA+vKxux3Kw0TuMNb/o/jL631R7NrU5jo3LzjjgD2FX6wolkYEp9F7YWaXZY4BvopObAGe52aj20Oay7L6uxiFUq/NTOMrT5trJBY3LNOSJuFr+UWGuUSulwj6qR++Io5pTyHjJLaw+s+dXdOArFAeum5bbxhGcLa18eSFYM761wA4KLdVbwd1nXoIG3+wTSO1EQCbArqc7UIhQYKI/WYDpNROdKOTyIpIYXjHz4SZBYXOn9zXJGvgnPpuHoefkT2RB5ryrfr1GFmoV2Bd/i32KIdtiDVqzCZHp9y4ZLlxz3+beMA19dNGbdYgUuanzQuAqeDNK2AcAd0IcnSrmijrxs3oNPbKr1wX2cYdD01m8jhNEn1+JCRAYghI9VsUVeEfuydA942M9gAjIiMGp7L7j09+YaJ0KH3BH8ZVJl20ojjIEa5GkOLo4IK/DMflkgG/qupG2u94o77LaIQ== cloudbees@localhost";
    }
}
