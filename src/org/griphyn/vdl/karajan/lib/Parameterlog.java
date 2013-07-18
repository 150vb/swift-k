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

import k.rt.Stack;
import k.thr.LWThread;

import org.apache.log4j.Logger;
import org.globus.cog.karajan.analyzer.ArgRef;
import org.globus.cog.karajan.analyzer.CompilationException;
import org.globus.cog.karajan.analyzer.Scope;
import org.globus.cog.karajan.analyzer.Signature;
import org.globus.cog.karajan.analyzer.VarRef;
import org.globus.cog.karajan.compiled.nodes.Node;
import org.globus.cog.karajan.compiled.nodes.InternalFunction;
import org.globus.cog.karajan.parser.WrapperNode;
import org.griphyn.vdl.karajan.functions.ConfigProperty;
import org.griphyn.vdl.util.VDL2Config;

public class Parameterlog extends InternalFunction {
    public static final Logger logger = Logger.getLogger(Parameterlog.class);
    
    private ArgRef<String> direction;
    private ArgRef<String> variable;
    private ArgRef<String> id;
    
    @Override
    protected Signature getSignature() {
        return new Signature(params("direction", "variable", "id"));
    }

    private Boolean enabled;
    private VarRef<VDL2Config> config;
    
    @Override
    protected Node compileBody(WrapperNode w, Scope argScope, Scope scope)
            throws CompilationException {
        config = scope.getVarRef("SWIFT_CONFIG");
        return super.compileBody(w, argScope, scope);
    }



    @Override
    protected void runBody(LWThread thr) {
        Stack stack = thr.getStack();
        boolean run;
        synchronized(this) {
            if (enabled == null) {
                enabled = "true".equals(ConfigProperty.getProperty("provenance.log", true, config.getValue(stack)));
            }
            run = enabled;
        }
        if (run) {
            super.run(thr);
            logger.info("PARAM thread=" + SwiftFunction.getThreadPrefix(thr) + " direction="
                    + direction.getValue(stack) + " variable=" + variable.getValue(stack)
                    + " provenanceid=" + id.getValue(stack));
        }
    }
}
