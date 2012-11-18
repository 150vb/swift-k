//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Jan 19, 2008
 */
package org.globus.cog.abstraction.coaster.service.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.coaster.service.Registering;
import org.globus.cog.abstraction.impl.common.execution.JobException;
import org.globus.cog.abstraction.impl.common.task.TaskSubmissionException;
import org.globus.cog.abstraction.interfaces.Service;
import org.globus.cog.abstraction.interfaces.Status;
import org.globus.cog.abstraction.interfaces.Task;
import org.globus.cog.karajan.workflow.service.ConnectionHandler;
import org.globus.cog.karajan.workflow.service.GSSService;
import org.globus.cog.karajan.workflow.service.channels.ChannelContext;
import org.globus.cog.karajan.workflow.service.channels.ChannelManager;
import org.globus.cog.karajan.workflow.service.channels.KarajanChannel;
import org.globus.cog.karajan.workflow.service.channels.PipedServerChannel;
import org.globus.gsi.GlobusCredentialException;
import org.globus.gsi.gssapi.auth.SelfAuthorization;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;

public class LocalService extends GSSService implements Registering {
    public static final Logger logger = Logger.getLogger(LocalService.class);

    // TODO change back to 300
    public static final long DEFAULT_REGISTRATION_TIMEOUT = 3000 * 1000;

    private Map<String, String> services;
    private Map<String, Long> lastHeardOf;
    
    private Map<ChannelContext, ServiceTrackerPair> resourceTrackers;

    public LocalService() throws IOException, GlobusCredentialException, GSSException {
        super();
    }

    public void start() {
        if (logger.isDebugEnabled()) {
            logger.debug("Starting local service");
        }
        setAuthorization(new SelfAuthorization());
        services = new HashMap<String, String>();
        lastHeardOf = new HashMap<String, Long>();
        resourceTrackers = new HashMap<ChannelContext, ServiceTrackerPair>();
        this.accept = true;
        Thread t = new Thread(this);
        t.setName("Local service");
        t.setDaemon(true);
        t.start();
        logger.info("Started local service: " + getHost() + ":" + getPort());
    }

    protected void handleConnection(Socket sock) {
        logger.info("Got connection");
        try {
            ConnectionHandler handler =
                    new ConnectionHandler("service-" + sock.getPort(), this, sock, LocalRequestManager.INSTANCE);
            logger.info("Initialized connection handler");
            handler.start();
            logger.info("Connection handler started");
        }
        catch (Exception e) {
            logger.warn("Could not start connection handler", e);
        }
    }

    public void handleConnection(InputStream is, OutputStream os) {
        try {
            ConnectionHandler handler =
                    new ConnectionHandler("service-pipe", this, is, os, LocalRequestManager.INSTANCE);
            handler.start();
        }
        catch (Exception e) {
            logger.warn("Could not start local connection handler", e);
        }
    }
    
    public PipedServerChannel newPipedServerChannel() {
        return new PipedServerChannel(LocalRequestManager.INSTANCE, new ChannelContext("spipe", this));
    }

    public String waitForRegistration(Task t, String id) throws InterruptedException,
            TaskSubmissionException {
        return waitForRegistration(t, id, DEFAULT_REGISTRATION_TIMEOUT);
    }

    public String waitForRegistration(Task t, String id, long timeout) throws InterruptedException,
            TaskSubmissionException {
        if (logger.isDebugEnabled()) {
            logger.debug("Waiting for registration from service " + id);
        }
        heardOf(id);
        synchronized (services) {
            while (!services.containsKey(id)) {
                services.wait(250);
                if (timeout < System.currentTimeMillis() - lastHeardOf(id)) {
                    throw new TaskSubmissionException("Timed out waiting for registration for "
                            + id);
                }
                Status s = t.getStatus();
                if (s.isTerminal()) {
                    throw new TaskSubmissionException("Task ended before registration was received"
                            + (s.getMessage() == null ? ". " : ": " + s.getMessage())
                            + out("STDOUT", t.getStdOutput()) + out("STDERR", t.getStdError()),
                        s.getException() instanceof JobException ? null : s.getException());
                }
            }
            return services.get(id);
        }
    }

    private String out(String name, String value) {
        if (value != null) {
            return "\n" + value;
        }
        else {
            return "";
        }
    }

    public void heardOf(String id) {
        synchronized (lastHeardOf) {
            lastHeardOf.put(id, System.currentTimeMillis());
        }
    }

    protected long lastHeardOf(String id) {
        synchronized (lastHeardOf) {
            return lastHeardOf.get(id).longValue();
        }
    }

    public String registrationReceived(String id, String url, KarajanChannel channel, 
    		Map<String, String> options) {
        if (logger.isDebugEnabled()) {
            logger.debug("Received registration from service " + id + ": " + url);
        }
        synchronized (services) {
            if (services.containsKey(id)) {
                logger.info("Replacing channel for service with id=" + id + ".");
            }
            try {
                GSSCredential cred = channel.getUserContext().getCredential();
                ChannelManager.getManager().registerChannel(url, cred, channel);
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to register channel " + url, e);
            }
            services.put(id, url);
            services.notifyAll();
        }
        return null;
    }

    public void unregister(String id) {
        if (logger.isDebugEnabled()) {
            logger.debug("Unregistering service " + id);
        }
        synchronized(services) {
            services.remove(id);
            services.notifyAll();
        }
    }

    public static void main(String[] args) {
        try {
            LocalService ls = new LocalService();
            ls.start();
            System.out.println("Started service: " + ls);
            while (true) {
                Thread.sleep(1000);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ServiceTrackerPair getResourceTracker(ChannelContext context) {
        return resourceTrackers.get(context);
    }

    public void addResourceTracker(ChannelContext ctx, Service service, 
            CoasterResourceTracker resourceTracker) {
        resourceTrackers.put(ctx, new ServiceTrackerPair(service, resourceTracker));
    }

    public void resourceUpdated(ChannelContext ctx, String name, String value) {
        ServiceTrackerPair stp = resourceTrackers.get(ctx);
        if (stp != null) {
            stp.tracker.resourceUpdated(stp.service, name, value);
        }
    }
    
    private static class ServiceTrackerPair {
        public final Service service;
        public final CoasterResourceTracker tracker;
        
        public ServiceTrackerPair(Service service, CoasterResourceTracker tracker) {
            this.service = service;
            this.tracker = tracker;
        }
    }
}
