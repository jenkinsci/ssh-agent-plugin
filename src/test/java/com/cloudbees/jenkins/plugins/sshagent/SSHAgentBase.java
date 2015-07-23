package com.cloudbees.jenkins.plugins.sshagent;

import com.trilead.ssh2.crypto.Base64;
import com.trilead.ssh2.packets.TypesWriter;
import org.apache.sshd.SshServer;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.shell.ProcessShellFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.EnumSet;

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
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKey.getPath()));
        sshd.setShellFactory(new ProcessShellFactory(new String[]{"uname", "-a"}, EnumSet.of(ProcessShellFactory.TtyOptions.ONlCr)));

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
     * Returns a string with a Private Key with Passphrase.
     *
     * @return
     */
    public String getPrivateKey() {
        return    "-----BEGIN RSA PRIVATE KEY-----\n"
                + "Proc-Type: 4,ENCRYPTED\n"
                + "DEK-Info: AES-128-CBC,D584FC96A8C0A05CBB0658D8051A626B\n"
                + "\n"
                + "7BIi0Vi94eLJFeDDM4G0rZLK4le7Y6j2zgD5kvVgnQWxALowBLvSonwbrBGMpoQC\n"
                + "m70PBg40oORtb9IVjF/SS2PDk0Rf+cDyTKZAHwjLU2eNLs4Aex1IWU39/vVHI7CZ\n"
                + "JOsHSsZyTO+r5q2oUpu4w37reS4N4bRQQeFluRb7j5ZAatQzL8Zm6GdWLK2929Zi\n"
                + "RGuCj/IFF8Z3bf4unvm32Llk/Ky5rbsGB1MJQ4OrRIF44J0xh3gKiuLML1XhRl/M\n"
                + "1oUZki19fTSYNJAv/PUgOOhBMm5F61pAwXT1+ZFaUeyaZC7wvHyff0NhGAi5dl0/\n"
                + "a3fy+z7t7T6oHURwFE6s1lig1cGZHjlZ/OzlhJwKq29b6r+FkH/DEOUhU6tuNLJW\n"
                + "of5Fz60Xi0cw0RwqOOvkCuKVAfzsVRwj+oJf36uJov5DYRSpoCtwJOHmINmVWUzA\n"
                + "30jRFyUS6tj01ozIeU3Te+Vh542UHwDLpluyI4Vdm0KwVajq8TKu0D7goksYF0w0\n"
                + "6Ee5OkfcBQxuApgKYPNQX8b6NA5ky4WpNcYDiX9ZVsakzQ2/qEkxeiXJWPMcQiVz\n"
                + "l57RXg8+Wdy10GZleL4bOu41ro+rGsV7ZRQeCE5BSp1qmjFrqmFGGpKprP/ysMX9\n"
                + "9kirUg3XHy8Q2u9ds3HsEuEM9hqyVNGkiTOuj8ATb7yit7dZjsQOPG1abK0GK3tf\n"
                + "caNnVZGFGYquwoYHWXyeuvfSpICTU0WhhSUfu2uJJRYILWweB0dX3n9WtYS7wWhh\n"
                + "mVQyQame/JsL+FOiiFoIgAq8NIUARDEHW8KvOJfJEs+jKDF8la2QdNMJZXPQ/Trp\n"
                + "/PFAVu36CqsFO1PEGLC9SHZofl/9RF80F9OQVa9jc6iLomqXzaQ8nxJZGt0UWLLc\n"
                + "76wE4WYDmFba7WcAM8fYe+df4PkgoVZyEe0Ri8d2VGAHUS+kvLw83PbLS+PloOJu\n"
                + "U4KgqXkMVVvHEf2Dw174NYz/Ox4j52W8qeRK4PD3bqHF5MFLyyIU+vLNE70OiBsz\n"
                + "wxsXwmQQrHUk1pXnr2el18Kvi3xZ2rX4VOLreKY/Ww7VZWLZJksNddXjMfFCPo2V\n"
                + "KmD7CvCwwtb6hiUYdgmOyNtbLxTAql0DSC9ACjuEUUY6CxsyIEMog8VLMWRy0Txw\n"
                + "ek5dQ7UvyqqGTuZL9RjbrLE+4Q4+05p0TRtuyumF2ku66/DhJQJQ02ka21bdK8eX\n"
                + "O+/9UrabB/EJVDrRYCtYIEeV1US7c0i2oWxJflMnS6iRh+YNe7Wa1AdVCCnQpO3O\n"
                + "i3xca64FD/rrwKuoRYgvmQeDwVf5/VQI/vB3VzsFDsEfypnbUIu24TxUjVk8Wnea\n"
                + "KK0hrupTOFX+SlzH9ejYsOVnQBgixmiIJ2d6kZ9tHTwnZqUYKrGdyvvhJWGnYmt+\n"
                + "wT/74T2PGDJkNCMwLrcRGQbyi/rFNk0rkFJZx+vWTb6Rddw9foKQ1u4Ao5S7v9Sp\n"
                + "PCim9oUY3T/YzxhBI2Cx/pw9N8Fbjs8ZyhLKiaTtC8PF4KaqvLLqk4hJ5I5QkzMh\n"
                + "99pZd1hzGMNUUTpfDEpsHSC1BHPdbtSnF0ReeODtu17oP7ZTqN7MLwkSwOXvu8so\n"
                + "WRYgwCfjD6u45NC6wngX8zMcBlsuwTEwCzdaL//OPw8NWNQY+Yk99aj5WpCYz7lE\n"
                + "r7uTFhDxpoRyMUfEZnyRiDrosNLUPuQ05gmrg8brDjTMHEBYygE2ktCCQunRLZEo\n"
                + "sLwUUmh9/r6jX7z9uWhBnHFHNuQt58aQiFcQ4it+CW93U7yWIauhWzm/VD+3UT3R\n"
                + "1KqhfaiKtq4jcAJ1TAOhPMY+68rq0SGKF68r1EsJLVpCTpq2VRHU+XtPk//PzheQ\n"
                + "eJEwEk51iBwJv4Qa4RCslzpwRnwnN1R2THkbCek5uYGOaUGDoAdHQtYSx7brH7S7\n"
                + "IWGUl/lE4ZXw8SQVYGZ8jSBpO4ykEsanbuaYH7a6QZyMvpOrxMpd0qmNGZuRmTns\n"
                + "R2I8oi1SOoagoA4gU4RyzWfde/DB2bFvaav39GE1I1WoyJd8IQmt84aL9Fbnv1IE\n"
                + "Z8vBPyqgmYNPTI18vysWQBPeIky696K2KNv5Idnk3vEApY/F53Xm3SrFEZnaXyWQ\n"
                + "+PC6ZPvfEK1flfzKtwrtpqJ9xSB5C7iBd0xzquyCrOVycDMonuL+fLm2DdvRmUMu\n"
                + "OhjuOsCZKKh0u8DSaf/1Pkge3/k//zeYS2hnb9TMJ/hkSuu98Xz+spn3IKYM8wxA\n"
                + "1cibn1ENxWTGM3dj5BAJJpgNN4YhZxXyAtGoIOySZ/P6bvfXJI6EaItdb/2heOl/\n"
                + "U04itVVwFZcYcibswCPZCF3stfKsKBnUBlW8MfV3QAkM1iRXSPQooexZ5d5pcCqA\n"
                + "Iix2WD2WdY4SoaCrfBiAekqHVNAeMm7Rxl31c/32D7i3oqAUb12Ic9aaFjkEobi/\n"
                + "Mf0w9U/kkCKSWS+ad6Q7VsUfUTiHfcLiWkmLcnzbd2FYYfh2P85j9xnu4DS3a/Jf\n"
                + "uVyqYakcz+GhxPLPDt3aaO0SykU9Ub8BYRYkIz1sEjVgfWacYoX5v7bFE99jzD8n\n"
                + "aMXhA3os82INX/FFqo/Np/G9nQJ7zaOOPzfK0CaKERvdpfQtvkmqpPyovgT6pRj8\n"
                + "hk0d6F7lCD0PYXvAfeqa6w6aqLUqruT9YYCAGn6N+6LY6XqN28r85kN9q/T/4VEs\n"
                + "ZjGQ2J+HNBqBmwWpORZztuLMxUSb/1j5Oc2ZN8J9DX2DYBG3qmx4VKtokBWsgSSN\n"
                + "4SzqDwbBz4F3/GmZTKkQrvKLwhuoKxbkODUHkakR+BglJk1wB4Ydd7pcwcbCzJ1a\n"
                + "b/ShIoWNNBy12+8vz08R4ppprk65gxRXWUDlsPg3I5dIerrorDYhD5L0kMbRSEfl\n"
                + "Cu4ugfR0aBBHfmk2hqZQCe1rUyaXE0lJzzMS/d9o64Eu3pW0UfT2PEubnverDaVI\n"
                + "E0IhuacLwNdvnCTInPiihClRxaGI+gGRyZdiTKgrhdpSGS8mqdoc4/H2HDScdbzM\n"
                + "8g1TsUTEQPmlO9swV76cYwun+tdsEZMxwuVqIP7MMQSTZGXreVOb1jCi9fTniW5y\n"
                + "eEbGhq44GcZ6mt4w7ANJC7KDSDVcJP58O65c7W9MnYurnBTIftDTEdoksg+psXdA\n"
                + "-----END RSA PRIVATE KEY-----\n";
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
