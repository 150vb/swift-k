//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Jun 20, 2005
 */
package org.globus.cog.karajan.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.impl.common.AbstractionFactory;
import org.globus.cog.abstraction.impl.common.ProviderMethodException;
import org.globus.cog.abstraction.impl.common.StatusEvent;
import org.globus.cog.abstraction.impl.common.StatusImpl;
import org.globus.cog.abstraction.impl.common.task.ActiveTaskException;
import org.globus.cog.abstraction.impl.common.task.CachingFileOperationTaskHandler;
import org.globus.cog.abstraction.impl.common.task.CachingFileTransferTaskHandler;
import org.globus.cog.abstraction.impl.common.task.InvalidProviderException;
import org.globus.cog.abstraction.impl.common.task.TaskSubmissionException;
import org.globus.cog.abstraction.interfaces.JobSpecification;
import org.globus.cog.abstraction.interfaces.Service;
import org.globus.cog.abstraction.interfaces.Status;
import org.globus.cog.abstraction.interfaces.StatusListener;
import org.globus.cog.abstraction.interfaces.Task;
import org.globus.cog.abstraction.interfaces.TaskHandler;
import org.globus.cog.karajan.scheduler.submitQueue.GlobalSubmitQueue;
import org.globus.cog.karajan.scheduler.submitQueue.HostSubmitQueue;
import org.globus.cog.karajan.scheduler.submitQueue.InstanceSubmitQueue;
import org.globus.cog.karajan.scheduler.submitQueue.NonBlockingCancel;
import org.globus.cog.karajan.scheduler.submitQueue.NonBlockingSubmit;
import org.globus.cog.karajan.scheduler.submitQueue.SubmitQueue;
import org.globus.cog.karajan.util.BoundContact;
import org.globus.cog.karajan.util.Contact;
import org.globus.cog.karajan.util.Queue;
import org.globus.cog.karajan.util.TaskHandlerWrapper;
import org.globus.cog.karajan.util.TypeUtil;
import org.globus.cog.karajan.util.VirtualContact;

public abstract class LateBindingScheduler extends AbstractScheduler implements StatusListener {
	public static final String JOBS_PER_CPU = "jobsPerCPU";
	public static final String HOST_SUBMIT_THROTTLE = "hostSubmitThrottle";
	public static final String SUBMIT_THROTTLE = "submitThrottle";
	public static final String MAX_TRANSFERS = "maxTransfers";
	public static final String SSH_INITIAL_RATE = "sshInitialRate";
	public static final String MAX_FILE_OPERATIONS = "maxFileOperations";

	public static final int K = 1024;
	public static final int THREAD_STACK_SIZE = 192 * K;
	public static final int DEFAULT_SSH_INITIAL_RATE = 6;
	public static final int DEFAULT_JOBS_PER_CPU = 128;
	public static final int DEFAULT_MAX_TRANSFERS = 32;
	public static final int DEFAULT_MAX_FILE_OPERATIONS = 64;

	private static final Logger logger = Logger.getLogger(LateBindingScheduler.class);

	private Map<Contact, BoundContact> virtualContacts;
	private boolean done, started;
	private int running;

	protected final Map executionHandlers;

	private TaskHandler transferHandler;

	private TaskHandler fileOperationHandler;

	private final Map handlers, taskContacts;

	private int jobsPerCPU, maxTransfers, currentTransfers, sshInitialRate, maxFileOperations,
			currentFileOperations, currentJobs;

	private InstanceSubmitQueue submitQueue;

	private boolean tasksFinished;

	public LateBindingScheduler() {
		virtualContacts = Collections.synchronizedMap(new HashMap<Contact, BoundContact>());
		executionHandlers = new HashMap();
		taskContacts = new HashMap();
		jobsPerCPU = DEFAULT_JOBS_PER_CPU;
		maxTransfers = DEFAULT_MAX_TRANSFERS;
		maxFileOperations = DEFAULT_MAX_FILE_OPERATIONS;
		sshInitialRate = DEFAULT_SSH_INITIAL_RATE;
		handlers = new HashMap();
		submitQueue = new InstanceSubmitQueue();
		addFailureHandler(new SSHThrottlingFailureHandler());
		addTaskTransformer(new TCPBufferSizeTransformer());
	}

	public Contact allocateContact(Object constraints) throws NoFreeResourceException {
		if (getResources().size() == 0) {
			throw new NoSuchResourceException("No service contacts available");
		}
		Contact contact = new VirtualContact();
		if (constraints instanceof TaskConstraints) {
			contact.setConstraints((TaskConstraints) constraints);
		}
		return contact;
	}

	public Contact allocateContact() throws NoFreeResourceException {
		return allocateContact(null);
	}

	public void releaseContact(Contact contact) {
		virtualContacts.remove(contact);
		tasksFinished = true;
	}
	
	protected BoundContact getBoundContact(Contact contact) {
	    if (contact instanceof BoundContact) {
	        return (BoundContact) contact;
	    }
	    else {
	    	return virtualContacts.get(contact);
	    }
	}

	public BoundContact resolveVirtualContact(Contact contact)
			throws NoFreeResourceException {
		if (contact.isVirtual()) {
			BoundContact next;
			if (getResources().size() == 0) {
				throw new NoFreeResourceException("No service contacts available");
			}
			next = virtualContacts.get(contact);
			if (next != null) {
				next = virtualContacts.get(contact);
				int index = getResources().indexOf(next);
				if (!checkLoad(next)) {
					throw new NoFreeResourceException("Contact " + next.getHost()
							+ " has too many tasks");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Resolved " + contact + " to " + next);
				}
				return next;
			}
			else {
				next = getNextContact(contact.getConstraints());
				if (next != null) {
					virtualContacts.put(contact, next);
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Resolved " + contact + " to " + next);
				}
				return next;
			}
		}
		else {
			if (logger.isDebugEnabled()) {
				logger.debug("Already resolved: " + contact);
			}
			return (BoundContact) contact;
		}
	}

	public Map<Contact, BoundContact> getVirtualContacts() {
		return this.virtualContacts;
	}

	public void setVirtualContacts(HashMap<Contact, BoundContact> virtualContacts) {
		this.virtualContacts = virtualContacts;
	}

	protected abstract BoundContact getNextContact(TaskConstraints constraints)
			throws NoFreeResourceException;

	protected BoundContact getNextContact(Task t) throws NoFreeResourceException {
		return getNextContact(getTaskConstraints(t));
	}

	protected TaskConstraints getTaskConstraints(Task t) {
		Object constraints = super.getConstraints(t);
		if (constraints instanceof Contact[]) {
			Contact[] c = (Contact[]) constraints;
			if (c.length > 0 && c[0] != null) {
				return c[0].getConstraints();
			}
		}
		return null;
	}

	public void enqueue(Task task, Object constraints) {
		if (constraints != null) {
			setConstraints(task, constraints);
		}
		synchronized (this) {
			if (!started) {
				start();
				started = true;
			}
			getJobQueue().enqueue(task);
			notify();
		}
	}

	public boolean isDone() {
		return done;
	}

	public int getRunning() {
		return running;
	}

	protected void checkGlobalLoadConditions() throws NoFreeResourceException {
		if (!checkFreeMemory()) {
			throw new NoFreeResourceException("Not enough free memory for another job");
		}
	}

	protected void checkTaskLoadConditions(Task t) throws NoFreeResourceException {
		if (t.getType() == 0) {
			return;
		}
		if (t.getType() == Task.FILE_OPERATION && (currentFileOperations >= maxFileOperations)) {
			throw new NoFreeResourceException();
		}
		if (t.getType() == Task.FILE_TRANSFER && (currentTransfers >= maxTransfers)) {
			throw new NoFreeResourceException();
		}
		if (t.getType() == Task.JOB_SUBMISSION && (currentJobs >= getMaxSimultaneousJobs())) {
			throw new NoFreeResourceException();
		}
	}

	// make sure there is enough memory to run 8 more threads (approx).
	public boolean checkFreeMemory() {
		// TODO it is unlikely that Java allocates stack memory for threads from
		// the heap.
		return (THREAD_STACK_SIZE * 8 < Runtime.getRuntime().freeMemory()
				+ Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory());
	}

	protected synchronized int incRunning() {
		return ++running;
	}

	protected synchronized int decRunning() {
		return --running;
	}

	@Override
	public void run() {
		Queue queue = getJobQueue();
		Queue.Cursor c = queue.cursor();
		while (!isDone()) {
			while (queue.isEmpty()) {
				synchronized (this) {
					if (!sleep()) {
						return;
					}
				}
			}
			synchronized (this) {
				tasksFinished = false;
				c.reset();
			}
			while (c.hasNext()) {
				Task t = (Task) c.next();
				boolean remove = true;
				try {
					submitUnbound(t);
				}
				catch (NoSuchResourceException e) {
				    failTask(t, "The application \"" + getTaskConstraints(t).getConstraint("tr")
							+ "\" is not available in your tc.data catalog ", e);
				}
				catch (NoFreeResourceException e) {
					remove = false;
				}
				catch (Exception e) {
					failTask(t, "The scheduler could not execute the task", e);
				}
				if (remove) {
					c.remove();
				}
				else {
					while (!c.hasNext()) {
						synchronized (this) {
							if (!sleep()) {
								return;
							}
							if (tasksFinished) {
								tasksFinished = false;
								c.reset();
							}
						}
					}
				}
			}
		}
	}

	private boolean sleep() {
		try {
			wait(500);
			return true;
		}
		catch (InterruptedException e) {
			logger.info("Scheduler interrupted", e);
			return false;
		}
	}

	public void terminate() {
		done = true;
	}

	protected void failTask(Task t, String message, Exception e) {
		if (logger.isDebugEnabled()) {
			logger.debug("Failing task " + t + " because of " + message, e);
		}
		Status s = new StatusImpl();
		s.setPrevStatusCode(t.getStatus().getStatusCode());
		s.setStatusCode(Status.FAILED);
		s.setMessage(message);
		s.setException(e);
		t.setStatus(s);
		fireJobStatusChangeEvent(t, s);
	}

	private List<Contact> contactTran = new ArrayList<Contact>();

	void submitUnbound(Task t) throws NoFreeResourceException {
		try {
			if (t == null) {
				return;
			}
			checkTaskLoadConditions(t);
			contactTran.clear();
			Service[] services = new Service[t.getRequiredServices()];
			Contact[] contacts;
			Object constraints = getConstraints(t);
			if (constraints != null) {
				contacts = (Contact[]) constraints;
				if (contacts == null) {
					contacts = new Contact[] { this.getNextContact(t) };
					contactTran.add(contacts[0]);
				}
			}
			else {
				contacts = new Contact[t.getRequiredServices()];
				for (int i = 0; i < t.getRequiredServices(); i++) {
					if (t.getService(i) == null) {
						contacts[i] = this.getNextContact(t);
						contactTran.add(contacts[i]);
					}
				}
			}

			for (int i = 0; i < services.length; i++) {
				if (contacts[i] != null && contacts[i].isVirtual()) {
					contacts[i] = resolveContact(t, contacts[i]);
					contactTran.add(contacts[i]);
				}
				try {
					services[i] = t.getService(i);
				}
				catch (IndexOutOfBoundsException e) {
					// Means there's no such service.
					// TODO An alternative way should be provided by core
				}
				if (services[i] == null) {
					services[i] = resolveService((BoundContact) contacts[i], t.getType());
				}
				if (services[i] == null) {
					throw new NoSuchResourceException(
							"Could not find a suitable service/provider for host " + contacts[i]);
				}
				t.setService(i, services[i]);
			}

			if (services.length == 1 && t.getType() == Task.JOB_SUBMISSION) {
				String project = (String) services[0].getAttribute("project");
				if (project != null) {
					((JobSpecification) t.getSpecification()).setAttribute("project", project);
				}
			}

			submitBoundToServices(t, contacts, services);
			logger.debug("No host specified");
		}
		catch (NoFreeResourceException e) {
			Iterator i = contactTran.iterator();
			while (i.hasNext()) {
				releaseContact((Contact) i.next());
			}
			throw e;
		}
		catch (Exception e) {
			e.printStackTrace();
			if (logger.isDebugEnabled()) {
				logger.debug("Scheduler exception: job =" + t.getIdentity().getValue()
						+ ", status = " + t.getStatus(), e);
			}
			failTask(t, e.toString(), e);
			return;
		}
	}

	public BoundContact resolveContact(Task t, Contact contact) throws NoFreeResourceException {
		BoundContact boundContact;
		if (contact == null) {
			boundContact = getNextContact(t);
		}
		else {
			if (contact.isVirtual()) {
				boundContact = resolveVirtualContact(contact);
			}
			else {
				boundContact = (BoundContact) contact;
			}
			boundContact.setConstraints(contact.getConstraints());
		}
		return boundContact;
	}

	public Service resolveService(BoundContact contact, int taskType) {
		Iterator<TaskHandlerWrapper> h = this.getTaskHandlerWrappers(getHandlerType(taskType)).iterator();
		while (h.hasNext()) {
			TaskHandlerWrapper handler = h.next();
			if (contact.hasService(handler)) {
				return contact.getService(handler);
			}
		}
		return null;
	}

	public int getHandlerType(int taskType) {
		int htype = TaskHandler.GENERIC;
		if (taskType == Task.JOB_SUBMISSION) {
			htype = TaskHandler.EXECUTION;
		}
		else if (taskType == 0) {
		    htype = TaskHandler.EXECUTION;
		}
		else {
			htype = TaskHandler.FILE_OPERATION;
		}
		return htype;
	}

	public TaskHandler findTaskHandler(Task task, Service[] services)
			throws TaskSubmissionException, InvalidProviderException, ProviderMethodException {
		if (task.getType() == Task.JOB_SUBMISSION) {
			String provider = services[0].getProvider();
			TaskHandler handler = (TaskHandler) executionHandlers.get(provider);
			if (handler == null) {
				handler = AbstractionFactory.newExecutionTaskHandler(provider);
				executionHandlers.put(provider, handler);
			}
			return handler;
		}
		else if (task.getType() == Task.FILE_OPERATION) {
			if (fileOperationHandler == null) {
				fileOperationHandler = new CachingFileOperationTaskHandler();
			}
			return fileOperationHandler;
		}
		else if (task.getType() == Task.FILE_TRANSFER) {
			if (transferHandler == null) {
				transferHandler = new CachingFileTransferTaskHandler();
			}
			return transferHandler;
		}
		else {
			throw new TaskSubmissionException("Unsupported task type " + task.getType());
		}
	}

	public void submitBoundToServices(Task t, Contact[] contacts, Service[] services)
			throws TaskSubmissionException {
		if (t instanceof ContactAllocationTask) {
			((ContactAllocationTask) t).setContact((BoundContact) contacts[0]);
			removeConstraints(t);
			Status status = t.getStatus();
			status.setPrevStatusCode(status.getStatusCode());
			status.setStatusCode(Status.COMPLETED);
			StatusEvent se = new StatusEvent(t, status);
			fireJobStatusChangeEvent(se);
			return;
		}
		for (int i = 0; i < contacts.length; i++) {
			if (!(contacts[i] instanceof BoundContact)) {
				throw new TaskSubmissionException(
						"submitBoundToServices called but at least a contact is not bound (" + contacts[i] + ")");
			}
			BoundContact c = (BoundContact) contacts[i];
			c.setActiveTasks(c.getActiveTasks() + 1);
		}
		applyTaskTransformers(t, contacts, services);
		t.addStatusListener(this);
		TaskHandler handler;
		try {
			handler = findTaskHandler(t, services);
		}
		catch (Exception e) {
			throw new TaskSubmissionException("Cannot submit task", e);
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Submitting task " + t);
		}
		SubmitQueue[] queues = new SubmitQueue[4];
		queues[0] = GlobalSubmitQueue.getQueue();
		queues[1] = submitQueue;
		HostSubmitQueue shq = submitQueue.getHostQueue((BoundContact) contacts[0]);
		queues[2] = shq;
		queues[3] = shq.getProviderQueue(t.getService(0).getProvider(), sshInitialRate, 2,
				".*throttled.*");
		synchronized (this) {
			taskContacts.put(t, contacts);
		    handlers.put(t, handler);
		}
		NonBlockingSubmit nbs = new NonBlockingSubmit(handler, t, queues);
		nbs.go();
		synchronized(this) {
			incRunning();
			if (t.getType() == Task.FILE_OPERATION) {
				currentFileOperations++;
			}
			else if (t.getType() == Task.FILE_TRANSFER) {
				currentTransfers++;
			}
			else if (t.getType() == Task.JOB_SUBMISSION) {
				currentJobs++;
			}
		}
	}

	public InstanceSubmitQueue getSubmitQueue() {
		return submitQueue;
	}

	protected int getJobsPerCPU() {
		return jobsPerCPU;
	}

	protected TaskHandler getHandler(Task t) {
		synchronized(handlers) {
			return (TaskHandler) handlers.get(t);
		}
	}

	protected void setHandler(Task t, TaskHandler th) {
		synchronized(handlers) {
			handlers.put(t, th);
		}
	}

	protected void removeHandler(Task t) {
		synchronized(handlers) {
			handlers.remove(t);
		}
	}

	@Override
	public void setProperty(String name, Object value) {
		if (name.equalsIgnoreCase(JOBS_PER_CPU)) {
			logger.debug("Scheduler: setting jobsPerCpu to " + value);
			jobsPerCPU = throttleValue(value);
		}
		else if (name.equalsIgnoreCase(SUBMIT_THROTTLE)) {
			submitQueue.setThrottle(throttleValue(value));
		}
		else if (name.equalsIgnoreCase(HOST_SUBMIT_THROTTLE)) {
			submitQueue.setHostThrottle(throttleValue(value));
		}
		else if (name.equalsIgnoreCase(MAX_TRANSFERS)) {
			maxTransfers = throttleValue(value);
		}
		else if (name.equalsIgnoreCase(SSH_INITIAL_RATE)) {
			sshInitialRate = TypeUtil.toInt(value);
		}
		else if (name.equalsIgnoreCase(MAX_FILE_OPERATIONS)) {
			maxFileOperations = throttleValue(value);
		}
		else {
			super.setProperty(name, value);
		}
	}

	public void statusChanged(StatusEvent e) {
		try {
			Task task = (Task) e.getSource();
			Status status = e.getStatus();
			int code = status.getStatusCode();
			if (code == Status.COMPLETED) {
				logComplete(task);
			}
			if (status.isTerminal()) {
				synchronized (this) {
					Contact[] contacts = getContacts(task);
					if (contacts == null) {
						logger.warn("Task had no contacts " + task);
					}
					tasksFinished = true;
					decRunning();
					task.removeStatusListener(this);
					if (task.getType() == Task.FILE_OPERATION) {
						currentFileOperations--;
					}
					else if (task.getType() == Task.FILE_TRANSFER) {
						currentTransfers--;
					}
					if (task.getType() == Task.JOB_SUBMISSION) {
						currentJobs--;
					}
					synchronized (taskContacts) {
						taskContacts.remove(task);
					}

					if (contacts != null) {
						for (int i = 0; i < contacts.length; i++) {
							BoundContact c = (BoundContact) contacts[i];
							c.setActiveTasks(c.getActiveTasks() - 1);
						}
					}

					TaskHandler handler = getHandler(task);
					if (handler == null) {
					    logger.warn("No handler found for task " + task);
					}
					else {
    					try {
    						handler.remove(task);
    					}
    					catch (ActiveTaskException e1) {
    						/*
    						 * I think this is the out of order status events
    						 * phenomenon, where a task gets in an ACTIVE state
    						 * after being COMPLETED. The good news is that it
    						 * should only once get into the state of ACTIVE
    						 */
    						task.getStatus().setStatusCode(code);
    						try {
    							handler.remove(task);
    						}
    						catch (ActiveTaskException e2) {
    							// now it's really weird
    							e1.printStackTrace();
    							Throwable t = new RuntimeException("Something is wrong here", e1);
    							t.printStackTrace();
    						}
    					}
    					finally {
    						removeHandler(task);
    						notify();
    					}
					}
				}
				if (code == Status.FAILED) {
					if (logger.isDebugEnabled()) {
						logger.debug("(" + task.getIdentity().getValue() + ") Failed: ",
								e.getStatus().getException());
					}
					if (runFailureHandlers(task)) {
						return;
					}
				}
				removeConstraints(task);
			}
		}
		catch (Exception ee) {
			logger.warn("Exception caught while processing event", ee);
		}
		fireJobStatusChangeEvent(e);
	}

	void logComplete(Task task) {
		if (logger.isDebugEnabled()) {
			logger.debug(task + " Completed. Waiting: " + getJobQueue().size()
					+ ", Running: " + (getRunning() - 1) + ". Heap size: "
					+ (Runtime.getRuntime().totalMemory() / (1024 * 1024))
					+ "M, Heap free: "
					+ (Runtime.getRuntime().freeMemory() / (1024 * 1024)) + "M, Max heap: "
					+ (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + "M");
		}
		else if (logger.isInfoEnabled()) {
			logger.info("JobQueue: " + getJobQueue().size());
		}
	}

	public void cancelTask(Task task) {
		cancelTask(task, null);
	}

	public void cancelTask(Task task, String message) {
		TaskHandler handler = getHandler(task);
		if (handler != null) {
			new NonBlockingCancel(handler, task, message).go();
		}
	}

	protected boolean checkLoad(BoundContact contact) throws NoFreeResourceException {
		if (contact.getActiveTasks() >= getJobsPerCPU() * contact.getCpus()) {
			return false;
		}
		return true;
	}

	public static String[] propertyNames;

	@Override
	public synchronized String[] getPropertyNames() {
		if (propertyNames == null) {
			propertyNames = AbstractScheduler.combineNames(super.getPropertyNames(), new String[] {
					JOBS_PER_CPU, HOST_SUBMIT_THROTTLE, SUBMIT_THROTTLE, MAX_TRANSFERS,
					SSH_INITIAL_RATE, MAX_FILE_OPERATIONS });
		}
		return propertyNames;
	}

	protected Contact[] getContacts(Task t) {
		return (Contact[]) taskContacts.get(t);
	}

	protected synchronized void raiseTasksFinished() {
		this.tasksFinished = true;
	}
}
