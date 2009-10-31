//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Jan 17, 2008
 */
package org.globus.cog.abstraction.impl.execution.coaster;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.coaster.service.CoasterService;
import org.globus.cog.abstraction.coaster.service.ServiceShutdownCommand;
import org.globus.cog.abstraction.coaster.service.local.LocalService;
import org.globus.cog.abstraction.impl.common.AbstractionFactory;
import org.globus.cog.abstraction.impl.common.ProviderMethodException;
import org.globus.cog.abstraction.impl.common.StatusEvent;
import org.globus.cog.abstraction.impl.common.task.ExecutionServiceImpl;
import org.globus.cog.abstraction.impl.common.task.IllegalSpecException;
import org.globus.cog.abstraction.impl.common.task.InvalidProviderException;
import org.globus.cog.abstraction.impl.common.task.InvalidServiceContactException;
import org.globus.cog.abstraction.impl.common.task.JobSpecificationImpl;
import org.globus.cog.abstraction.impl.common.task.TaskImpl;
import org.globus.cog.abstraction.impl.common.task.TaskSubmissionException;
import org.globus.cog.abstraction.impl.execution.coaster.bootstrap.Bootstrap;
import org.globus.cog.abstraction.impl.execution.coaster.bootstrap.Digester;
import org.globus.cog.abstraction.interfaces.Delegation;
import org.globus.cog.abstraction.interfaces.ExecutionService;
import org.globus.cog.abstraction.interfaces.FileLocation;
import org.globus.cog.abstraction.interfaces.JobSpecification;
import org.globus.cog.abstraction.interfaces.SecurityContext;
import org.globus.cog.abstraction.interfaces.ServiceContact;
import org.globus.cog.abstraction.interfaces.Status;
import org.globus.cog.abstraction.interfaces.StatusListener;
import org.globus.cog.abstraction.interfaces.Task;
import org.globus.cog.abstraction.interfaces.TaskHandler;
import org.globus.cog.karajan.workflow.service.channels.ChannelManager;
import org.globus.cog.karajan.workflow.service.channels.IrrecoverableException;
import org.globus.cog.karajan.workflow.service.channels.KarajanChannel;
import org.globus.cog.karajan.workflow.service.commands.Command;
import org.globus.cog.karajan.workflow.service.commands.Command.Callback;
import org.ietf.jgss.GSSCredential;

public class ServiceManager implements StatusListener {
    public static final Logger logger = Logger.getLogger(ServiceManager.class);

    public static final String BOOTSTRAP_SCRIPT = "bootstrap.sh";
    public static final String BOOTSTRAP_JAR = "coaster-bootstrap.jar";

    public static final String TASK_ATTR_ID = "coaster:serviceid";

    private static ServiceManager defaultManager;

    public static synchronized ServiceManager getDefault() {
        if (defaultManager == null) {
            defaultManager = new ServiceManager();
        }
        return defaultManager;
    }

    public static final boolean LOCALJVM = false;
    
    public static final boolean LOCALJVM_WHEN_LOCAL = true;

    private BootstrapService bootstrapService;
    private LocalService localService;
    private Map services;
    private Map credentials;
    private Map bootHandlers;
    private Set starting;
    private Map usageCount;
    private ServiceReaper serviceReaper;

    public ServiceManager() {
        services = new HashMap();
        credentials = new HashMap();
        starting = new HashSet();
        usageCount = new HashMap();
        bootHandlers = new HashMap();
        serviceReaper = new ServiceReaper();
        Runtime.getRuntime().addShutdownHook(serviceReaper);
    }

    private TaskHandler getBootHandler(String provider) throws InvalidServiceContactException,
            InvalidProviderException, ProviderMethodException, IllegalSpecException {
        synchronized (bootHandlers) {
            TaskHandler th = (TaskHandler) bootHandlers.get(provider);
            if (th == null) {
                th = AbstractionFactory.newExecutionTaskHandler(provider);
                bootHandlers.put(provider, th);
            }
            return th;
        }
    }

    public String reserveService(ServiceContact contact, SecurityContext sc,
            String bootHandlerProvider) throws TaskSubmissionException {
        if (logger.isDebugEnabled()) {
            logger.debug("Reserving service " + contact);
        }
        try {
            // beah. it's impossible to nicely abstract both concurrency
            // and normal program semantics
            String url = waitForStart(contact);
            if (url == null) {
                url =
                        startService(contact, sc, getBootHandler(bootHandlerProvider),
                            bootHandlerProvider);
            }
            increaseUsageCount(contact);
            return url;
        }
        catch (Exception e) {
            throw new TaskSubmissionException("Could not start coaster service", e);
        }
    }

    public String reserveService(Task task, String bootHandlerProvider)
            throws TaskSubmissionException {
        return reserveService(getContact(task), getSecurityContext(task), bootHandlerProvider);
    }

    protected String waitForStart(Object service) throws InterruptedException {
        synchronized (services) {
            while (starting.contains(service)) {
                services.wait(100);
            }
            String url = (String) services.get(service);
            if (url == null) {
                starting.add(service);
            }
            return url;
        }
    }

    public void serviceIsActive(String id) {
        localService.heardOf(id);
    }

    private static final String[] STRING_ARRAY = new String[0];

    protected String startService(ServiceContact contact, SecurityContext sc,
            TaskHandler bootHandler, String bootHandlerProvider) throws Exception {
        try {
            startLocalService();
            final Task t = buildTask(contact);
            setSecurityContext(t, sc, bootHandlerProvider);
            t.addStatusListener(this);
            if (logger.isDebugEnabled()) {
                logger.debug("Starting coaster service on " + contact + ". Task is " + t);
            }
            boolean local = "local".equals(bootHandlerProvider);
            if (LOCALJVM || (LOCALJVM_WHEN_LOCAL && local)) {
                final String ls = getLocalServiceURL();
                final String id = "l" + getRandomID();
                t.setAttribute(TASK_ATTR_ID, id);
                new Thread(new Runnable() {
                    public void run() {
                        CoasterService.main(new String[] { ls, id, "-local" });
                    }
                }).start();
            }
            else {
                bootHandler.submit(t);
            }
            String url = localService.waitForRegistration(t, (String) t.getAttribute(TASK_ATTR_ID));
            synchronized (services) {
                services.put(contact, url);
                credentials.put(url, sc.getCredentials());
            }
            return url;
        }
        finally {
            synchronized (services) {
                starting.remove(contact);
                services.notifyAll();
            }
        }
    }

    private void setSecurityContext(Task t, SecurityContext sc, String provider)
            throws InvalidProviderException, ProviderMethodException {
        t.getService(0).setSecurityContext(AbstractionFactory.newSecurityContext(provider));
    }

    public void statusChanged(StatusEvent event) {
        Task t = (Task) event.getSource();
        Status s = event.getStatus();
        if (s.isTerminal()) {
            if (logger.isInfoEnabled()) {
                logger.info("Service task " + t + " terminated. Removing service.");
            }
            String url;
            ServiceContact contact = getContact(t);
            synchronized (services) {
                url = (String) services.remove(contact);
                if (url == null) {
                    logger.info("Service does not appear to be registered with this manager");
                }
                else {
                    credentials.remove(url);
                }
            }
            String msg =
                    "Coaster service ended. Reason: " + s.getMessage() + "\n\tstdout: "
                            + t.getStdOutput() + "\n\tstderr: " + t.getStdError();
            NotificationManager.getDefault().serviceTaskEnded(contact, msg);
            try {
                if (url != null) {
                    GSSCredential cred =
                            (GSSCredential) t.getService(0).getSecurityContext().getCredentials();
                    KarajanChannel channel =
                            ChannelManager.getManager().getExistingChannel(url, cred);
                    if (channel != null) {
                        channel.getChannelContext().notifyRegisteredListeners(
                            new IrrecoverableException(msg));
                        channel.close();
                    }
                }
            }
            catch (Exception e) {
                logger.info("Failed to close channel", e);
            }
        }
    }
    private static final Integer ZERO = new Integer(0);

    protected void increaseUsageCount(Object service) {
        synchronized (usageCount) {
            Integer i = (Integer) usageCount.get(service);
            if (i == null) {
                i = ZERO;
            }
            usageCount.put(service, new Integer(i.intValue() + 1));
        }
    }

    protected ServiceContact getContact(Task task) {
        return task.getService(0).getServiceContact();
    }

    protected SecurityContext getSecurityContext(Task task) {
        return task.getService(0).getSecurityContext();
    }

    private Task buildTask(ServiceContact sc) throws TaskSubmissionException {
        try {
            Task t = new TaskImpl();
            t.setType(Task.JOB_SUBMISSION);
            JobSpecification js = new JobSpecificationImpl();
            js.setExecutable("/bin/bash");
            js.addArgument("-c");
            String id = getRandomID();
            t.setAttribute(TASK_ATTR_ID, id);
            js.addArgument(loadBootstrapScript(new String[] { getBootstrapServiceURL(),
                    getLocalServiceURL(), getMD5(BOOTSTRAP_JAR), getMD5(Bootstrap.BOOTSTRAP_LIST),
                    id, sc.getHost() }));
            js.setDelegation(Delegation.FULL_DELEGATION);
            js.setStdOutputLocation(FileLocation.MEMORY);
            js.setStdErrorLocation(FileLocation.MEMORY);
            t.setSpecification(js);
            ExecutionService s = new ExecutionServiceImpl();
            s.setServiceContact(sc);
            s.setJobManager("fork");
            t.setService(0, s);
            return t;
        }
        catch (Exception e) {
            throw new TaskSubmissionException(e);
        }
    }

    private synchronized String loadBootstrapScript(String[] args) throws TaskSubmissionException {
        URL u = ServiceManager.class.getClassLoader().getResource(BOOTSTRAP_SCRIPT);
        if (u == null) {
            throw new TaskSubmissionException("Could not find bootstrap script in classpath");
        }
        try {
            StringBuffer sb = new StringBuffer();
            sb.append("echo -e \"");
            BufferedReader br = new BufferedReader(new InputStreamReader(u.openStream()));
            String line = br.readLine();
            while (line != null) {
                escape(sb, line, args);
                sb.append("\\x0a");
                line = br.readLine();
            }
            sb.append("\"|/bin/bash");
            return sb.toString();
        }
        catch (IOException e) {
            throw new TaskSubmissionException("Could not load bootstrap script", e);
        }
    }

    private void escape(StringBuffer sb, String line, String[] args) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            switch (c) {
                case '\'':
                    sb.append("\\x27");
                    break;
                case '`':
                    sb.append("\\x60");
                    break;
                case '"':
                    sb.append("\\x22");
                    break;
                case '<':
                    sb.append("\\x3c");
                    break;
                case '>':
                    sb.append("\\x3e");
                    break;
                case '$':
                    if (i + 1 < line.length()) {
                        int n = line.charAt(i + 1) - '0';
                        if (n >= 1 && n <= 9 && n <= args.length && args[n - 1] != null) {
                            i++;
                            sb.append('"');
                            sb.append(args[n - 1]);
                            sb.append('"');
                            // a hack to prevent blind substitution (e.g. inside
                            // a function).
                            args[n - 1] = null;
                            continue;
                        }
                    }
                    sb.append("\\x24");
                    break;
                default:
                    sb.append(c);
            }
        }
    }

    private String getBootstrapServiceURL() throws IOException {
        return startBootstrapService();
    }

    private String getMD5(String name) throws NoSuchAlgorithmException, IOException {
        File f = bootstrapService.getFile(name);
        return Digester.computeMD5(f);
    }

    private synchronized String startBootstrapService() throws IOException {
        if (bootstrapService == null) {
            bootstrapService = new BootstrapService();
            bootstrapService.start();
        }
        return bootstrapService.getURL();
    }

    private synchronized void startLocalService() throws IOException {
        if (localService == null) {
            localService = new LocalService();
            localService.start();
        }
    }

    private synchronized String getLocalServiceURL() throws IOException {
        startLocalService();
        return localService.getURL();
    }
    
    public LocalService getLocalService() throws IOException {
    	startLocalService();
    	return localService;
    }

    private String getRandomID() {
        int r;
        try {
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            r = sr.nextInt();
        }
        catch (NoSuchAlgorithmException e) {
            r = (int) (Math.random() * Integer.MAX_VALUE);
        }
        if (r < 0) {
            return '0' + String.valueOf(-r);
        }
        else {
            return '1' + String.valueOf(r);
        }
    }

    private class ServiceReaper extends Thread implements Callback {
        private int count;

        public ServiceReaper() {
            setName("Coaster service reaper");
        }

        public void run() {
            System.out.println("Cleaning up...");
            Iterator i = services.values().iterator();
            count = services.size();
            while (i.hasNext()) {
                String url = (String) i.next();
                Object cred = credentials.get(url);
                try {
                    System.out.println("Shutting down service at " + url);
                    KarajanChannel channel =
                            ChannelManager.getManager().reserveChannel(url, (GSSCredential) cred);
                    System.out.println("Got channel " + channel);
                    ServiceShutdownCommand ssc = new ServiceShutdownCommand();
                    ssc.setReplyTimeout(10000);
                    ssc.setMaxRetries(0);
                    ssc.executeAsync(channel, this);
                    ChannelManager.getManager().releaseChannel(channel);
                }
                catch (Exception e) {
                    logger.warn("Failed to shut down service " + url, e);
                }
            }
            synchronized (this) {
                while (count > 0) {
                    try {
                        wait(100);
                    }
                    catch (InterruptedException e) {
                        return;
                    }
                }
            }
            System.out.println(" Done");
        }

        public synchronized void errorReceived(Command cmd, String msg, Exception t) {
            System.out.print("-");
            count--;
            notifyAll();
        }

        public synchronized void replyReceived(Command cmd) {
            System.out.print("+");
            count--;
            notifyAll();
        }
    }
}
