// ----------------------------------------------------------------------
// This code is developed as part of the Java CoG Kit project
// The terms of the license can be found at http://www.cogkit.org/license
// This message may not be removed or altered.
// ----------------------------------------------------------------------

/*
 * Created on Jul 2, 2004
 */
package org.globus.cog.karajan.compiled.nodes.functions;

import org.globus.cog.karajan.analyzer.CompilationException;
import org.globus.cog.karajan.analyzer.Param;
import org.globus.cog.karajan.analyzer.Pure;
import org.globus.cog.karajan.analyzer.Scope;
import org.globus.cog.karajan.analyzer.Var;
import org.globus.cog.karajan.compiled.nodes.Node;
import org.globus.cog.karajan.parser.WrapperNode;

public class OptionalArg extends Node implements Pure {

	@Override
	public Node compile(WrapperNode w, Scope scope) throws CompilationException {
		Var.Channel cr = scope.lookupChannel("...");
		for (WrapperNode c : w.nodes()) {
			if (c.getNodeType().equals("k:var")) {
				cr.append(new Param(c.getText(), Param.Type.OPTIONAL));
			}
			else {
				throw new CompilationException(c, "Expected identifier");
			}
		}
		return null;
	}
}