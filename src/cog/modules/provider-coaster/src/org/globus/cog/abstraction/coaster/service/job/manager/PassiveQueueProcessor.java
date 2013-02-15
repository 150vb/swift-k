//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Apr 5, 2010
 */
package org.globus.cog.abstraction.coaster.service.job.manager;

import java.net.URI;
import java.util.Map;

import org.globus.cog.abstraction.coaster.rlog.RemoteLogCommand;
import org.globus.cog.abstraction.coaster.service.ResourceUpdateCommand;
import org.globus.cog.karajan.workflow.service.channels.ChannelContext;
import org.globus.cog.karajan.workflow.service.channels.ChannelManager;
import org.globus.cog.karajan.workflow.service.channels.KarajanChannel;

public class PassiveQueueProcessor extends BlockQueueProcessor {
    private final URI callbackURI;
    
    private int currentWorkers;

    public PassiveQueueProcessor(Settings settings, URI callbackURI) {
        super(settings);
        setName("Passive Queue Processor");
        setSettings(settings);
        this.callbackURI = callbackURI;
    }

    @Override
    public void setClientChannelContext(ChannelContext channelContext) {
        super.setClientChannelContext(channelContext);
        KarajanChannel channel;
        try {
            channel = ChannelManager.getManager().reserveChannel(channelContext);
            RemoteLogCommand cmd = new RemoteLogCommand(RemoteLogCommand.Type.STDERR,
                "Passive queue processor initialized. Callback URI is " + callbackURI);
            cmd.executeAsync(channel, null);
            ChannelManager.getManager().releaseChannel(channel);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int updatePlan() throws PlanningException {
        return 1;
    }

    @Override
    protected void removeIdleBlocks() {
        // no removing of idle blocks here
    }
    
    @Override
    public String registrationReceived(String blockID, String workerID, String workerHostname,
            ChannelContext channelContext, Map<String, String> options) {
        
        String r = getBlock(blockID).workerStarted(workerID, workerHostname, channelContext);
        
        if (clientIsConnected()) {
            ResourceUpdateCommand wsc;
            synchronized(this) {
                currentWorkers++;
                wsc = new ResourceUpdateCommand("job-capacity", 
                    String.valueOf(currentWorkers * getSettings().getJobsPerNode()));
            }
            try {
                KarajanChannel channel = ChannelManager.getManager().reserveChannel(getClientChannelContext());
                wsc.executeAsync(channel);
                ChannelManager.getManager().releaseChannel(channel);
            }
            catch (Exception e) {
                logger.info("Failed to send worker status update to client", e);
            }
        }
        
        return r;
    }

    @Override
    protected Block getBlock(String id) {
        Map<String, Block> blocks = getBlocks();
        Block b;
        synchronized(blocks) {
            b = blocks.get(id);
            if (b == null) {
                b = new Block(id, 1, TimeInterval.FOREVER, this);
                b.setRunning(true);
                blocks.put(id, b);
            }
        }
        return b;
    }

    @Override
    public void nodeRemoved(Node node) {
        ResourceUpdateCommand wsc;
        synchronized(this) {
            currentWorkers--;
            wsc = new ResourceUpdateCommand("job-capacity", 
                String.valueOf(currentWorkers * getSettings().getJobsPerNode()));
        }
        try {
            KarajanChannel channel = ChannelManager.getManager().reserveChannel(getClientChannelContext());
            wsc.executeAsync(channel);
            ChannelManager.getManager().releaseChannel(channel);
        }
        catch (Exception e) {
            logger.warn("Failed to send worker status update to client", e);
        }
    }
}
