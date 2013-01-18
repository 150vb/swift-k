//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Nov 8, 2005
 */
package org.globus.cog.karajan.workflow.nodes;

import org.globus.cog.karajan.arguments.Arg;
import org.globus.cog.karajan.stack.VariableStack;
import org.globus.cog.karajan.util.TypeUtil;
import org.globus.cog.karajan.workflow.ExecutionException;

public class Catch extends AbstractRegexpFailureHandler {
	public static final Arg A_MATCH = new Arg.Positional("match", 0);
	
	static {
		setArguments(Catch.class, new Arg[] { A_MATCH });
	}

	protected void partialArgumentsEvaluated(VariableStack stack) throws ExecutionException {
		ExecutionException error = (ExecutionException) stack.getVar("exception");
		String match = TypeUtil.toString(getArgument(A_MATCH, stack));
		if (matches(match, error)) {
			super.partialArgumentsEvaluated(stack);
			startRest(stack);
		}
		else {
			failImmediately(stack, (ExecutionException) stack.getVar(SequentialChoice.LAST_FAILURE));
		}
	}
}
