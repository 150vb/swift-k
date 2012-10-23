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
 * Created on Jun 17, 2006
 */
package org.griphyn.vdl.karajan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.globus.cog.karajan.stack.VariableStack;
import org.griphyn.vdl.mapping.DSHandle;

public class WaitingThreadsMonitor {
	private static Map<VariableStack, DSHandle> threads = new HashMap<VariableStack, DSHandle>();
	private static Map<VariableStack, List<DSHandle>> outputs = new HashMap<VariableStack, List<DSHandle>>();;
	
	public static void addThread(VariableStack stack, DSHandle waitingOn) {
	    if (stack != null) {
	        synchronized(threads) {
	            threads.put(stack, waitingOn);
	        }
	    }
	}
		
	public static void removeThread(VariableStack stack) {
	    synchronized(threads) {
	        threads.remove(stack);
	    }
	}
	
	public static Map<VariableStack, DSHandle> getAllThreads() {
	    synchronized(threads) {
	        return new HashMap<VariableStack, DSHandle>(threads);
	    }
	}

    public static void addOutput(VariableStack stack, List<DSHandle> outputs) {
        synchronized(WaitingThreadsMonitor.outputs) {
            WaitingThreadsMonitor.outputs.put(stack, outputs);
        }
    }

    public static void removeOutput(VariableStack stack) {
        synchronized(outputs) {
            outputs.remove(stack);
        }
    }
    
    public static Map<VariableStack, List<DSHandle>> getOutputs() {
        synchronized(outputs) {
            return new HashMap<VariableStack, List<DSHandle>>(outputs);
        }
    }
}
