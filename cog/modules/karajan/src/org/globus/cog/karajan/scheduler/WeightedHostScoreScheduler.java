//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Jun 20, 2005
 */
package org.globus.cog.karajan.scheduler;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.impl.common.StatusEvent;
import org.globus.cog.abstraction.interfaces.Status;
import org.globus.cog.abstraction.interfaces.Task;
import org.globus.cog.karajan.util.BoundContact;
import org.globus.cog.karajan.util.Contact;
import org.globus.cog.karajan.util.ContactSet;
import org.globus.cog.karajan.util.TypeUtil;
import org.globus.cog.karajan.workflow.KarajanRuntimeException;

public class WeightedHostScoreScheduler extends LateBindingScheduler {

	private static final Logger logger = Logger.getLogger(WeightedHostScoreScheduler.class);

	public static final int POLICY_WEIGHTED_RANDOM = 0;
	public static final int POLICY_BEST_SCORE = 1;
	public static final String FACTOR_CONNECTION_REFUSED = "connectionRefusedFactor";
	public static final String FACTOR_CONNECTION_TIMEOUT = "connectionTimeoutFactor";
	public static final String FACTOR_SUBMISSION_TASK_LOAD = "jobSubmissionTaskLoadFactor";
	public static final String FACTOR_TRANSFER_TASK_LOAD = "transferTaskLoadFactor";
	public static final String FACTOR_FILEOP_TASK_LOAD = "fileOperationTaskLoadFactor";
	public static final String FACTOR_SUCCESS = "successFactor";
	public static final String FACTOR_FAILURE = "failureFactor";
	public static final String SCORE_HIGH_CAP = "scoreHighCap";
	public static final String SCORE_LOW_CAP = "scoreLowCap";
	public static final String RENORMALIZATION_DELAY = "renormalizationDelay";
	public static final String POLICY = "policy";

	private WeightedHostSet sorted;
	private double sum;
	private int policy;
	private int delay;

	public WeightedHostScoreScheduler() {
		policy = POLICY_WEIGHTED_RANDOM;
		setDefaultFactors();
	}

	protected final void setDefaultFactors() {
		setFactor(FACTOR_CONNECTION_REFUSED, 0.1);
		setFactor(FACTOR_CONNECTION_TIMEOUT, 0.05);
		setFactor(FACTOR_SUBMISSION_TASK_LOAD, 0.9);
		setFactor(FACTOR_TRANSFER_TASK_LOAD, 0.9);
		setFactor(FACTOR_FILEOP_TASK_LOAD, 0.95);
		setFactor(FACTOR_SUCCESS, 1.2);
		setFactor(FACTOR_FAILURE, 0.9);
		setFactor(SCORE_HIGH_CAP, 100);
		setFactor(SCORE_LOW_CAP, 0.001);
		setFactor(RENORMALIZATION_DELAY, 100);
	}

	protected final void setFactor(String name, double value) {
		setProperty(name, new Double(value));
	}

	protected double getFactor(String name) {
		return ((Double) getProperty(name)).doubleValue();
	}

	public void setResources(ContactSet grid) {
		super.setResources(grid);
		sorted = new WeightedHostSet();
		Iterator i = grid.getContacts().iterator();
		while (i.hasNext()) {
			addToSorted(new WeightedHost((BoundContact) i.next()));
		}
	}

	protected void addToSorted(WeightedHost wh) {
		sorted.add(wh);
		sum += wh.getScore();
	}

	protected synchronized void multiplyScore(WeightedHost wh, double factor) {
		double score = sorted.remove(wh);
		if (logger.isDebugEnabled()) {
			logger.debug("multiplyScore(" + wh + ", " + factor + ")");
		}
		sum -= score;
		WeightedHost nwh = new WeightedHost(wh.getHost(), checkCaps(score * factor));
		if (logger.isDebugEnabled()) {
			logger.debug("Old score: " + score + ", new score: " + nwh.getScore());
		}
		sorted.add(nwh);
		sum += nwh.getScore();
	}

	protected double checkCaps(double score) {
		if (score > getFactor(SCORE_HIGH_CAP)) {
			return getFactor(SCORE_HIGH_CAP);
		}
		else if (score < getFactor(SCORE_LOW_CAP)) {
			return getFactor(SCORE_LOW_CAP);
		}
		else {
			return score;
		}
	}

	protected synchronized BoundContact getNextContact(TaskConstraints t) throws NoFreeResourceException {
		checkGlobalLoadConditions();
		BoundContact contact;
		if (policy == POLICY_WEIGHTED_RANDOM) {
			double rand = Math.random() * sum;
			if (logger.isDebugEnabled()) {
				logger.debug("Sorted: " + sorted);
				logger.debug("Rand: " + rand + ", sum: " + sum);
			}
			Iterator i = sorted.iterator();
			double sum = 0;
			while (i.hasNext()) {
				WeightedHost wh = (WeightedHost) i.next();
				sum += wh.getScore();
				if (sum >= rand) {
					return wh.getHost();
				}
			}
			renormalize();
			contact = sorted.last().getHost();
		}
		else if (policy == POLICY_BEST_SCORE) {
			renormalize();
			contact = sorted.last().getHost();
		}
		else {
			throw new KarajanRuntimeException("Invalid policy number: " + policy);
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Next contact: " + contact);
		}
		return contact;
	}

	protected void renormalize() {
		delay++;
		if (delay > getFactor(RENORMALIZATION_DELAY)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Renormalizing...");
				logger.debug("Before normalization: " + sorted);
			}
			delay = 0;
			double prod = 1;
			Iterator i = sorted.iterator();
			while (i.hasNext()) {
				WeightedHost wh = (WeightedHost) i.next();
				prod *= wh.getScore();
			}
			double geomAvg = Math.pow(prod, 1.0 / sorted.size());
			double renormalizationFactor = 1 / geomAvg;
			i = sorted.iterator();
			sorted = new WeightedHostSet();
			while (i.hasNext()) {
				WeightedHost wh = (WeightedHost) i.next();
				WeightedHost nwh = new WeightedHost(wh.getHost(), checkCaps(wh.getScore()
						* renormalizationFactor));
				sorted.add(nwh);
			}
			if (logger.isDebugEnabled()) {
				logger.debug("After normalization: " + sorted);
			}
		}
	}

	private static String[] propertyNames;
	private static final String[] myPropertyNames = new String[] { POLICY,
			FACTOR_CONNECTION_REFUSED, FACTOR_CONNECTION_TIMEOUT, FACTOR_SUBMISSION_TASK_LOAD,
			FACTOR_TRANSFER_TASK_LOAD, FACTOR_FILEOP_TASK_LOAD, FACTOR_FAILURE, FACTOR_SUCCESS,
			SCORE_HIGH_CAP, SCORE_LOW_CAP, RENORMALIZATION_DELAY };
	private static Set propertyNamesSet;

	static {
		propertyNamesSet = new HashSet();
		for (int i = 0; i < myPropertyNames.length; i++) {
			propertyNamesSet.add(myPropertyNames[i]);
		}
	}

	public synchronized String[] getPropertyNames() {
		if (propertyNames == null) {
			propertyNames = AbstractScheduler.combineNames(super.getPropertyNames(),
					myPropertyNames);
		}
		return propertyNames;
	}

	public final void setProperty(String name, Object value) {
		if (propertyNamesSet.contains(name)) {
			if (POLICY.equals(name)) {
				if (value instanceof String) {
					value = ((String) value).toLowerCase();
				}
				if ("random".equals(value)) {
					policy = POLICY_WEIGHTED_RANDOM;
				}
				else if ("best".equals("value")) {
					policy = POLICY_BEST_SCORE;
				}
				else {
					throw new KarajanRuntimeException("Unknown policy type: " + value);
				}
			}
			else {
				super.setProperty(name, new Double(TypeUtil.toDouble(value)));
			}
		}
	}

	public void statusChanged(StatusEvent e) {
		Task t = (Task) e.getSource();
		int code = e.getStatus().getStatusCode();
		Contact[] contacts = getContacts(t);
		if (code == Status.SUBMITTED) {
			factorSubmission(t, contacts, 1);
		}
		else if (code == Status.COMPLETED) {
			factorSubmission(t, contacts, -1);
			factorMultiple(contacts, getFactor(FACTOR_SUCCESS));
		}
		else if (code == Status.FAILED) {
			factorMultiple(contacts, getFactor(FACTOR_FAILURE));
			Exception ex = e.getStatus().getException();
			if (ex != null) {
				String exs = ex.toString();
				if (exs.indexOf("Connection refused") > 0 || exs.indexOf("connection refused") > 0) {
					factorMultiple(contacts, getFactor(FACTOR_CONNECTION_REFUSED));
				}
				else if (exs.indexOf("timeout") > 0) {
					factorMultiple(contacts, getFactor(FACTOR_CONNECTION_TIMEOUT));
				}
			}
		}
		super.statusChanged(e);
	}

	private void factorSubmission(Task t, Contact[] contacts, int exp) {
		// I wonder where the line between abstraction and obfuscation is...
		if (t.getType() == Task.JOB_SUBMISSION) {
			factorMultiple(contacts, Math.pow(getFactor(FACTOR_SUBMISSION_TASK_LOAD), exp));
		}
		else if (t.getType() == Task.FILE_TRANSFER) {
			factorMultiple(contacts, Math.pow(getFactor(FACTOR_TRANSFER_TASK_LOAD), exp));
		}
		else if (t.getType() == Task.FILE_OPERATION) {
			factorMultiple(contacts, Math.pow(getFactor(FACTOR_FILEOP_TASK_LOAD), exp));
		}
	}

	private void factorMultiple(Contact[] contacts, double factor) {
		for (int i = 0; i < contacts.length; i++) {
			WeightedHost wh = new WeightedHost((BoundContact) contacts[i]);
			multiplyScore(wh, factor);
		}
	}

}
