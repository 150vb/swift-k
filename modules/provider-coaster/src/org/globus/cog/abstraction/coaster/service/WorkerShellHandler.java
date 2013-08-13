//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Jul 21, 2005
 */
package org.globus.cog.abstraction.coaster.service;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.coaster.service.job.manager.BlockQueueProcessor;
import org.globus.cog.abstraction.impl.execution.coaster.WorkerShellCommand;
import org.globus.cog.coaster.ProtocolException;
import org.globus.cog.coaster.channels.ChannelException;
import org.globus.cog.coaster.channels.ChannelManager;
import org.globus.cog.coaster.channels.CoasterChannel;
import org.globus.cog.coaster.commands.Command;
import org.globus.cog.coaster.commands.Command.Callback;
import org.globus.cog.coaster.handlers.RequestHandler;

public class WorkerShellHandler extends RequestHandler implements Callback {
    public static final Logger logger = Logger.getLogger(WorkerShellHandler.class);
    
    public static final String NAME = WorkerShellCommand.NAME;

    public void requestComplete() throws ProtocolException {
        String workerId = getInDataAsString(0);
        String command = getInDataAsString(1);
        WorkerShellCommand wsc = new WorkerShellCommand(workerId, command);
        BlockQueueProcessor bqp = (BlockQueueProcessor) ((CoasterService) getChannel().getChannelContext().
                getService()).getJobQueue().getCoasterQueueProcessor();
        try {
            ChannelManager manager = ChannelManager.getManager();
            CoasterChannel worker = bqp.getWorkerChannel(workerId);
            if (worker == null) {
                sendReply("Error: worker not found");
            }
            else {
                CoasterChannel reserved = manager.reserveChannel(worker);
                wsc.executeAsync(reserved, this);
            }
        }
        catch (ChannelException e) {
            sendError("Cannot contact worker", e);
        }
    }

    public void errorReceived(Command cmd, String msg, Exception t) {
        try {
            sendError("Worker error: " + msg, t);
        }
        catch (ProtocolException e) {
            logger.error("Cannot send reply: " + msg, e);
        }
    }

    public void replyReceived(Command cmd) {
        try {
            sendReply(cmd.getInDataAsString(1));
        }
        catch (ProtocolException e) {
            logger.error("Cannot send reply", e);
        }
    }
}
