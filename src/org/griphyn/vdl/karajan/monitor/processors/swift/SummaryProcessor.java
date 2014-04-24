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


/*
 * Created on Aug 28, 2008
 */
package org.griphyn.vdl.karajan.monitor.processors.swift;

import org.apache.log4j.Level;
import org.griphyn.vdl.karajan.lib.RuntimeStats.ProgressTicker;
import org.griphyn.vdl.karajan.monitor.SystemState;
import org.griphyn.vdl.karajan.monitor.items.ApplicationState;
import org.griphyn.vdl.karajan.monitor.items.StatefulItemClass;
import org.griphyn.vdl.karajan.monitor.items.SummaryItem;
import org.griphyn.vdl.karajan.monitor.processors.AbstractMessageProcessor;

public class SummaryProcessor extends AbstractMessageProcessor {
    
	public Level getSupportedLevel() {
		return Level.INFO;
	}

	public Class<?> getSupportedSource() {
		return ProgressTicker.class;
	}

	public void processMessage(SystemState state, Object message, Object details) {
		String msg = String.valueOf(message);
		if(msg.contains("CrtHeap")) {
		   return;
		}
		SummaryItem s = (SummaryItem) state.getItemByID(SummaryItem.ID, StatefulItemClass.WORKFLOW);
		String[] pairs = msg.split("  ");
		for (ApplicationState key : SummaryItem.STATES) {
		    s.setCount(key.getName(), 0);
		}
		for (String pair : pairs) {
		    if (pair.equals("")) {
		        continue;
		    }
		    String[] v = pair.split(":");
		    String key = v[0].trim();
		    int value = Integer.parseInt(v[1]);
		    if (key.equals(ApplicationState.FINISHED_IN_PREVIOUS_RUN.getName())) {
		        state.setCompletedPreviously(value);
		    }
		    s.setCount(key, value);
		}
		state.itemUpdated(s);
	}
}
