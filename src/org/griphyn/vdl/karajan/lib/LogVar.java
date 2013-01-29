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
 * Created on Dec 26, 2006
 */
package org.griphyn.vdl.karajan.lib;

import k.rt.Channel;
import k.rt.Stack;

import org.globus.cog.karajan.analyzer.ArgRef;
import org.globus.cog.karajan.analyzer.ChannelRef;
import org.globus.cog.karajan.analyzer.Signature;
import org.globus.cog.karajan.analyzer.VariableNotFoundException;
import org.globus.cog.karajan.util.TypeUtil;
import org.griphyn.vdl.mapping.DSHandle;
import org.griphyn.vdl.mapping.MappingParam;
import org.griphyn.vdl.mapping.Path;

public class LogVar extends SwiftFunction {
    private ArgRef<DSHandle> var;
    private ArgRef<Object> path;
    private ChannelRef<Object> cr_restartlog;
    
	@Override
    protected Signature getSignature() {
        return new Signature(params("var", "path"), returns(channel("restartlog", 1)));
    }

    @Override
	public Object function(Stack stack) {
		DSHandle var = this.var.getValue(stack);
		Path path;
        Object p = this.path.getValue(stack);
        if (p instanceof Path) {
            path = (Path) p;
        }
        else {
            path = Path.parse(TypeUtil.toString(p));
        }
        logVar(cr_restartlog.get(stack), var, path);
		return null;
	}
	
	public static void logVar(Channel<Object> log, DSHandle var, Path path) throws VariableNotFoundException {
	    path = var.getPathFromRoot().append(path);
        String annotation;
        if(var.getMapper() != null) {
            annotation = "" + var.getMapper().map(path);
        } else {
            annotation = "unmapped";
        }
        log.add(var.getRoot().getParam(MappingParam.SWIFT_RESTARTID)
                + "." + path.stringForm() + "!" + annotation);
	}
}
