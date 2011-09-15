//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Apr 21, 2009
 */
package org.globus.cog.abstraction.coaster.service.job.manager;

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.impl.common.StatusImpl;
import org.globus.cog.abstraction.impl.common.execution.WallTime;
import org.globus.cog.abstraction.interfaces.JobSpecification;
import org.globus.cog.abstraction.interfaces.Status;
import org.globus.cog.abstraction.interfaces.Task;

public class Job implements Comparable<Job> {
    public static final Logger logger = Logger.getLogger(Job.class);

    public static final Set<String> SUPPORTED_ATTRIBUTES;

    static {
    	SUPPORTED_ATTRIBUTES = new HashSet<String>();
    	SUPPORTED_ATTRIBUTES.add("mpi.processes");
    	SUPPORTED_ATTRIBUTES.add("mpi.ppn");
    	SUPPORTED_ATTRIBUTES.add("maxwalltime");
    }

    private int id;
    private Task task;

    /**
       If not 1, number of MPI processes required (i.e., mpiexec -n)
       Set by JobSpecification attribute "mpi.processes"
     */
    int mpiProcesses;

    /**
       If not 1, number of MPI processes required (i.e., mpiexec -n)
       Set by JobSpecification attribute "mpi.processes"
     */
    int mpiPPN;

    private TimeInterval walltime;
    private Time starttime, endtime;
    private boolean done;

    private static int sid;

    public Job() {
    	synchronized (Job.class) {
    		id = sid++;
    	}
    }

    public Job(Task task) {
    	this();
        setTask(task);
    }

	public Job(Task task, int cpus) {
		this();
        setTask(task);
        this.mpiProcesses = cpus;
    }

    void setTask(Task task) {
        this.task = task;

        JobSpecification spec =
            (JobSpecification) task.getSpecification();
        logger.info("Coasters Job id=" + id + " is:\n" + spec);

        // Set walltime
        Object tmp = spec.getAttribute("maxwalltime");
        if (tmp == null) {
            this.walltime = TimeInterval.fromSeconds(10 * 60);
        }
        else {
            WallTime wt = new WallTime(tmp.toString());
            this.walltime = TimeInterval.fromSeconds(wt.getSeconds());
        }

        // MPI settings
        tmp = spec.getAttribute("mpi.processes");
        if (tmp == null)
            mpiProcesses = 1;
        else {
            mpiProcesses = Integer.parseInt((String) tmp);
            tmp = spec.getAttribute("mpi.ppn");
            if (tmp == null)
            	mpiPPN = 1;
            else
            	mpiPPN = Integer.parseInt((String) tmp);
            logger.info("MPI Job: id=" + id +
                         " mpi.processes=" + mpiProcesses +
                         " mpi.ppn=" + mpiPPN);
        }
    }

    public Time getEndTime() {
        if (endtime == null) {
            if (starttime == null) {
                return null;
            }
            else {
                return starttime.add(walltime);
            }
        }
        else {
            return endtime;
        }
    }

    public void setEndTime(Time endtime) {
        this.endtime = endtime;
    }

    public Time getStartTime() {
        return starttime;
    }

    public void setStartTime(Time t) {
        this.starttime = t;
    }

    public boolean isDone() {
        return done;
    }

    public int compareTo(Job other) {
        // Job other = (Job) obj;
        TimeInterval diff = walltime.subtract(other.walltime);
        if (diff.getMilliseconds() == 0) {
            return id - other.id;
        }
        else {
            return (int) diff.getMilliseconds();
        }
    }

    public TimeInterval getMaxWallTime() {
        return walltime;
    }

    public void setMaxWallTime(TimeInterval walltime) {
        this.walltime = walltime;
    }

    @Override
	public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("Job(id:");
        sb.append(id);
        sb.append(" ");
        sb.append(walltime);
        if (mpiProcesses != 1) {
            sb.append(" [");
            sb.append(mpiProcesses);
            sb.append('/');
            sb.append(mpiPPN);
            sb.append("]");
        }
        sb.append(")");
        return sb.toString();
    }

    public void start() {
        starttime = Time.now();
        endtime = Time.now().add(walltime);
        if (logger.isDebugEnabled())
            logger.debug(this.toString() + " start: " +
                         starttime.getSeconds() + "-" +
                         endtime.getSeconds());
    }

    public void fail(String message, Exception e) {
        task.setStatus(new StatusImpl(Status.FAILED, message, e));
    }

    public Task getTask() {
        return task;
    }

    public int getCpus() {
        return mpiProcesses;
    }
}
