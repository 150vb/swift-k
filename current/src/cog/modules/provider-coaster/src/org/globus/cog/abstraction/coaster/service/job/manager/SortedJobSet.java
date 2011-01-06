//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Apr 21, 2009
 */
package org.globus.cog.abstraction.coaster.service.job.manager;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.log4j.Logger;


public class SortedJobSet implements Iterable<Job> {
    public static final Logger logger = Logger.getLogger(SortedJobSet.class);
    
    private SortedMap<TimeInterval, LinkedList<Job>> sm;
    int size;
    double jsize;
    /** 
       Monotonically increasing job sequence number
     */
    int seq;
    private Metric metric;
    
    public SortedJobSet() {
        this(Metric.NULL_METRIC);
    }
    
    public SortedJobSet(Metric metric) {
        sm = new TreeMap<TimeInterval, LinkedList<Job>>();
        size = 0;
        this.metric = metric;
    }
    
    public SortedJobSet(SortedJobSet other) {
        metric = other.metric;
        synchronized(other) {
            sm = new TreeMap<TimeInterval, LinkedList<Job>>(other.sm);
            jsize = other.jsize;
            size = other.size;
        }
    }

    public int size() {
        return size;
    }

    public synchronized void add(Job j) {
        LinkedList<Job> l = sm.get(j.getMaxWallTime());
        if (l == null) {
            l = new LinkedList<Job>();
            sm.put(j.getMaxWallTime(), l);
        }
        l.add(j);
        jsize += metric.getSize(j);
        size++;
        seq++;
    }

    /**
       Remove and return largest job with a walltime smaller than the 
       given walltime and less than or equal to the given cpu
       Could be cleaned up using Java 1.6 functionality
     */
    public synchronized Job removeOne(TimeInterval walltime,
                                      int cpus) {
        Job result = null;
        SortedMap<TimeInterval, LinkedList<Job>> smaller = 
            sm.headMap(walltime);
        
        while (! smaller.isEmpty()) {
            TimeInterval key = smaller.lastKey();
            List<Job> jobs = smaller.get(key);
            result = removeOne(key, jobs, cpus);
            if (result != null) {
                jsize -= metric.getSize(result);
                if (--size == 0) jsize = 0;
                return result;
            }
            smaller = smaller.headMap(key);
        }
        return null;
    }

    Job removeOne(TimeInterval key, List<Job> jobs, int cpus)
    {
        Job result = null;
        for (Iterator<Job> it = jobs.iterator(); it.hasNext(); ) {
            Job job = it.next();
            if (job.cpus <= cpus) {
                result = job;
                it.remove();
                break;
            }
        }
        if (jobs.isEmpty())
            sm.remove(key);
        return result;
    }
    
    public double getJSize() {
        return jsize;
    }

    public Iterator<Job> iterator() {
        return new Iterator<Job>() {
            private Iterator<LinkedList<Job>> it1 = sm.values().iterator();
            private Iterator<Job> it2 = it1.hasNext() ? it1.next().iterator() : null;

            public boolean hasNext() {
                return it2 != null && (it2.hasNext() || it1.hasNext());
            }

            public Job next() {
                if (it2.hasNext()) {
                    return it2.next();
                }
                else if (it1.hasNext()) {
                    it2 = it1.next().iterator();
                    return it2.next();
                }
                else {
                    throw new NoSuchElementException();
                }
            }

            public void remove() {
            }
        };
    }

    public synchronized int getSeq() {
        return seq;
    }
    
    public String toString() {
        return sm.toString();
    }
}