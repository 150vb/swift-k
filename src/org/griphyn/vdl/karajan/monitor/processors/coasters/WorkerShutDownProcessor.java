//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Aug 7, 2013
 */
package org.griphyn.vdl.karajan.monitor.processors.coasters;

import org.griphyn.vdl.karajan.monitor.SystemState;
import org.griphyn.vdl.karajan.monitor.items.StatefulItemClass;
import org.griphyn.vdl.karajan.monitor.processors.SimpleParser;

public class WorkerShutDownProcessor extends AbstractRemoteLogProcessor {
    private CoasterStatusItem item;
    
    @Override
    public void initialize(SystemState state) {
        super.initialize(state);
    }

    @Override
    public String getMessageHeader() {
        return "WORKER_SHUTDOWN";
    }

    @Override
    public void processMessage(SystemState state, SimpleParser p, Object details) {
        try {
            p.skip("blockid=");
            String blockId = p.word();
            
            CoasterStatusItem item = (CoasterStatusItem) state.getItemByID(CoasterStatusItem.ID, StatefulItemClass.MISC);
            item.workerShutDown(blockId);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
