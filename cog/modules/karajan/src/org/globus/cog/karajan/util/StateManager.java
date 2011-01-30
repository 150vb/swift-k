// ----------------------------------------------------------------------
// This code is developed as part of the Java CoG Kit project
// The terms of the license can be found at http://www.cogkit.org/license
// This message may not be removed or altered.
// ----------------------------------------------------------------------

/*
 * Created on Sep 29, 2003
 */
package org.globus.cog.karajan.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.globus.cog.karajan.stack.VariableNotFoundException;
import org.globus.cog.karajan.stack.VariableStack;
import org.globus.cog.karajan.workflow.ExecutionContext;
import org.globus.cog.karajan.workflow.ExecutionException;
import org.globus.cog.karajan.workflow.events.EventTargetPair;
import org.globus.cog.karajan.workflow.nodes.FlowElement;
import org.globus.cog.karajan.workflow.nodes.ProjectNode;

public class StateManager {
	private static final Logger logger = Logger.getLogger(StateManager.class);

	private static long waitTime = 10;
	private long last;
	private long counter;
	private static Timer timer;
	private boolean restartComplete = true;
	private String fileName = "checkpoint.xml";
	private int checkpointInterval = 120; // seconds
	private Map executing;

	private Integer interval;
	private String name;
	private boolean checkpointsEnabled;
	private Set checkpoints;
	private ExecutionContext ec;
	private boolean timestamped;
	private Task task;

	public StateManager(ExecutionContext ec) {
		this.ec = ec;
		last = 20;
		interval = new Integer(12);
		counter = 0;
		executing = new HashMap();
		name = "checkpoint.xml";
		checkpointsEnabled = false;
		checkpoints = new HashSet();
	}

	private Timer getTimer() {
		synchronized (StateManager.class) {
			if (timer == null) {
				timer = new Timer(true);
			}
			return timer;
		}
	}

	public synchronized void registerElement(FlowElement el, VariableStack stack)
			throws VariableNotFoundException {
		if (logger.isDebugEnabled()) {
			logger.debug("Registering " + el);
		}
		executing.put(new ThreadedElement(el, ThreadingContext.get(stack)), stack);
	}

	public synchronized void unregisterElement(FlowElement el, VariableStack stack)
			throws VariableNotFoundException {
		if (logger.isDebugEnabled()) {
			logger.debug("Unregistering " + el);
		}
		executing.remove(new ThreadedElement(el, ThreadingContext.get(stack)));
	}

	public void abortContext(ThreadingContext context) throws ExecutionException {
		Iterator i;
		synchronized (this) {
			i = new HashSet(executing.keySet()).iterator();
		}
		while (i.hasNext()) {
			ThreadedElement te = (ThreadedElement) i.next();
			if (te.getThread().isSubContext(context)) {
				VariableStack stack = (VariableStack) executing.get(te);
				if (stack != null) {
					te.getElement().abort(stack.copy());
					synchronized (this) {
						executing.remove(te);
					}
				}
			}
		}
	}

	public long getWaitTime() {
		return waitTime;
	}

	public void setWaitTime(long l) {
		counter = 0;
		waitTime = l;
	}

	public boolean request() {
		if ((last > waitTime) && (restartComplete)) {
			last = 0;
			return true;
		}
		logger.debug("Request for checkpoint denied (last=" + last + ", waitTime=" + waitTime
				+ ", restartComplete=" + restartComplete + ")");
		return false;
	}

	public boolean isRestartComplete() {
		return restartComplete;
	}

	public void setRestartComplete(boolean b) {
		restartComplete = b;
	}

	
	
	private static final String[] EMPTY_STRING_ARRAY = new String[0];

	public String[] getCheckpoints() {
		synchronized (checkpoints) {
			return (String[]) checkpoints.toArray(EMPTY_STRING_ARRAY);
		}
	}

	public Integer getInterval() {
		return this.interval;
	}

	public void setInterval(Integer interval) {
		this.interval = interval;
		checkpointInterval = interval.intValue() / 10;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
		fileName = name;
	}

	public boolean getCheckpointsEnabled() {
		return this.checkpointsEnabled;
	}

	public synchronized void setCheckpointsEnabled(boolean checkpointsEnabled) {
		if (this.checkpointsEnabled == checkpointsEnabled) {
			return;
		}
		this.checkpointsEnabled = checkpointsEnabled;
		if (checkpointsEnabled) {
			getTimer().schedule(task = new Task(this), 10000, 10000);
		}
		else {
			task.cancel();
			task = null;
		}
	}

	public synchronized void stop() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}


	public Map getExecuting() {
		return this.executing;
	}

	public boolean isTimestamped() {
		return timestamped;
	}

	public void setTimestamped(boolean timestamped) {
		this.timestamped = timestamped;
	}

	public class Task extends TimerTask {
		private final StateManager cm;

		public Task(StateManager cm) {
			this.cm = cm;
		}

		public void run() {
			//cm.actionPerformed();
		}
	}

	public static class _Checkpoint {
		public ProjectNode projectNode;
		public _State state;
	}

	public static class _State {
		public List runningElements;
		public List events;

		public _State() {
			runningElements = new ArrayList();
			events = new ArrayList();
		}

		public void addRunningElement(Object e) {
			runningElements.add(e);
		}

		public void addEvent(EventTargetPair e) {
			events.add(e);
		}
	}

	public static class _RunningElement {
		public FlowElement _element;
		public VariableStack _stack;

		public _RunningElement(FlowElement element, VariableStack stack) {
			this._element = element;
			this._stack = stack;
		}
	}

}