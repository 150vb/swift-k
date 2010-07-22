package org.griphyn.vdl.karajan.lib.swiftscript;

import java.io.IOException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.globus.cog.karajan.arguments.Arg;
import org.globus.cog.karajan.stack.VariableStack;
import org.globus.cog.karajan.util.TypeUtil;
import org.globus.cog.karajan.workflow.ExecutionException;
import org.globus.cog.karajan.workflow.nodes.functions.FunctionsCollection;
import org.griphyn.vdl.karajan.lib.SwiftArg;
import org.griphyn.vdl.karajan.lib.VDLFunction;
import org.griphyn.vdl.mapping.DSHandle;
import org.griphyn.vdl.mapping.HandleOpenException;
import org.griphyn.vdl.mapping.InvalidPathException;
import org.griphyn.vdl.mapping.Path;
import org.griphyn.vdl.mapping.RootArrayDataNode;
import org.griphyn.vdl.mapping.RootDataNode;
import org.griphyn.vdl.type.NoSuchTypeException;
import org.griphyn.vdl.type.Types;
import org.griphyn.vdl.util.VDL2Config;

import org.griphyn.vdl.mapping.AbsFile;
import org.globus.cog.karajan.workflow.futures.FutureNotYetAvailable;

public class Misc extends FunctionsCollection {

	private static final Logger logger = Logger.getLogger(Misc.class);

	public static final SwiftArg PA_INPUT = new SwiftArg.Positional("input");
	public static final SwiftArg PA_PATTERN = new SwiftArg.Positional("regexp");
	public static final SwiftArg PA_TRANSFORM = new SwiftArg.Positional("transform");

	static {
		setArguments("swiftscript_trace", new Arg[] { Arg.VARGS });
		setArguments("swiftscript_strcat", new Arg[] { Arg.VARGS });
		setArguments("swiftscript_strcut", new Arg[] { PA_INPUT, PA_PATTERN });
        setArguments("swiftscript_strstr", new Arg[] { PA_INPUT, PA_PATTERN });
		setArguments("swiftscript_strsplit", new Arg[] { PA_INPUT, PA_PATTERN });
		setArguments("swiftscript_regexp", new Arg[] { PA_INPUT, PA_PATTERN, PA_TRANSFORM });
		setArguments("swiftscript_toint", new Arg[] { PA_INPUT });
		setArguments("swiftscript_tofloat", new Arg[] { PA_INPUT });
		setArguments("swiftscript_tostring", new Arg[] { PA_INPUT });
		setArguments("swiftscript_dirname", new Arg[] { Arg.VARGS });
	}

	private static final Logger traceLogger = Logger.getLogger("org.globus.swift.trace");
	public DSHandle swiftscript_trace(VariableStack stack) throws ExecutionException, NoSuchTypeException,
			InvalidPathException {

		DSHandle[] args = SwiftArg.VARGS.asDSHandleArray(stack);

		StringBuffer buf = new StringBuffer();
		buf.append("SwiftScript trace: ");
		for (int i = 0; i < args.length; i++) {
			DSHandle handle = args[i];
			VDLFunction.waitFor(stack, handle);
			if(i!=0) buf.append(", ");
			buf.append(args[i]);
		}
		traceLogger.warn(buf);
		return null;
	}
  
	public DSHandle swiftscript_strcat(VariableStack stack) throws ExecutionException, NoSuchTypeException,
			InvalidPathException {
		Object[] args = SwiftArg.VARGS.asArray(stack);
		int provid = VDLFunction.nextProvenanceID();
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < args.length; i++) {
			buf.append(TypeUtil.toString(args[i]));
		}
		DSHandle handle = new RootDataNode(Types.STRING);
		handle.setValue(buf.toString());
		handle.closeShallow();
		try {
			if(VDL2Config.getConfig().getProvenanceLog()) {
				DSHandle[] provArgs = SwiftArg.VARGS.asDSHandleArray(stack);
				for (int i = 0; i < provArgs.length; i++) {
					VDLFunction.logProvenanceParameter(provid, (DSHandle)provArgs[i], ""+i);
				}
				VDLFunction.logProvenanceResult(provid, handle, "strcat");
			}
		} catch(IOException ioe) {
			throw new ExecutionException("When logging provenance for strcat", ioe);
		}
		return handle;
	}

	public DSHandle swiftscript_strcut(VariableStack stack) throws ExecutionException, NoSuchTypeException,
			InvalidPathException {
		int provid = VDLFunction.nextProvenanceID();
		String inputString = TypeUtil.toString(PA_INPUT.getValue(stack));
		String pattern = TypeUtil.toString(PA_PATTERN.getValue(stack));
		if (logger.isDebugEnabled()) {
			logger.debug("strcut will match '" + inputString + "' with pattern '" + pattern + "'");
		}

		String group;
		try {
			Pattern p = Pattern.compile(pattern);
			// TODO probably should memoize this?

			Matcher m = p.matcher(inputString);
			m.find();
			group = m.group(1);
		}
		catch (IllegalStateException e) {
			throw new ExecutionException("@strcut could not match pattern " + pattern
					+ " against string " + inputString, e);
		}
		if (logger.isDebugEnabled()) {
			logger.debug("strcut matched '" + group + "'");
		}
		DSHandle handle = new RootDataNode(Types.STRING);
		handle.setValue(group);
		handle.closeShallow();
		VDLFunction.logProvenanceResult(provid, handle, "strcut");
		VDLFunction.logProvenanceParameter(provid, PA_INPUT.getRawValue(stack), "input");
		VDLFunction.logProvenanceParameter(provid, PA_PATTERN.getRawValue(stack), "pattern");
		return handle;
	}
	
    public DSHandle swiftscript_strstr(VariableStack stack) throws ExecutionException, NoSuchTypeException,
            InvalidPathException {
        
        String inputString = TypeUtil.toString(PA_INPUT.getValue(stack));
        String pattern = TypeUtil.toString(PA_PATTERN.getValue(stack));
        if (logger.isDebugEnabled()) {
            logger.debug("strstr will search '" + inputString + 
                "' for pattern '" + pattern + "'");
        }
        int result = inputString.indexOf(pattern);
        DSHandle handle = new RootDataNode(Types.INT);
        handle.setValue(new Double(result));
        handle.closeShallow();
        return handle;
    }
	
	public DSHandle swiftscript_strsplit(VariableStack stack) throws ExecutionException, NoSuchTypeException,
		InvalidPathException {
		String str = TypeUtil.toString(PA_INPUT.getValue(stack));
		String pattern = TypeUtil.toString(PA_PATTERN.getValue(stack));

		String[] split = str.split(pattern);

		DSHandle handle = new RootArrayDataNode(Types.STRING.arrayType());
		for (int i = 0; i < split.length; i++) {
			DSHandle el = handle.getField(Path.EMPTY_PATH.addFirst(String.valueOf(i), true));
			el.setValue(split[i]);
		}
		handle.closeDeep();
		int provid=VDLFunction.nextProvenanceID();
		VDLFunction.logProvenanceResult(provid, handle, "strsplit");
		VDLFunction.logProvenanceParameter(provid, PA_INPUT.getRawValue(stack), "input");
		VDLFunction.logProvenanceParameter(provid, PA_PATTERN.getRawValue(stack), "pattern");
		return handle;
	}

	public DSHandle swiftscript_regexp(VariableStack stack) throws ExecutionException, NoSuchTypeException,
			InvalidPathException {
		String inputString = TypeUtil.toString(PA_INPUT.getValue(stack));
		String pattern = TypeUtil.toString(PA_PATTERN.getValue(stack));
		String transform = TypeUtil.toString(PA_TRANSFORM.getValue(stack));
		if (logger.isDebugEnabled()) {
			logger.debug("regexp will match '" + inputString + "' with pattern '" + pattern + "'");
		}

		String group;
		try {
			Pattern p = Pattern.compile(pattern);
			// TODO probably should memoize this?

			Matcher m = p.matcher(inputString);
			m.find();
			group = m.replaceFirst(transform);
		}
		catch (IllegalStateException e) {
			throw new ExecutionException("@regexp could not match pattern " + pattern
					+ " against string " + inputString, e);
		}
		if (logger.isDebugEnabled()) {
			logger.debug("regexp replacement produced '" + group + "'");
		}
		DSHandle handle = new RootDataNode(Types.STRING);
		handle.setValue(group);
		handle.closeShallow();

		int provid=VDLFunction.nextProvenanceID();
		VDLFunction.logProvenanceResult(provid, handle, "regexp");
		VDLFunction.logProvenanceParameter(provid, PA_INPUT.getRawValue(stack), "input");
		VDLFunction.logProvenanceParameter(provid, PA_PATTERN.getRawValue(stack), "pattern");
		VDLFunction.logProvenanceParameter(provid, PA_TRANSFORM.getRawValue(stack), "transform");
		return handle;
	}

	public DSHandle swiftscript_toint(VariableStack stack) throws ExecutionException, NoSuchTypeException,
			InvalidPathException {
		String inputString = TypeUtil.toString(PA_INPUT.getValue(stack));
		int i = inputString.indexOf(".");
		if( i >= 0 )
		{
			inputString = inputString.substring(0, i);
		}
		DSHandle handle = new RootDataNode(Types.INT);
		handle.setValue( new Integer( inputString ) );
		handle.closeShallow();
		int provid=VDLFunction.nextProvenanceID();
		VDLFunction.logProvenanceResult(provid, handle, "toint");
		VDLFunction.logProvenanceParameter(provid, PA_INPUT.getRawValue(stack), "string");
		return handle;
	}

	public DSHandle swiftscript_tofloat(VariableStack stack) throws ExecutionException, NoSuchTypeException,
			InvalidPathException {
		String inputString = TypeUtil.toString(PA_INPUT.getValue(stack));
		DSHandle handle = new RootDataNode(Types.FLOAT);
		handle.setValue(new Double(inputString));
		handle.closeShallow();
		int provid=VDLFunction.nextProvenanceID();
		VDLFunction.logProvenanceResult(provid, handle, "tofloat");
		VDLFunction.logProvenanceParameter(provid, PA_INPUT.getRawValue(stack), "string");
		return handle;
	}
	
	public DSHandle swiftscript_tostring(VariableStack stack)
                throws ExecutionException, NoSuchTypeException,
                InvalidPathException {
                Object input = PA_INPUT.getValue(stack);
                DSHandle handle = new RootDataNode(Types.STRING);
                handle.setValue(""+input);
                handle.closeShallow();
                return handle;
	}

        public DSHandle swiftscript_dirname(VariableStack stack) 
                throws ExecutionException, NoSuchTypeException, InvalidPathException {
                DSHandle handle;
                try
                {
                        DSHandle[] args = SwiftArg.VARGS.asDSHandleArray(stack);
                        DSHandle arg = args[0];
                        String[] input = VDLFunction.filename(arg);
                        String name = input[0]; 
                        String result = new AbsFile(name).getDir();
                        handle = new RootDataNode(Types.STRING);
                        handle.setValue(result);
                        handle.closeShallow();
                }
                catch (HandleOpenException e) {
                        throw new FutureNotYetAvailable
                                (VDLFunction.addFutureListener(stack, e.getSource()));
                }
                return handle;
        }
}

/*
 * Local Variables:
 *  c-basic-offset: 8
 * End:
 *
 * vim: ft=c ts=8 sts=4 sw=4 expandtab
 */
