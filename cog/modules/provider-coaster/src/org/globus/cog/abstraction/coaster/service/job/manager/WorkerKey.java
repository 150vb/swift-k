//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Mar 12, 2008
 */
package org.globus.cog.abstraction.coaster.service.job.manager;

public class WorkerKey implements Comparable {
    private Worker worker;
    private long time;
    
    public WorkerKey(Worker worker) {
        this.worker = worker;
    }
    
    public WorkerKey(long time) {
        this.time = time;
    }

    public int compareTo(Object o) {
        WorkerKey wk = (WorkerKey) o;

        if (worker == null) {
            if (wk.worker == null) {
                return sgn(time - wk.time);
            }
            else {
                return sgn(time
                        - wk.worker.getScheduledTerminationTime().longValue());
            }
        }
        else {
            if (wk.worker == null) {
                return sgn(worker.getScheduledTerminationTime().longValue()
                        - wk.time);
            }
            else {
                if (worker == wk.worker) {
                    return 0;
                }
                else {
                    int dif = sgn(worker.getScheduledTerminationTime()
                            .longValue()
                            - wk.worker.getScheduledTerminationTime()
                                    .longValue());
                    if (dif != 0) {
                        return dif;
                    }
                    else {
                        return System.identityHashCode(worker)
                                - System.identityHashCode(wk.worker);
                    }
                }
            }
        }
    }

    private int sgn(long val) {
        if (val < 0) {
            return -1;
        }
        else if (val > 0) {
            return 1;
        }
        else {
            return 0;
        }
    }

}
