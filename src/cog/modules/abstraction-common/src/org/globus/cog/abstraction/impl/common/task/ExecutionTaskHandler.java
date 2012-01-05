// ----------------------------------------------------------------------
// This code is developed as part of the Java CoG Kit project
// The terms of the license can be found at http://www.cogkit.org/license
// This message may not be removed or altered.
// ----------------------------------------------------------------------

package org.globus.cog.abstraction.impl.common.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.impl.common.AbstractionFactory;
import org.globus.cog.abstraction.impl.common.ProviderMethodException;
import org.globus.cog.abstraction.impl.common.StatusImpl;
import org.globus.cog.abstraction.impl.common.TaskHandlerSkeleton;
import org.globus.cog.abstraction.interfaces.Service;
import org.globus.cog.abstraction.interfaces.Status;
import org.globus.cog.abstraction.interfaces.Task;
import org.globus.cog.abstraction.interfaces.TaskHandler;

public class ExecutionTaskHandler extends TaskHandlerSkeleton {
    
    private Map<String, TaskHandler> mapping;
    Logger logger = Logger.getLogger(ExecutionTaskHandler.class);
    
    public ExecutionTaskHandler() {
        mapping = new HashMap<String, TaskHandler>();
        setType(TaskHandler.EXECUTION);
    }

    public void submit(Task task)
        throws
            IllegalSpecException,
            InvalidSecurityContextException,
            InvalidServiceContactException,
            TaskSubmissionException {
        if (task.getType() != Task.JOB_SUBMISSION) {
            throw new TaskSubmissionException
                ("Execution handler can only handle job submission tasks");
        }
        String provider = task.getService(0).getProvider().toLowerCase();
        logger.info("provider=" + provider);
        TaskHandler taskHandler = mapping.get(provider);

        if (taskHandler == null) {
            try {
                taskHandler = createTaskHandler(provider);
            } catch (InvalidProviderException ipe) {
                throw new TaskSubmissionException("Cannot submit task", ipe);
            }
        }

        logger.debug("taskHandler="+taskHandler);
        taskHandler.submit(task);
    }

    public void suspend(Task task)
        throws InvalidSecurityContextException, TaskSubmissionException {
        if (task.getType() != Task.JOB_SUBMISSION) {
            throw new TaskSubmissionException("Execution handler can only handle job submission tasks");
        }
        String provider = task.getService(Service.DEFAULT_SERVICE).getProvider().toLowerCase();
        TaskHandler taskHandler = this.mapping.get(provider);
        if (taskHandler != null) {
            taskHandler.suspend(task);
        } else {
            throw new TaskSubmissionException(
                "Provider " + provider + " unknown");
        }
    }

    public void resume(Task task)
        throws InvalidSecurityContextException, TaskSubmissionException {
        if (task.getType() != Task.JOB_SUBMISSION) {
            throw new TaskSubmissionException("Execution handler can only handle job submission tasks");
        }
        String provider = task.getService(Service.DEFAULT_SERVICE).getProvider().toLowerCase();
        TaskHandler taskHandler = this.mapping.get(provider);
        if (taskHandler != null) {
            taskHandler.resume(task);
        } else {
            throw new TaskSubmissionException(
                "Provider " + provider + " unknown");
        }
    }
    
    public void cancel(Task task) throws InvalidSecurityContextException, TaskSubmissionException {
        cancel(task, null);
    }

    public void cancel(Task task, String message)
        throws InvalidSecurityContextException, TaskSubmissionException {
        if (task.getType() != Task.JOB_SUBMISSION) {
            throw new TaskSubmissionException("Execution handler can only handle job submission tasks");
        }
        String provider = task.getService(Service.DEFAULT_SERVICE).getProvider().toLowerCase();
        TaskHandler taskHandler = this.mapping.get(provider);
        if (taskHandler != null) {
            taskHandler.cancel(task, message);
        } else {
            task.setStatus(new StatusImpl(Status.CANCELED, message, null));
        }
    }

    public void remove(Task task) throws ActiveTaskException {
        String provider = task.getService(Service.DEFAULT_SERVICE).getProvider().toLowerCase();
        TaskHandler taskHandler = this.mapping.get(provider);
        if (taskHandler != null) {
            taskHandler.remove(task);
        }
    }
    
    public Collection<Task> getAllTasks() {
        ArrayList<Task> l = new ArrayList<Task>();
        synchronized(mapping) {
            for (TaskHandler th : mapping.values()) {
                l.addAll(th.getAllTasks());
            }
        }
        return l;
    }
    
    protected Collection<Task> getTasksWithStatus(int code) {
        ArrayList<Task> l = new ArrayList<Task>();
        synchronized(mapping) {
            for (TaskHandler th : mapping.values()) {
                for (Task t : th.getAllTasks()) {
                    if (t.getStatus().getStatusCode() == code) {
                        l.add(t);
                    }
                }
            }
        }
        return l;
    }

    private TaskHandler createTaskHandler(String provider)
        throws InvalidProviderException {
        TaskHandler taskHandler;
        try {
            taskHandler = AbstractionFactory.newExecutionTaskHandler(provider);
        } catch (ProviderMethodException e) {
            throw new InvalidProviderException(
                "Cannot create new task handler for provider " + provider,
                e);
        }
        this.mapping.put(provider, taskHandler);
        return taskHandler;
    }
    
    /** return a collection of active tasks */
    public Collection<Task> getActiveTasks() {
        return getTasksWithStatus(Status.ACTIVE);
    }

    /** return a collection of failed tasks */
    public Collection<Task> getFailedTasks() {
        return getTasksWithStatus(Status.FAILED);
    }

    /** return a collection of completed tasks */
    public Collection<Task> getCompletedTasks() {
        return getTasksWithStatus(Status.COMPLETED);
    }

    /** return a collection of suspended tasks */
    public Collection<Task> getSuspendedTasks() {
        return getTasksWithStatus(Status.SUSPENDED);
    }

    /** return a collection of resumed tasks */
    public Collection<Task> getResumedTasks() {
        return getTasksWithStatus(Status.RESUMED);
    }

    /** return a collection of canceled tasks */
    public Collection<Task> getCanceledTasks() {
        return getTasksWithStatus(Status.CANCELED);
    }
    
    public String toString() {
        return "ExecutionTaskHandler"; 
    }
}
