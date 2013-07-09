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
 * Created on Jul 18, 2010
 */
package org.griphyn.vdl.karajan.lib;

import java.util.Collection;
import java.util.List;

import k.rt.Channel;
import k.rt.ExecutionException;
import k.rt.Stack;
import k.thr.LWThread;

import org.globus.cog.karajan.analyzer.ArgRef;
import org.globus.cog.karajan.analyzer.ChannelRef;
import org.globus.cog.karajan.analyzer.Signature;
import org.globus.cog.karajan.compiled.nodes.InternalFunction;
import org.griphyn.vdl.mapping.DSHandle;
import org.griphyn.vdl.mapping.Path;

public class DoRestartLog extends InternalFunction {
    
    private ArgRef<List<List<Object>>> restartouts;
    private ChannelRef<Object> cr_vargs;
    private ChannelRef<Object> cr_restartLog;
   
    @Override
    protected Signature getSignature() {
        return new Signature(params("restartouts"), returns(channel("...", DYNAMIC), channel("restartLog", DYNAMIC)));
    }

    @Override
    protected void runBody(LWThread thr) {
        Stack stack = thr.getStack();
        Collection<List<Object>> files = restartouts.getValue(stack);
        Channel<Object> ret = cr_vargs.get(stack);
        Channel<Object> log = cr_restartLog.get(stack);
        try {
            for (List<Object> pv : files) {
                Path p = (Path) pv.get(0);
                DSHandle handle = (DSHandle) pv.get(1);
                LogVar.logVar(log, handle, p);
            }
        }
        catch (Exception e) {
            throw new ExecutionException(this, e);
        }
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
