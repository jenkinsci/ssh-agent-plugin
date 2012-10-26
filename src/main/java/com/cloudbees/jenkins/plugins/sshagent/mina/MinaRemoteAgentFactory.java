package com.cloudbees.jenkins.plugins.sshagent.mina;

import com.cloudbees.jenkins.plugins.sshagent.RemoteAgent;
import com.cloudbees.jenkins.plugins.sshagent.RemoteAgentFactory;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import org.apache.tomcat.jni.Library;

/**
 * @author stephenc
 * @since 26/10/2012 14:35
 */
@Extension
public class MinaRemoteAgentFactory extends RemoteAgentFactory {

    @Override
    public String getDisplayName() {
        return "Java/JNI ssh-agent";
    }

    @Override
    public boolean isSupported(Launcher launcher, final TaskListener listener) {
        try {
            return launcher.getChannel().call(new TomcatNativeInstalled(listener));
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Override
    public RemoteAgent start(Launcher launcher, final TaskListener listener) throws Throwable {
        return launcher.getChannel().call(new MinaRemoteAgentStarter(listener));
    }

    private static class TomcatNativeInstalled implements Callable<Boolean, Throwable> {
        private final TaskListener listener;

        public TomcatNativeInstalled(TaskListener listener) {
            this.listener = listener;
        }

        public Boolean call() throws Throwable {
            try {
                Library.initialize(null);
                return true;
            } catch (Exception e) {
                listener.getLogger().println("[ssh-agent] Could not find Tomcat Native library");
                return false;
            }
        }
    }
}
