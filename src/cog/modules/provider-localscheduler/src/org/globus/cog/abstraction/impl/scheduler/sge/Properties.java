//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Oct 20, 2005
 */
package org.globus.cog.abstraction.impl.scheduler.sge;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.impl.scheduler.common.AbstractProperties;

public class Properties extends AbstractProperties {
	private static Logger logger = Logger.getLogger(Properties.class);

	public static final String PROPERTIES = "provider-sge.properties";
	
	public static final String POLL_INTERVAL = "poll.interval";
	public static final String QSUB = "qsub";
	public static final String QSTAT = "qstat";
	public static final String QDEL = "qdel";
	public static final String DEFAULT_PE = "parallel.environment"; 

	private static Properties properties;

	public static synchronized Properties getProperties() {
		if (properties == null) {
			properties = new Properties();
			properties.load(PROPERTIES);
		}
		return properties;
	}

	protected void setDefaults() {
		setPollInterval(10);
		setSubmitCommand("qsub");
		setPollCommand("qstat");
		setRemoveCommand("qdel");
		setDefaultPE("1way");
	}

	public String getPollCommandName() {
		return QSTAT;
	}


	public String getRemoveCommandName() {
		return QDEL;
	}


	public String getSubmitCommandName() {
		return QSUB;
	}
	
	public void setDefaultPE(String pe) {
	    setProperty(DEFAULT_PE, pe);
	}
	
	public String getDefaultPE() {
	    return getProperty(DEFAULT_PE);
	}
}
