For new versions, see [GitHub releases](https://github.com/jenkinsci/ssh-agent-plugin/releases).

### Version 1.17 (2018-10-02)

-   Did not properly interact with `withDockerContainer` when run on a
    machine with `DISPLAY=:0` set.

### Version 1.16 (2018-07-30)

-   [Fix security
    issue](https://jenkins.io/security/advisory/2018-07-30/#SECURITY-704)

### Version 1.15 (2017-04-06)

-   [issue@42093](#) Fixed quoting for askpass in
    command-line implementation. 

### Version 1.14 (2017-02-10)

-   [JENKINS-36997](https://issues.jenkins-ci.org/browse/JENKINS-36997)
    New default implementation that uses command-line `ssh-agent`.
    Should fix various problems with crypto APIs,
    `docker.image(…).inside {sshagent(…) {…}}`, etc.
-   [JENKINS-38830](https://issues.jenkins-ci.org/browse/JENKINS-38830)
    Track credentials used in the wrapper.
-   [JENKINS-35563](https://issues.jenkins-ci.org/browse/JENKINS-35563)
    Fixes to credentials dropdown.

### Version 1.13 (2016-03-03)

-   [JENKINS-32120](https://issues.jenkins-ci.org/browse/JENKINS-32120)
    Register Bouncy Castle on the remote agent by using Bouncy Castle
    API plugin

Apparently does not work in some versions of Jenkins; see
[JENKINS-36935](https://issues.jenkins-ci.org/browse/JENKINS-36935).

### Version 1.12 (2016-03-03)

-   **Wrong release**. Release process broken due a network issue.

### Version 1.11 (2016-03-03)

-   [JENKINS-35463](https://issues.jenkins-ci.org/browse/JENKINS-35463)
    First release using
    [bouncycastle-api-plugin](https://wiki.jenkins-ci.org/display/JENKINS/Bouncy+Castle+API+Plugin)

### Version 1.10 (2016-03-03)

-   [JENKINS-27152](https://issues.jenkins-ci.org/browse/JENKINS-27152) / [JENKINS-32624](https://issues.jenkins-ci.org/browse/JENKINS-32624)
    Use a standardized temporary directory compatible with Docker
    Pipeline.

### Version 1.9 (2015-12-07)

Changelog unrecorded.

### Version 1.8 (2015-08-07)

-   Compatible with
    [Workflow](https://wiki.jenkins-ci.org/display/JENKINS/Workflow+Plugin) (issue
    [\#28689](https://issues.jenkins-ci.org/browse/JENKINS-28689))

### Version 1.7 (2015-06-02)

-   Fixed a socket and thread leak ([issue
    \#27555](https://issues.jenkins-ci.org/browse/JENKINS-27555))

### Version 1.6 (2015-04-20)

-   SSH agent socket service thread shouldn't keep JVM alive.

### Version 1.5 (2014-08-11)

-   Add support for multiple credentials
-   Add support for parameterized credentials

### Version 1.4.2 (2014-08-11)

-   Fix for
    [JENKINS-20276](https://issues.jenkins-ci.org/browse/JENKINS-20276)
-   **WARNING: Due to classpath conflicts, this plugin will not work if
    1.518 \<= Jenkins Version \< 1.533 (i.e. 1.518 broke it, 1.533 fixed
    it)**

### Version 1.4.1 (2013-11-08)

-   Switch from f:select to c:select so that in-place addition of
    credentials is supported when the credentials plugin exposes such
    support
-   **WARNING: Due to classpath conflicts, this plugin will not work if
    1.518 \<= Jenkins Version \< 1.533 (i.e. 1.518 broke it, 1.533 fixed
    it)**

### Version 1.4 (2013-10-08)

-   Minor improvement in exception handling
-   Minor improvement in fault reporting
-   Update JNR libraries
-   **WARNING: Due to classpath conflicts, this plugin will not work if
    1.518 \<= Jenkins Version \< 1.533 (i.e. 1.518 broke it, 1.533 fixed
    it)**

### Version 1.3 (2013-08-09)

-   Set-up SSH Agent before SCM checkout, this way [GIT can use the ssh
    agent](https://issues.jenkins-ci.org/browse/JENKINS-12492).
    (Contributed by Patric Boos)
-   Upgrade to [SSH Credentials
    1.3](https://wiki.jenkins.io/display/JENKINS/SSH+Credentials+Plugin)

### Version 1.2 (2013-08-07)

-   Upgrade to [Credentials plugin
    1.6](https://wiki.jenkins.io/display/JENKINS/Credentials+Plugin) and
    [SSH Credentials plugin
    1.0](https://wiki.jenkins.io/display/JENKINS/SSH+Credentials+Plugin).
    This now allows serving multiple private keys from the users home
    directory, e.g. \~/.ssh/id\_rsa, \~/.ssh/id\_dsa and
    \~/.ssh/identity

### Version 1.1 (2013-07-04)

-   If BouncyCastleProvider is not registered, try to register it
    ourselves anyway... this should make installation and configuration
    even easier.

### Version 1.0 (2012-11-01)

-   Using jnr-unixsocket have been able to remove the requirement on
    Apache Tomcat Native for unix nodes. Likely still require the Apache
    Tomcat Native for Windows nodes.

### Version 0.1 (2012-10-26)

-   Initial release 
