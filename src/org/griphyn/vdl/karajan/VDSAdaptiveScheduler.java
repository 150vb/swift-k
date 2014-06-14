/*
 * Copyright 2012 University of Chicago
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/*
 * Created on Jun 12, 2006
 */
package org.griphyn.vdl.karajan;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.coaster.service.local.CoasterResourceTracker;
import org.globus.cog.abstraction.impl.common.IdentityImpl;
import org.globus.cog.abstraction.impl.common.StatusEvent;
import org.globus.cog.abstraction.impl.common.StatusImpl;
import org.globus.cog.abstraction.impl.common.task.JobSpecificationImpl;
import org.globus.cog.abstraction.impl.common.task.TaskImpl;
import org.globus.cog.abstraction.interfaces.JobSpecification;
import org.globus.cog.abstraction.interfaces.Service;
import org.globus.cog.abstraction.interfaces.Status;
import org.globus.cog.abstraction.interfaces.StatusListener;
import org.globus.cog.abstraction.interfaces.Task;
import org.globus.cog.karajan.scheduler.AbstractScheduler;
import org.globus.cog.karajan.scheduler.ContactAllocationTask;
import org.globus.cog.karajan.scheduler.ResourceConstraintChecker;
import org.globus.cog.karajan.scheduler.TaskConstraints;
import org.globus.cog.karajan.scheduler.WeightedHostScoreScheduler;
import org.globus.cog.karajan.scheduler.WeightedHostSet;
import org.globus.cog.karajan.util.BoundContact;
import org.globus.cog.karajan.util.Contact;
import org.globus.cog.karajan.util.ContactSet;
import org.globus.cog.karajan.util.TypeUtil;
import org.globus.swift.catalog.TCEntry;
import org.globus.swift.catalog.transformation.File;
import org.globus.swift.catalog.types.TCType;
import org.griphyn.vdl.util.FQN;


public class VDSAdaptiveScheduler extends WeightedHostScoreScheduler implements CoasterResourceTracker {
	public static final Logger logger = Logger.getLogger(VDSAdaptiveScheduler.class);

	private static Timer timer;

	private TCCache tc;
	private LinkedList<Entry> dq;
	private int clusteringQueueDelay = 1;
	private int minClusterTime = 60;
	private Map<Task, List<Entry>> tasks;
	private boolean clusteringEnabled;
	private int clusterId;
	
	/**
	 * A map to allow quick determination of what contact a service
	 * belongs to
	 */
	private Map<Service, BoundContact> serviceContactMapping;

	public VDSAdaptiveScheduler() {
		dq = new LinkedList<Entry>();
		tasks = new HashMap<Task, List<Entry>>();
		serviceContactMapping = new HashMap<Service, BoundContact>();
	}

	public static final String PROP_TC_FILE = "transformationCatalogFile";
	public static final String PROP_CLUSTERING_ENABLED = "clusteringEnabled";
	public static final String PROP_CLUSTERING_QUEUE_DELAY = "clusteringQueueDelay";
	public static final String PROP_CLUSTERING_MIN_TIME = "clusteringMinTime";

	private static String[] propertyNames;

	public synchronized String[] getPropertyNames() {
		if (propertyNames == null) {
			propertyNames = AbstractScheduler.combineNames(super.getPropertyNames(),
					new String[] { PROP_TC_FILE });
		}
		return propertyNames;
	}

	public void setProperty(String name, Object value) {
		if (PROP_TC_FILE.equals(name)) {
			tc = new TCCache(File.getNonSingletonInstance((String) value));
			this.setConstraintChecker(new TCChecker(tc));
			this.addTaskTransformer(new VDSTaskTransformer(tc));
		}
		else if (PROP_CLUSTERING_QUEUE_DELAY.equals(name)) {
			clusteringQueueDelay = TypeUtil.toInt(value);
		}
		else if (PROP_CLUSTERING_MIN_TIME.equals(name)) {
			minClusterTime = TypeUtil.toInt(value);
		}
		else if (PROP_CLUSTERING_ENABLED.equals(name)) {
			clusteringEnabled = TypeUtil.toBoolean(value);
		}
		else {
			super.setProperty(name, value);
		}
	}

	@Override
	public void enqueue(Task task, Object constraints, StatusListener l) {
		if (shouldBeClustered(task, constraints)) {
			startTimer();
			if (logger.isDebugEnabled()) {
				logger.debug("Adding task to clustering queue: " + task.getIdentity());
			}
			synchronized (dq) {
				dq.addLast(new Entry(task, constraints, l));
			}
		}
		else {
			super.enqueue(task, constraints, l);
		}
	}

	private synchronized Timer startTimer() {
		if (timer == null) {
			timer = new Timer(true);
			timer.schedule(new TimerTask() {
				public void run() {
					processDelayQueue();
				}
			}, clusteringQueueDelay * 1000, clusteringQueueDelay * 1000);
		}
		return timer;
	}

	private boolean shouldBeClustered(Task task, Object constraints) {
		if (!clusteringEnabled) {
			return false;
		}
		String reason = null;
		try {
			if (task.getType() != Task.JOB_SUBMISSION) {
				reason = "not a job";
				return false;
			}
			if (((JobSpecification) task.getSpecification()).getAttribute("maxwalltime") == null) {
				reason = "no maxwalltime";
				return false;
			}
			if (!(constraints instanceof Contact[])) {
				reason = "weird constraints";
				return false;
			}

			if (((Contact[]) constraints).length != 1) {
				reason = "constraints size != 1";
				return false;
			}
			boolean cluster = getMaxWallTime(task) < minClusterTime;
			if (!cluster) {
				reason = "not short enough";
			}
			return cluster;
		}
		finally {
			if (reason != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("Task is not suitable for clustering (" + reason + ") "
							+ task.getIdentity());
				}
			}
		}
	}

	/*
	 * TODO Add maxmemory=max(maxmemory), minmemory=max(minmemory) and all other
	 * similar attributes
	 */
	private void processDelayQueue() {
		if (logger.isDebugEnabled()) {
			logger.debug("Processing clustering queue");
		}
		synchronized (dq) {
			while (!dq.isEmpty()) {
				int clusterTime = 0;
				LinkedList<Entry> cluster = new LinkedList<Entry>();
				Map<String, String> env = new HashMap<String, String>();
				Map<String, Object> attrs = new HashMap<String, Object>();
				Object constraints = null;
				String dir = null;

				Iterator<Entry> dqi = dq.iterator();
				while (clusterTime < minClusterTime && dqi.hasNext()) {
					Entry e = dqi.next();
					Task task = e.task;

					JobSpecification js = (JobSpecification) task.getSpecification();

					if (constraints == null) {
						constraints = ((Object[]) e.constraints)[0];
					}
					else if (!constraints.equals(((Object[]) e.constraints)[0])) {
						continue;
					}

					if (dir == null) {
						dir = js.getDirectory() == null ? "" : js.getDirectory();
					}
					else if ((js.getDirectory() != null || !dir.equals(""))
							&& !dir.equals(js.getDirectory())) {
						continue;
					}

					if (detectConflict(js, env, attrs)) {
						continue;
					}
					else {
						dqi.remove();
					}

					merge(js, env, attrs);

					clusterTime += getMaxWallTime(task);
					cluster.addLast(e);
				}

				if (logger.isDebugEnabled()) {
					logger.debug("Got a cluster with size " + cluster.size());
				}

				if (cluster.size() == 0) {
					continue;
				}
				else if (cluster.size() == 1) {
					Entry e = cluster.removeFirst();
					super.enqueue(e.task, e.constraints, e.listener);
				}
				else if (cluster.size() > 1) {
					Task t = new TaskImpl();
					int thisClusterId = clusterId++;
					t.setIdentity(new IdentityImpl("cluster-" + thisClusterId));
					t.setType(Task.JOB_SUBMISSION);
					t.setRequiredService(1);

					JobSpecification js = new JobSpecificationImpl();
					t.setSpecification(js);
					js.setExecutable("/bin/sh");
					js.addArgument("shared/_swiftseq");
					js.addArgument("cluster-"+thisClusterId);
					js.addArgument("/clusters/"); // slice path more here TODO
					js.setDirectory(dir);
					js.setAttribute("maxwalltime", secondsToTime(clusterTime));
					
					if (logger.isInfoEnabled()) {
						logger.info("Creating cluster " + t.getIdentity() + " with size " + cluster.size());
					}

					for (Entry e : cluster) {
						Task st = e.task;
						if (logger.isInfoEnabled()) {
							logger.info("Task " + st.getIdentity() + " clustered in " + t.getIdentity());
						}
						JobSpecification sjs = (JobSpecification) st.getSpecification();
						js.addArgument(sjs.getExecutable());
						List<String> args = sjs.getArgumentsAsList();
						for (String arg : args) {
							if (arg.equals("|")) {
								arg = "||";
							}
							js.addArgument(arg);
						}
						js.addArgument("|");
					}
					
					for (Map.Entry<String, String> e : env.entrySet()) {
						js.addEnvironmentVariable(e.getKey(), e.getValue());
					}

					for (Map.Entry<String, Object> e : attrs.entrySet()) {
						js.setAttribute(e.getKey(), e.getValue());
					}

					synchronized (tasks) {
						tasks.put(t, cluster);
					}
					super.enqueue(t, new Contact[] { (Contact) constraints }, null);
				}
			}
		}
	}

	private boolean detectConflict(JobSpecification js, Map<String, String> env, Map<String, Object> attrs) {
		return detectEnvironmentConflict(js, env) || detectAttributeConflict(js, attrs);
	}

	private boolean detectEnvironmentConflict(JobSpecification js, Map<String, String> env) {
	    for (String envName : js.getEnvironmentVariableNames()) {
			Object value = env.get(envName);
			if (value != null && !value.equals(js.getEnvironmentVariable(envName))) {
				return true;
			}
		}
		return false;
	}

	private boolean detectAttributeConflict(JobSpecification js, Map<String, Object> attrs) {
	    for (String attrName : js.getAttributeNames()) {
			if (attrName.equals("maxwalltime")) {
				continue;
			}
			Object value = attrs.get(attrName);
			if (value != null && !value.equals(js.getAttribute(attrName))) {
				return true;
			}
		}
		return false;
	}

	private void merge(JobSpecification js, Map<String, String> env, Map<String, Object> attrs) {
		mergeEnvironment(js, env);
		mergeAttributes(js, attrs);
	}

	private void mergeEnvironment(JobSpecification js, Map<String, String> env) {
	    for (String envName : js.getEnvironmentVariableNames()) {
			env.put(envName, js.getEnvironmentVariable(envName));
		}
	}

	private void mergeAttributes(JobSpecification js, Map<String, Object> attrs) {
	    for (String attrName : js.getAttributeNames()) {
			if (attrName.equals("maxwalltime")) {
				continue;
			}
			attrs.put(attrName, js.getAttribute(attrName));
		}
	}

	private int getMaxWallTime(Task t) {
		return timeToSeconds(TypeUtil.toString(((JobSpecification) t.getSpecification()).getAttribute("maxwalltime")));
	}

	/**
	 * Valid times formats: Minutes, Hours:Minutes, Hours:Minutes:Seconds
	 */
	public static int timeToSeconds(String time) {
		String[] s = time.split(":");
		try {
			if (s.length == 1) {
				return 60 * Integer.parseInt(s[0]);
			}
			else if (s.length == 2) {
				return 60 * Integer.parseInt(s[1]) + 3600 * Integer.parseInt(s[0]);
			}
			else if (s.length == 3) {
				return Integer.parseInt(s[2]) + 60 * Integer.parseInt(s[1]) + 3600
						* Integer.parseInt(s[0]);
			}
		}
		catch (NumberFormatException e) {
		}
		throw new IllegalArgumentException("Invalid time specification: " + time);
	}

	public static String secondsToTime(int seconds) {
		StringBuffer sb = new StringBuffer();
		pad(sb, seconds / 3600);
		sb.append(':');
		pad(sb, (seconds % 3600) / 60);
		sb.append(':');
		pad(sb, seconds % 60);
		return sb.toString();
	}

	private static void pad(StringBuffer sb, int value) {
		if (value < 10) {
			sb.append('0');
		}
		sb.append(String.valueOf(value));
	}

	@Override
	protected void failTask(Entry e, String message, Exception ex) {
		if (logger.isDebugEnabled()) {
			logger.debug("Failing task " + e.task.getIdentity());
		}
		Task t = e.task;
		List<Entry> cluster = null;
		if (!(t instanceof ContactAllocationTask)) {
    		synchronized (tasks) {
    			cluster = tasks.get(t);
    		}
		}
		if (cluster != null) {
		    for (Entry e1 : cluster) {
				super.failTask(e1, message, ex);
			}
		}
		else {
			super.failTask(e, message, ex);
		}

	}

	@Override
	public void statusChanged(StatusEvent se, Entry e) {
		Task t = e.task;
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Got task status change for " + t.getIdentity());
			}
			List<Entry> cluster = null;
			synchronized (tasks) {
				cluster = tasks.get(t);
			}

			if (cluster == null) {
				super.statusChanged(se, e);
			}
			else {
				if (logger.isDebugEnabled()) {
					logger.debug("Got cluster status change for " + t.getIdentity());
				}

				Status clusterMemberStatus = se.getStatus();
				if(clusterMemberStatus.getStatusCode() == Status.FAILED) {
					clusterMemberStatus = new StatusImpl(Status.COMPLETED);
				}
				for (Entry e1 : cluster) {
					Task ct = e1.task;
					StatusEvent nse = new StatusEvent(ct, clusterMemberStatus);
					ct.setStatus(clusterMemberStatus);
					fireJobStatusChangeEvent(nse, e1);
				}
				if (se.getStatus().isTerminal()) {
					if (logger.isInfoEnabled()) {
						logger.info("Removing cluster " + t.getIdentity());
					}
					synchronized (tasks) {
						tasks.remove(t);
					}
				}
			}
		}
		catch (Exception ex) {
			failTask(e, ex.getMessage(), ex);
		}
	}
	
	@Override
    public void setResources(ContactSet cs) {
	    if (cs == null || cs.getContacts() == null) {
	        throw new IllegalArgumentException("No sites specified");
	    }
        super.setResources(cs);
        for (BoundContact bc : cs.getContacts()) {
            if ("passive".equals(bc.getProperty("globus:workerManager")) 
                    && "true".equals(bc.getProperty("globus:throttleTracksWorkers"))) {
                Service s = bc.getService(Service.EXECUTION, "coaster");
                if (s != null) {
                    s.setAttribute("resource-tracker", this);
                    WeightedHostSet whs = getWeightedHostSet();
                    // set throttle to one so that a task gets sent
                    // to the provider and the connection/service is 
                    // initialized/started
                    whs.changeThrottleOverride(whs.findHost(bc), 1);
                    serviceContactMapping.put(s, bc);
                }
            }
        }
    }
	
	public void resourceUpdated(Service service, String name, String value) {
	    if (logger.isInfoEnabled()) {
	        logger.info(service + " resource updated: " + name + " -> " + value);
	    }
	    if (name.equals("job-capacity")) {
	        int throttle = Integer.parseInt(value);
    	    BoundContact bc = serviceContactMapping.get(service);
    	    WeightedHostSet whs = getWeightedHostSet();
    	    whs.changeThrottleOverride(whs.findHost(bc), throttle > 0 ? throttle : 1);
    	    
    	    raiseTasksFinished();
	    }
    }

	public static class TCChecker implements ResourceConstraintChecker {
		private TCCache tc;

		public TCChecker(TCCache tc) {
			this.tc = tc;
		}

		public boolean checkConstraints(BoundContact resource, TaskConstraints tc) {
			if (isPresent("trfqn", tc)) {
				FQN tr = (FQN) tc.getConstraint("trfqn");
				try {
					List<TCEntry> l = this.tc.getTCEntries(tr, resource.getHost(), TCType.INSTALLED);
					if (l == null || l.isEmpty()) {
						return false;
					}
					else {
						return true;
					}
				}
				catch (Exception e) {
					logger.warn("Exception caught while querying TC", e);
					return false;
				}
			}
			else {
				return true;
			}
		}

		private boolean isPresent(String constraint, TaskConstraints t) {
			if (t == null) {
				return false;
			}
			if (t.getConstraint(constraint) == null) {
				return false;
			}
			return true;
		}

		
		@SuppressWarnings({ "rawtypes", "unchecked" })
        public List checkConstraints(List resources, TaskConstraints tc) {
			LinkedList l = new LinkedList();
			Iterator i = resources.iterator();
			while (i.hasNext()) {
				BoundContact res = (BoundContact) i.next();
				if (checkConstraints(res, tc)) {
					l.add(res);
				}
			}
			return l;
		}
	}
}
