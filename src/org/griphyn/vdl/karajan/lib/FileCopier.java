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


package org.griphyn.vdl.karajan.lib;

import java.util.LinkedList;
import java.util.List;

import org.globus.cog.abstraction.impl.common.StatusEvent;
import org.globus.cog.abstraction.impl.common.task.FileTransferSpecificationImpl;
import org.globus.cog.abstraction.impl.common.task.FileTransferTask;
import org.globus.cog.abstraction.impl.common.task.FileTransferTaskHandler;
import org.globus.cog.abstraction.impl.common.task.IllegalSpecException;
import org.globus.cog.abstraction.impl.common.task.InvalidSecurityContextException;
import org.globus.cog.abstraction.impl.common.task.InvalidServiceContactException;
import org.globus.cog.abstraction.impl.common.task.ServiceContactImpl;
import org.globus.cog.abstraction.impl.common.task.ServiceImpl;
import org.globus.cog.abstraction.impl.common.task.TaskSubmissionException;
import org.globus.cog.abstraction.interfaces.FileTransferSpecification;
import org.globus.cog.abstraction.interfaces.Service;
import org.globus.cog.abstraction.interfaces.Status;
import org.globus.cog.abstraction.interfaces.StatusListener;
import org.globus.cog.abstraction.interfaces.TaskHandler;
import org.globus.cog.karajan.stack.VariableStack;
import org.globus.cog.karajan.workflow.ExecutionException;
import org.globus.cog.karajan.workflow.futures.Future;
import org.globus.cog.karajan.workflow.futures.FutureEvaluationException;
import org.globus.cog.karajan.workflow.futures.FutureListener;
import org.globus.cog.karajan.workflow.futures.FuturesMonitor;
import org.globus.cog.karajan.workflow.futures.ListenerStackPair;
import org.griphyn.vdl.mapping.AbsFile;
import org.griphyn.vdl.mapping.PhysicalFormat;

public class FileCopier implements Future, StatusListener {
    private static final TaskHandler fth = new FileTransferTaskHandler();

    private FileTransferTask task;
    private List<ListenerStackPair> actions;
    private Exception exception;
    private boolean closed;

    public FileCopier(PhysicalFormat src, PhysicalFormat dst) {
        AbsFile fsrc = (AbsFile) src;
        AbsFile fdst = (AbsFile) dst;
        FileTransferSpecification fts = new FileTransferSpecificationImpl();
        fts.setDestinationDirectory(fdst.getDirectory());
        fts.setDestinationFile(fdst.getName());
        fts.setSourceDirectory(fsrc.getDirectory());
        fts.setSourceFile(fsrc.getName());
        fts.setThirdPartyIfPossible(true);
        task = new FileTransferTask();
        task.setSpecification(fts);
        task.setService(Service.FILE_TRANSFER_SOURCE_SERVICE, new ServiceImpl(
            fsrc.getProtocol(), new ServiceContactImpl(fsrc.getHost()), null));
        task.setService(Service.FILE_TRANSFER_DESTINATION_SERVICE,
            new ServiceImpl(fdst.getProtocol(), new ServiceContactImpl(fdst
                .getHost()), null));
        task.addStatusListener(this);
    }

    public synchronized void addModificationAction(FutureListener target,
            VariableStack stack) {
        if (actions == null) {
            actions = new LinkedList<ListenerStackPair>();
        }
        ListenerStackPair etp = new ListenerStackPair(target, stack);
        if (FuturesMonitor.debug) {
            FuturesMonitor.monitor.add(etp, this);
        }
        synchronized (actions) {
            actions.add(etp);
        }
        if (closed) {
            actions();
        }
    }

    public List<ListenerStackPair> getModificationActions() {
        return actions;
    }

    private void actions() {
        if (actions != null) {
            synchronized (actions) {
                java.util.Iterator<ListenerStackPair> i = actions.iterator();
                while (i.hasNext()) {
                    ListenerStackPair etp = i.next();
                    if (FuturesMonitor.debug) {
                        FuturesMonitor.monitor.remove(etp);
                    }
                    i.remove();
                    etp.listener.futureModified(this, etp.stack);
                }
            }
        }
    }

    public void fail(FutureEvaluationException e) {
        this.exception = e;
        actions();
    }

    public Object getValue() {
        return null;
    }

    public boolean isClosed() {
        return closed;
    }

    public void start() throws IllegalSpecException,
            InvalidSecurityContextException, InvalidServiceContactException,
            TaskSubmissionException {
        fth.submit(task);
    }

    public void close() {
        closed = true;
        actions();
    }

    public void statusChanged(StatusEvent event) {
        Status s = event.getStatus();
        if (s.isTerminal()) {
            if (s.getStatusCode() == Status.COMPLETED) {
                close();
            }
            else {
                this.exception = new Exception(s.getMessage(), s.getException());
                close();
            }
        }
    }
    
    public Exception getException() {
        return exception;
    }
}
