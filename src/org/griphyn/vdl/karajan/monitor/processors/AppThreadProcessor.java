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
 * Created on Jan 29, 2007
 */
package org.griphyn.vdl.karajan.monitor.processors;

import org.apache.log4j.Level;
import org.griphyn.vdl.karajan.monitor.SystemState;
import org.griphyn.vdl.karajan.monitor.items.ApplicationItem;
import org.griphyn.vdl.karajan.monitor.items.Bridge;
import org.griphyn.vdl.karajan.monitor.items.StatefulItem;
import org.griphyn.vdl.karajan.monitor.items.StatefulItemClass;

public class AppThreadProcessor extends AbstractSwiftProcessor {

	public Level getSupportedLevel() {
		return Level.DEBUG;
	}

	public String getMessageHeader() {
		return "THREAD_ASSOCIATION";
	}

	public void processMessage(SystemState state, SimpleParser p, Object details) {
	    try {
			String appid, threadid, replicationgroup;
		    p.skip("jobid=");
			appid = p.word();
			p.skip("thread=");
			threadid = p.word();
			p.skip("replicationGroup=");
			replicationgroup = p.word();
				
			StatefulItem app = state.getItemByID(appid, StatefulItemClass.APPLICATION);
			if (app == null) {
				app = new ApplicationItem(appid);
				state.addItem(app);
			}
				
			StatefulItem thread = state.getItemByID(threadid, StatefulItemClass.BRIDGE);
			if (thread == null) {
				thread = new Bridge(threadid);
				thread.setParent(app);
				state.addItem(thread);
				app.addChild(thread);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}
