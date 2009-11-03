// ----------------------------------------------------------------------
// This code is developed as part of the Java CoG Kit project
// The terms of the license can be found at http://www.cogkit.org/license
// This message may not be removed or altered.
// ----------------------------------------------------------------------

/*
 * Created on Jul 2, 2004
 */
package org.globus.cog.karajan.workflow.nodes.functions;

import java.util.Collections;

import org.globus.cog.karajan.arguments.Arg;
import org.globus.cog.karajan.stack.VariableStack;
import org.globus.cog.karajan.util.Identifier;
import org.globus.cog.karajan.util.TypeUtil;
import org.globus.cog.karajan.workflow.DirectExecution;
import org.globus.cog.karajan.workflow.ExecutionException;

public class Variable extends AbstractFunction implements DirectExecution {

	public static final Arg A_NAME = new Arg.Positional("name");
	
	static {
		setArguments(Variable.class, new Arg[] { A_NAME });
	}

	private final int frame = -1;
	private String name;

	public Variable() {
		this.setAcceptsInlineText(true);
	}
	
	public Object function(VariableStack stack) throws ExecutionException {
		synchronized (this) {
			if (name == null) {
				name = TypeUtil.toString(A_NAME.getStatic(this)).toLowerCase();
				if (getStaticArguments().size() == 1) {
					setStaticArguments(Collections.EMPTY_MAP);
				}
			}
		}

		if (stack.parentFrame().isDefined("#quoted")) {
			Object value = new Identifier(name);
			setSimple(value);
			return value;
		}
		else {
			return stack.getVar(name);
		}
	}
}