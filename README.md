# SSH Agent Plugin

This plugin allows you to provide SSH credentials to builds via a
ssh-agent in Jenkins.

This is convenient in some cases.
See alternatives below.

# Requirements

You need to have the `ssh-agent` executable installed on the agent.

# Configuring

First you need to add some SSH Credentials to your instance:

Jenkins \| Manage Jenkins \| Manage Credentials

![](docs/images/Screen_Shot_2012-10-26_at_12.25.04.png)

Note that only Private Key based credentials can be used.

## Pipeline

From a Pipeline job, use the `sshagent` step.

```groovy
steps {
    sshagent(credentials: ['ssh-credentials-id']) {
      sh '''
          [ -d ~/.ssh ] || mkdir ~/.ssh && chmod 0700 ~/.ssh
          ssh-keyscan -t rsa,dsa example.com >> ~/.ssh/known_hosts
          ssh user@example.com ...
      '''
    }
}
```

### Alternatives not using this plugin

Alternately, you can use the generic `withCredentials` step to bind an SSH private key to a temporary file
and then pass that to commands that require it,
for example using the `-i` option to `ssh` or `scp`:

```groovy
withCredentials([sshUserPrivateKey(credentialsId: 'github', keyFileVariable: 'PK')]) {
    sh 'git -c core.sshCommand="ssh -i $PK" submodule update --init'
}
```

(Compare [`gitUsernamePassword` binding](https://plugins.jenkins.io/git/#plugin-content-git-credentials-binding).)

Or you can even use `ssh-agent`, but without this plugin, useful especially if you need to customize options in any way:

```groovy
withCredentials([sshUserPrivateKey(credentialsId: 'github', keyFileVariable: 'PK')]) {
    sh '''
        eval `ssh-agent -s`
        trap "ssh-agent -k" EXIT
        ssh-add "$PK"
        # rest of script…
    '''
}
```

## Freestyle

Then configure your build to use the credentials:

![](docs/images/Screen_Shot_2012-10-26_at_12.26.13.png)

And then your build will have those credentials available, e.g.

![](docs/images/Screen_Shot_2012-10-26_at_11.54.21.png)

# Version History

For new versions, see [GitHub releases](https://github.com/jenkinsci/ssh-agent-plugin/releases).

For old versions, see the [old changelog](https://github.com/jenkinsci/ssh-agent-plugin/blob/8933585c69d31c49648224dcbda04933941d2e26/docs/old-changelog.md).
