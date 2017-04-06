package com.cloudbees.jenkins.plugins.sshagent;

import com.trilead.ssh2.crypto.Base64;
import com.trilead.ssh2.packets.TypesWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.common.Factory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.command.UnknownCommand;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.SessionFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.*;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;

public class SSHAgentBase {

    public static AtomicInteger SSH_SERVER_INITIAL_PORT = new AtomicInteger(4380);

    public static String SSH_SERVER_HOST = "localhost";

    public static String CREDENTIAL_ID = "84822271-02d5-47b8-b8ff-c40fef175c29";

    private SshServer sshd = null;

    private int assignedPort = SSH_SERVER_INITIAL_PORT.getAndIncrement();

    protected void startMockSSHServer() throws Exception {
        File hostKey = new File(System.getProperty("java.io.tmpdir") + "/key.ser");
        hostKey.delete(); // do not carry from test to test
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(getValidPort());
        sshd.setHost(SSH_SERVER_HOST);
        sshd.getProperties().put(SshServer.WELCOME_BANNER, "Welcome to the Mock SSH Server\n");
        SimpleGeneratorHostKeyProvider hostKeyProvider = new SimpleGeneratorHostKeyProvider(new File(hostKey.getPath()));
        hostKeyProvider.setAlgorithm(/* TODO when upgrading sshd: KeyUtils.RSA_ALGORITHM */"RSA"); // http://stackoverflow.com/a/33692432/12916
        sshd.setKeyPairProvider(hostKeyProvider);
        sshd.setShellFactory(new Factory<Command>() {
            @Override
            public Command create() {
                Logger.getAnonymousLogger().info("Create shell");
                return new Command() {
                    private InputStream inputStream;
                    private OutputStream outputStream;
                    private OutputStream errorStream;
                    private ExitCallback exitCallback;

                    @Override
                    public void setInputStream(InputStream inputStream) {
                        this.inputStream = inputStream;
                    }

                    @Override
                    public void setOutputStream(OutputStream outputStream) {
                        this.outputStream = outputStream;
                    }

                    @Override
                    public void setErrorStream(OutputStream outputStream) {
                        this.errorStream = outputStream;
                    }

                    @Override
                    public void setExitCallback(ExitCallback exitCallback) {
                        this.exitCallback = exitCallback;
                    }

                    @Override
                    public void start(Environment environment) throws IOException {
                        if (outputStream != null) {
                            try {
                                outputStream.write("Connection established. Closing...\n".getBytes("UTF-8"));
                                outputStream.flush();
                            } catch (IOException e) {
                                // squash
                            }
                        }
                        if (exitCallback != null) {
                            exitCallback.onExit(0);
                        }
                    }

                    @Override
                    public void destroy() {

                    }
                };
            }
        });
        sshd.setCommandFactory(new CommandFactory() {
            @Override
            public Command createCommand(String s) {
                return new UnknownCommand(s);
            }
        });

        sshd.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            @Override
            public boolean authenticate(String username, PublicKey publicKey, ServerSession session) {
                return isAuthorizedKey(getPublicKeySignature(publicKey));
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
        sshd.setSessionFactory(new SessionFactory());

        sshd.start();
        System.out.println("Mock SSH Server is started using the port " + getAssignedPort());
    }

    protected void stopMockSSHServer() throws InterruptedException, IOException {
        if (sshd != null) {
            sshd.stop();
            System.out.println("Mock SSH Server is shutdown");
        }
    }

    /**
     * Returns a string with the Private Key with Passphrase.
     *
     * @return String with the Private Key
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
     * Same key as getPrivateKey(), but encrypted with a different passphrase
     *
     * @return
     */
    public String getPrivateKey2() {
        return    "-----BEGIN RSA PRIVATE KEY-----\n"
                + "Proc-Type: 4,ENCRYPTED\n"
                + "DEK-Info: AES-128-CBC,4F1CC1FC8ABFF63F16459D0FD743235A\n"
                + "\n"
                + "5eKeY3ayrWVVrfx2aDpxB487t5NmykzZaLwlZ2YD97DA7mE4S87ywwBF3Dg8pG9e\n"
                + "VCa03SfcjOITuXh6+Eh3dlAC5xC3Cg/gYfRKPUYrkNDkGOuAjuf/54iE+OVMS3Cm\n"
                + "gc2ZSWHKS1VRsh/StcVGemGsGknx5Ij7vNjHTLLi7RlK290PNTFRjbzpGUziTYqE\n"
                + "tYUfMtCUeAupZBxZTr+BLkA9wSLmDsA0K6J5kkGAvTZ4xM71u48QGjgOlOIiVkQK\n"
                + "r+kdtyvN2M38mkH+abUWYBs/Cfk0ZjpCez83XmEM8bASEbH6BAg9JZ1JdJp/dSiW\n"
                + "z267NfjGLKdUXreopgJhEj7OFIjxWsXcr1MVx9NrrdRYa+JPvooUtUu4AI4zG72E\n"
                + "MOCEydUwS8rMVsm3f2nDowlCq8ZyVRPoTGtkJgIzolJYcnd9XpnhqQvK+9PLva4k\n"
                + "3MIqIhyeyOoXl77ESZbl2VGwkqx9tR5HnSPr5mDXNJsSFLaxYkvfz8Zv97t4zAtb\n"
                + "lLtxnlKFwXwYiSudlK0d249BAIj+pBQBz+gq7Teks1Er8Xr4TaCUG9DRer1j9wYM\n"
                + "EhchnG/Vtvq6Lk8JN4GiAGpQNhk7Zxy71gljzidN79bMIPKpaF5xqJOLstUrxKGw\n"
                + "yIrD6Pn2Mf7QbM5EK7bnWsWITN0LDzGej1FtnXSBQvJz/QEDK2v/MAgJd9bT3Unm\n"
                + "cS0VbY0mi+UeIPcpvS5gc0k/6wXzr2IHKBDjre2Nh7fRZiII8dB60gg9GtzMeo9b\n"
                + "JuOUGCMoXl0h6AG6mrF5tWRy58vs19JJYuwSS/tVbxVEiwWGaQ/oeaTDBnQ/HOhk\n"
                + "ZssU5Ks0hQcRnnbV+3sikCx6k33Jf1vyfmzEKSyQdKL9GfWCmS/fBz7liPHaLtJu\n"
                + "PNToqL2A0a6eMsk7ytttVy/HpfSCMDcbyYcBEL+lEhb3T6uL5+yyRRaXk+ZkXxUJ\n"
                + "p/0vUBhNnwIlU4MYZsoU4R27ss7SDl3orra84GpfY1x+DdVUeDBnlH3fbvQzOT9w\n"
                + "3RLxkTl6H3DbfiAwWeSJF3UZK88c2J69rl4lrwfV/g4UMuK50GDby3HNV8zuLFeI\n"
                + "1pRyqfEqVRRAde9N1+uMa+fqZDujw3eH/hhh7nPa2NYawQN4klutLzpAj/lv6uU/\n"
                + "ubeISASZbv4bBGHROvprsglx/GuJ93zIdcwbdtyBysqbZsjoZhL2mTV0kmvlxzL0\n"
                + "oplngeaVcRzhnDEbFXNo6e9EthCCxUPe46xALsH+JBI7scgq+hTHtHTMrk3I/CU7\n"
                + "RqjaqWG/0FhWMKpdIVghIHHHTL3ndsoFNiB3qXRd/3OOmXJP302MDX4Zj4SxBGB3\n"
                + "YZeOj14yMZ51TJCR+NfqK7a7YSZHPU8ynOUtOf9XKfXwD2oyD42Zp0E6kY4INJ0k\n"
                + "buxhmHH1f4cva1zEXwOTzziKMpk1TYW1oi+7YcbeLDRs2I3Fvz2KCMmUnxIpxT8Q\n"
                + "ol1IyPxfKl6VD51gVKERNdEIyHLarHn+DiSHBto3JNj395H9vm68hdoFQDbalfPr\n"
                + "X3iUnblOV+BwPz8IGRN90evksIA3r/PUFAFuwfDAmrPe7qUid3ur+nrVVxojLbgX\n"
                + "JHN9BtDRQghkDt2igqgzuuwvShpyS1Yya5byA1Pbegrl4Yn1hERLie0kAhihZemB\n"
                + "NRQDw2T4nuI0uRHL1Pold8phlK/xINh5aOhD40qOykz5LFYk2m3Eh+tdYEdexhYj\n"
                + "x2HxCRL9jsCGdmifNqCT4WVLnj0YXuszeGIAKZE6wCyugQQ+2yFg1eVDE7HdS+ht\n"
                + "U8zpyVNRD/r6rL/9D7ljHca9+c4QMtYK9sd/qh040CNkUxuPjUEVtPVUmap6gK/u\n"
                + "zW7eUofNygSZrZ96NlKF8z2WGDA60RN8VimQZf6TiY87XhceHM1rS8jbtIkhzooH\n"
                + "cDsX2HJCtBZMlvZZxNxmPMEVhmHYVvtCM/4MG5Ud8mQ41I1icreiAldpXAXD4Nua\n"
                + "WhrgnHt4dsvkzVC8pVW5JMjNVGw55WmJutwnQPisqMCJLMpxu18Bi/NG1I3Syj9f\n"
                + "Jyqp0X5i4CI1IS19a8+zRk093jbdC83N6fnFI58vwOeHlEQEgWptJe9LJ4YfhfQ9\n"
                + "ha/XfGwshCs2/aTv0vIU/2pbkCA3kC9QLAc/r8QlgKV3/yqVlQ70BqxSbw0SsVVg\n"
                + "gDNLx+BFRVq+bnohANvdfgjqF7OXtKPaYxj9ggQoC2vlb8uvCzB6eTi4eP68Flvx\n"
                + "NC7zg1f8y1/Me6tFRCKzBEZg29NiEnmQaO4daCtVL92HhcFjdG43PSwKRL48Uc73\n"
                + "9yZIm4hcImp71sS5QO7jsPxiTXNa6rWaAsd12Tii7/kNIT5NOACWVr4WxUAdTVMH\n"
                + "leQueMk/8iGU6aM5Zr4SGezS934hOig3w8zlHOLSE+SadiexpXjPJe1RBb/9D8qG\n"
                + "7nynZVMbONbXRYWHuCPal/DdrkN6YynD5yBqrFvxZ7svJJFIH+Mw6o5DI3fYPER/\n"
                + "QB8TuBvdE5dSyuAPd3bHAugQyZMSn6usENWsHdquVvBGJZRnmVJxYBWPD5/XD5d3\n"
                + "UXi6ctjwBGobJOMgg4XzO6AxH9hKg8yacAv67N921zydnEjYMvosErtBXq6E6HXI\n"
                + "5ytVdR6q5vgG0vjXflpBsxRZm2I9uNOQuR6sjC0HDMmktQOESGWy+IwH5Z0vrs98\n"
                + "Rk6StDVqHlwbW85sCOBzYeZs9w5C1e9fT76kMTsm/E28y5OduLXRTW/Eqnr0DeUf\n"
                + "KgPw/bgJemOKNC0W6jZsqAFpArQ0PGuWikhsdoSIhRHXHjQnSqKv5qKzYiFZvPyV\n"
                + "8P+u3DvYl5pRY9W2qyuFcrspWlgaunKV9VK5VJGyTmt0ezI+dSNvOmCSmOxv5nly\n"
                + "+00Ilh9+VfudUq+WHAsUEC5VSL2NqLjHryBF/BZwCa+3Kdikh9qbEMC159Hw7rZs\n"
                + "qrgr6SWapKNogPqDbeTRA6w9bptKzSUxm371pr6RefDcuPaab9jamqQPCg7rqnjs\n"
                + "+2nKK8wY67JkilnYmWzqTykKUVGoKMqfSIv5COOTgWCGNtipIxhoMIqHpCuUp6uJ\n"
                + "VZYF6iimsK8r3AqMCwXT7SyGPkbU7LaLpE39sdKiqQr7AV6xidR2fii/+oh+EECl\n"
                + "-----END RSA PRIVATE KEY-----\n";
    }

    /**
     * Returns a string with the authorized Public Key.
     *
     * @return String with the authorized Public Key
     */
    public String getAuthorizedPublicKey() {
        return  "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQDB+TU0nnxVKh+m4zV2DhPm8SM5dBWQW2maU2VQp/sKHdgMuGep422eKbNfm9u82kyh1gImJzQVFQaWX+h+SewxiT9Xm7yD4D2RYXuIXgxp5x5WBpQBIHcWgV9v/a+O0cIUnDJYCh5j3O2RT4CpqnrseRrcVoMFSI+sdSAseYC2CKFAIua1x4cUykEH0kE/vkt4WPDJCb7+mIhNpjJEhHW7etsSCcA+vKxux3Kw0TuMNb/o/jL631R7NrU5jo3LzjjgD2FX6wolkYEp9F7YWaXZY4BvopObAGe52aj20Oay7L6uxiFUq/NTOMrT5trJBY3LNOSJuFr+UWGuUSulwj6qR++Io5pTyHjJLaw+s+dXdOArFAeum5bbxhGcLa18eSFYM761wA4KLdVbwd1nXoIG3+wTSO1EQCbArqc7UIhQYKI/WYDpNROdKOTyIpIYXjHz4SZBYXOn9zXJGvgnPpuHoefkT2RB5ryrfr1GFmoV2Bd/i32KIdtiDVqzCZHp9y4ZLlxz3+beMA19dNGbdYgUuanzQuAqeDNK2AcAd0IcnSrmijrxs3oNPbKr1wX2cYdD01m8jhNEn1+JCRAYghI9VsUVeEfuydA942M9gAjIiMGp7L7j09+YaJ0KH3BH8ZVJl20ojjIEa5GkOLo4IK/DMflkgG/qupG2u94o77LaIQ== cloudbees@localhost";
    }

    /**
     * Returns a valid port number to use in Mocked SSH Server. Verifies that the port is not being used.
     *
     * @return int with a valid port number
     */
    private int getValidPort() throws IOException {
        boolean validPort = false;
        while (!validPort) {
            ServerSocket socket = null;
            try {
                socket = new ServerSocket(assignedPort, 0, InetAddress.getByName(SSH_SERVER_HOST));
            } catch (BindException be) {
                assignedPort = SSH_SERVER_INITIAL_PORT.getAndIncrement();
            } finally {
                if (socket != null) {
                    socket.close();
                    validPort = true;
                }
            }
        }
        return assignedPort;
    }

    /**
     * Returns the assigned port number to be used in Mock.
     *
     * @return int with an assigned port number
     */
    public int getAssignedPort() {
        return assignedPort;
    }

}
