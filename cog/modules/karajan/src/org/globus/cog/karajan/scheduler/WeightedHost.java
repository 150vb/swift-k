//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Jun 21, 2005
 */
package org.globus.cog.karajan.scheduler;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import org.apache.log4j.Logger;
import org.globus.cog.karajan.util.BoundContact;

public class WeightedHost implements Comparable {

	static final int MINWEIGHT = -10;

	private static final Logger logger = Logger.getLogger(WeightedHost.class);

	private BoundContact host;
	private Double score;
	private double tscore;
	private int load;
	private double delayedDelta;
	private float jobThrottle;
	private long lastUsed;

	public WeightedHost(BoundContact contact, float jobThrottle) {
		this(contact, 0.0, jobThrottle);
	}

	public WeightedHost(BoundContact contact, double score, float jobThrottle) {
		this(contact, score, 0, jobThrottle);
	}

	public WeightedHost(BoundContact contact, double score, int load, float jobThrottle) {
		this.host = contact;
		setScore(score);
		this.load = load;
		this.jobThrottle = jobThrottle;
	}

	protected void setScore(double score) {
		if (score < MINWEIGHT)
			score = MINWEIGHT;
		this.score = new Double(score);
		this.tscore = smooth(score);
	}

	public static final double T = 100;
	public static final double B = 2.0 * Math.log(T) / Math.PI;
	public static final double C = 0.2;

	public double smooth(double score) {
		return Math.exp(B * Math.atan(C * score));
	}

	public final double getScore() {
		return score.doubleValue();
	}

	public final Double getScoreAsDouble() {
		return score;
	}

	public final double getTScore() {
		if (tscore >= 1)
			return tscore;
		if (isOverloaded() != 0)
			return 0;
		else
			return 1;
	}

	public final BoundContact getHost() {
		return host;
	}

	public int getLoad() {
		return load;
	}

	public void setLoad(int load) {
		this.load = load;
	}

	public synchronized void changeLoad(int dl) {
		load += dl;
		if (load < 0) {
			load = 0;
		}
	}

	public boolean equals(Object obj) {
		if (obj instanceof WeightedHost) {
			WeightedHost wh = (WeightedHost) obj;
			return host.equals(wh.host);
		}
		return false;
	}

	public int hashCode() {
		return host.hashCode();
	}

	public static final NumberFormat D4;
	static {
		D4 = DecimalFormat.getInstance();
		D4.setMaximumFractionDigits(3);
		D4.setMinimumFractionDigits(3);
	}

	public String toString() {
		return host.toString() + ":" + D4.format(score) + "(" + D4.format(tscore) + "):" + load
				+ "/" + (int) (maxLoad()) + " overload: " + isOverloaded();
	}

	public int compareTo(Object o) {
		WeightedHost other = (WeightedHost) o;
		int r = score.compareTo(other.score);
		if (r == 0) {
			// arbitrary ordering on the contact
			return System.identityHashCode(host) - System.identityHashCode(other.host);
		}
		else {
			return r;
		}
	}

	public double getDelayedDelta() {
		return delayedDelta;
	}

	public void setDelayedDelta(double delayedDelta) {
		this.delayedDelta = delayedDelta;
	}

	public int isOverloaded() {
		double ml = maxLoad();
		if (tscore >= 1) {
			// the site is mostly good. permit 1 or more jobs
			// always.
			if (logger.isDebugEnabled()) {
				logger.debug("In load mode. score = " + score + " tscore = " + tscore + ", maxload="
						+ ml);
			}
			return load <= ml ? 0 : 1;
		}
		else {
			// the site is mostly bad. allow either 1 or 0 jobs
			// based on time.
			long now = System.currentTimeMillis();
			long delay = now - lastUsed;
			long permittedDelay = (long) (Math.exp(-(score.doubleValue())) * 100.0);
			boolean overloaded = (delay < permittedDelay);
			int d = (int) (delay - permittedDelay);
			// tscore of -1 will give delay of around
			// 200ms, and will double every time tscore goes
			// down by one (which is once per failed job? roughly?)
			if (logger.isDebugEnabled()) {
				logger.debug("In delay mode. score = " + score + " tscore = " + tscore
						+ ", maxload=" + ml + " delay since last used=" + delay + "ms"
						+ " permitted delay=" + permittedDelay + "ms overloaded=" + overloaded+ " delay-permitted delay="+d);
			}
			if (overloaded) {
				return (int) (delay - permittedDelay);
			}
			else {
				return load < ml ? 0 : 1;
			}
		}
	}

	public float getJobThrottle() {
		return jobThrottle;
	}

	public double maxLoad() {
		return jobThrottle * tscore + 1;
	}

	public void notifyUsed() {
		lastUsed = System.currentTimeMillis();
	}
}
