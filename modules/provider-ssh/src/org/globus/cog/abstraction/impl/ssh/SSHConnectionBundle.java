//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Nov 20, 2007
 */
package org.globus.cog.abstraction.impl.ssh;

import java.io.IOException;
import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.impl.common.PublicKeyAuthentication;
import org.globus.cog.abstraction.impl.common.task.InvalidSecurityContextException;
import org.globus.cog.abstraction.impl.common.task.InvalidServiceContactException;
import org.globus.cog.abstraction.impl.common.task.TaskSubmissionException;

public class SSHConnectionBundle {
    public static final Logger logger = Logger
            .getLogger(SSHConnectionBundle.class);

    public static final long MAX_IDLE_TIME = 60 * 1000;
    public static final int MAX_SESSIONS_PER_CONNECTION = 10;
    public static final int MAX_CONCURRENT_CONNECTIONS = 10;

    private List connections;
    private ConnectionID id;
    private Object credentials;
    private int connecting;

    public SSHConnectionBundle(ConnectionID id) {
        this.id = id;
        connections = new ArrayList();
        if (logger.isDebugEnabled()) {
            logger.debug("New SSH connection bundle: " + id);
        }
    }

    public ConnectionID getId() {
        return id;
    }

    public Object getCredentials() {
        return credentials;
    }

    public void setCredentials(Object credentials) {
        this.credentials = credentials;
    }

    public SSHChannel allocateChannel() throws InvalidSecurityContextException,
            InvalidServiceContactException, TaskSubmissionException {
        Connection connection = null;
        synchronized (connections) {
            Iterator i = connections.iterator();
            while (i.hasNext()) {
                Connection c = (Connection) i.next();
                if (c.sessionCount < MAX_SESSIONS_PER_CONNECTION) {
                    connection = c;
                    break;
                }
            }
            if (connection == null) {
                connection = newConnection();
                connections.add(connection);
            }
            connection.sessionCount++;
        }
        try {
            connection.ensureConnected();
        }
        catch (Exception e) {
            badSsh(connection.ssh);
            rethrow(e);
        }
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Creating new SSH session to " + id);
            }
            return new SSHChannel(this, connection.ssh, connection.ssh
                    .openSessionChannel());
        }
        catch (IOException e) {
            throw new TaskSubmissionException("Failed to create SSH session", e);
        }
    }

    private Connection newConnection() {
        if (logger.isDebugEnabled()) {
            logger.debug("Creating new SSH connection to " + id);
        }
        Ssh ssh = new Ssh();
        ssh.setHost(id.host);
        ssh.setPort(id.port);
        if (id.credentials instanceof PasswordAuthentication) {
            PasswordAuthentication auth = (PasswordAuthentication) id.credentials;
            ssh.setUsername(auth.getUserName());
            ssh.setPassword(String.valueOf(auth.getPassword()));
        }
        else if (id.credentials instanceof PublicKeyAuthentication) {
            PublicKeyAuthentication auth = (PublicKeyAuthentication) id.credentials;
            ssh.setUsername(auth.getUsername());
            ssh.setKeyfile(auth.getPrivateKeyFile().getAbsolutePath());
            if (auth.getPassPhrase() != null) {
                ssh.setPassphrase(String.valueOf(auth.getPassPhrase()));
            }
        }
        Connection c = new Connection(ssh);
        return c;
    }

    public void releaseChannel(SSHChannel s) {
        if (logger.isDebugEnabled()) {
            logger.debug("Releasing ssh channel for " + id);
        }
        try {
            s.getSession().close();
        }
        catch (IOException e) {
            badSsh(s.getSsh());
        }
        synchronized (connections) {
            Ssh ssh = s.getSsh();
            Iterator i = connections.iterator();
            while (i.hasNext()) {
                Connection c = (Connection) i.next();
                if (c.ssh == ssh) {
                    c.sessionCount--;
                    if (c.sessionCount == 0) {
                        c.idleTime = System.currentTimeMillis();
                    }
                    break;
                }
            }
        }
    }

    private void badSsh(Ssh ssh) {
        if (logger.isDebugEnabled()) {
            logger.debug("Bad connection for " + id + ". Removing.");
        }
        synchronized (connections) {
            Iterator i = connections.iterator();
            while (i.hasNext()) {
                Connection c = (Connection) i.next();
                if (c.ssh == ssh) {
                    i.remove();
                    break;
                }
            }
        }
    }

    public boolean shutdownIdleConnections() {
        long crt = System.currentTimeMillis();
        boolean anyActive = false;
        synchronized (connections) {
            Iterator i = connections.iterator();
            while (i.hasNext()) {
                Connection c = (Connection) i.next();
                if (c.sessionCount == 0 && crt - c.idleTime > MAX_IDLE_TIME) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Shutting down idle connection for " + id);
                    }
                    c.ssh.disconnect();
                }
                else {
                    anyActive = true;
                }
            }
        }
        return anyActive;
    }

    private void rethrow(Exception e) throws InvalidSecurityContextException,
            InvalidServiceContactException, TaskSubmissionException {
        if (e instanceof InvalidSecurityContextException) {
            throw (InvalidSecurityContextException) e;
        }
        if (e instanceof InvalidServiceContactException) {
            throw (InvalidServiceContactException) e;
        }
        if (e instanceof TaskSubmissionException) {
            throw (TaskSubmissionException) e;
        }
        if (e != null) {
            throw new TaskSubmissionException(e);
        }
    }

    private class Connection {
        private Ssh ssh;
        public int sessionCount;
        public long idleTime;
        private boolean fconnecting, fconnected;
        private Exception connectionException;

        public Connection(Ssh connection) {
            this.ssh = connection;
        }

        public void ensureConnected() throws InvalidSecurityContextException,
                InvalidServiceContactException, TaskSubmissionException {
            try {
                synchronized (this) {
                    while (fconnecting) {
                        wait();
                    }
                    if (fconnected) {
                        rethrow(connectionException);
                        return;
                    }
                    else {
                        fconnecting = true;
                    }
                }
                synchronized (id) {
                    connecting++;
                    while (connecting > MAX_CONCURRENT_CONNECTIONS) {
                        id.wait();
                    }
                }
                try {
                    ssh.connect();
                }
                catch (Exception e) {
                    synchronized (id) {
                        connectionException = e;
                    }
                }
                synchronized (id) {
                    connecting--;
                    id.notify();
                }
                synchronized (this) {
                    fconnecting = false;
                    fconnected = true;
                    notifyAll();
                }
                rethrow(connectionException);
            }
            catch (InterruptedException e) {
                throw new TaskSubmissionException(
                        "Thread got interrupted while connecting", e);
            }
        }
    }
}
