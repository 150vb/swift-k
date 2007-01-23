//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Jul 20, 2006
 */
package org.globus.cog.karajan.scheduler;

import org.globus.cog.abstraction.impl.common.StatusImpl;
import org.globus.cog.abstraction.interfaces.Status;
import org.globus.cog.abstraction.interfaces.Task;

public class SSHThrottlingFailureHandler implements FailureHandler {
	public static final String ATTR_RESTARTS = "throttling:restarts";
	public static final int DEFAULT_MAX_RESTARTS = 2;

	private int maxRestarts = DEFAULT_MAX_RESTARTS;

	public boolean handleFailure(Task t, Scheduler s) {
		Exception e = t.getStatus().getException();
		if (e == null
				|| e.getMessage() == null || !e.getMessage().matches(
						".*SSH Connection failed.*server throttled the connection.*")) {
			return false;
		}
		Integer restarts = (Integer) t.getAttribute(ATTR_RESTARTS);
		if (restarts == null) {
			restarts = new Integer(1);
		}
		else {
			restarts = new Integer(restarts.intValue() + 1);
		}
		if (restarts.intValue() > maxRestarts) {
			return false;
		}
		else {
			System.err.println("restarts: "+restarts);
			Status status = new StatusImpl();
			status.setStatusCode(Status.UNSUBMITTED);
			t.setStatus(status);
			s.enqueue(t, null);
			return true;
		}
	}

	public void setProperty(String name, String value) {
		if ("maxRestarts".equalsIgnoreCase(name)) {
			maxRestarts = Integer.parseInt(value);
		}
		else {
			throw new IllegalArgumentException(name);
		}
	}

}
