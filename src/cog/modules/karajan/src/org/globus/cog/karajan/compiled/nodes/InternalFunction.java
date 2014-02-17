// ----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Apr 16, 2005
 */
package org.globus.cog.karajan.compiled.nodes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import k.rt.ExecutionException;
import k.rt.FutureObject;
import k.rt.FutureValue;
import k.rt.Stack;
import k.thr.LWThread;
import k.thr.Yield;

import org.globus.cog.karajan.analyzer.ArgMappingChannel;
import org.globus.cog.karajan.analyzer.ArgRef;
import org.globus.cog.karajan.analyzer.ChannelRef;
import org.globus.cog.karajan.analyzer.CompilationException;
import org.globus.cog.karajan.analyzer.IntrospectionHelper;
import org.globus.cog.karajan.analyzer.Param;
import org.globus.cog.karajan.analyzer.ParamWrapperVar;
import org.globus.cog.karajan.analyzer.Pure;
import org.globus.cog.karajan.analyzer.Scope;
import org.globus.cog.karajan.analyzer.Signature;
import org.globus.cog.karajan.analyzer.StaticChannel;
import org.globus.cog.karajan.analyzer.Var;
import org.globus.cog.karajan.parser.WrapperNode;

public abstract class InternalFunction extends Sequential {
	protected static final int DYNAMIC = -1;
	
	private boolean hasVargs;
	
	private List<ChannelRef<?>> channelParams;
	protected ChannelRef<Object> _vargs;
	
	protected abstract Signature getSignature();
	private int firstOptionalIndex = -1, lastOptionalIndex;
		
	protected Param[] params(Object... p) {
		Param[] a = new Param[p.length];
		for (int i = 0; i < p.length; i++) {
			if (p[i] instanceof String) {
				String name = (String) p[i];
				if (name.equals("...")) {
					a[i] = new Param((String) p[i], Param.Type.CHANNEL);
				}
				else {
					a[i] = new Param((String) p[i], Param.Type.POSITIONAL);
				}
			}
			else if (p[i] instanceof Param) {
				a[i] = (Param) p[i];
			}
			else {
				throw new IllegalArgumentException("Unknown parameter object: " + p[i]);
			}
		}
		return a;
	}
	
	protected Param[] returns(Object... p) {
		return params(p);
	}
	
	protected Param channel(String name) {
		if (name == null) {
			throw new IllegalArgumentException("Null parameter name");
		}
		return new Param(name, Param.Type.CHANNEL);
	}
	
	protected Param channel(String name, k.rt.Channel<?> value) {
		if (name == null) {
			throw new IllegalArgumentException("Null parameter name");
		}
		return new Param(name, Param.Type.CHANNEL, value);
	}
	
	protected Param channel(String name, int arity) {
		if (name == null) {
			throw new IllegalArgumentException("Null parameter name");
		}
		Param p = new Param(name, Param.Type.CHANNEL);
		if (arity == DYNAMIC) {
			p.setDynamic();
		}
		else {
			//p.arity = arity;
			p.setDynamic();
		}
		return p;
	}
	
	protected Param optional(String name, Object value) {
		if (name == null) {
			throw new IllegalArgumentException("Null parameter name");
		}
		return new Param(name, Param.Type.OPTIONAL, value);
	}
	
	protected Param identifier(String name) {
		if (name == null) {
			throw new IllegalArgumentException("Null parameter name");
		}
		return new Param(name, Param.Type.IDENTIFIER);
	}
	
	protected Param block(String name) {
		return new Param(name, Param.Type.BLOCK);
	}
	
	protected List<ChannelRef<?>> getChannelParams() {
		return channelParams;
	}
		
	protected void runBody(LWThread thr) {
	}
	
	@Override
	public void run(LWThread thr) {
		int ec = childCount();
        int i = thr.checkSliceAndPopState();
        Stack stack = thr.getStack();
        try {
	        switch (i) {
	        	case 0:
	        		initializeArgs(stack);
	        		i++;
	        	default:
	        			try {
				            for (; i <= ec; i++) {
				            	runChild(i - 1, thr);
				            }
				            checkArgs(stack);
	        			}
	        			catch (IllegalArgumentException e) {
	        				throw new ExecutionException(this, e.getMessage());
	        			}
			            try {
			            	runBody(thr);
			            }
			            catch (ExecutionException e) {
			            	throw e;
			            }
			            catch (RuntimeException e) {
			            	throw new ExecutionException(this, e);
			            }
		    }
        }
        catch (Yield y) {
            y.getState().push(i);
            throw y;
        }
	}

	protected void initializeArgs(final Stack stack) {
		if (_vargs != null) {
			_vargs.create(stack);
		}
		if (channelParams != null) {
			for (ChannelRef<?> c : channelParams) {
				c.create(stack);
			}
		}
		initializeOptional(stack);
	}
	
	protected void checkArgs(final Stack stack) {
		if (_vargs != null) {
			_vargs.check(stack);
		}
	}
	
	protected void initializeOptional(Stack stack) {
	    if (firstOptionalIndex != -1) {
            Arrays.fill(stack.top().getAll(), firstOptionalIndex, lastOptionalIndex, null);
        }
	}

	protected static class ArgInfo {
		public final LinkedList<WrapperNode> blocks;
		public final Var.Channel vargs;
		public final StaticChannel vargValues;
		public final List<Param> optional;
		public final List<Param> positional;
		public final List<Param> channelParams;
		public final ParamWrapperVar.IndexRange ir;
		
		public ArgInfo(LinkedList<WrapperNode> blocks, Var.Channel vargs, StaticChannel vargValues, 
				List<Param> optional, List<Param> positional, List<Param> channelParams, ParamWrapperVar.IndexRange ir) {
			this.blocks = blocks;
			this.vargs = vargs;
			this.vargValues = vargValues;
			this.optional = optional;
			this.positional = positional;
			this.channelParams = channelParams;
			this.ir = ir;
		}
	}
	
	protected ArgInfo compileArgs(WrapperNode w, Signature sig, Scope scope) throws CompilationException {
		resolveChannelReturns(w, sig, scope);
		resolveParamReturns(w, sig, scope);
		
		List<Param> channels = getChannelParams(sig);
		List<Param> optional = getOptionalParams(sig);
		List<Param> positional = getPositionalParams(sig);
		// optionals first
		if (optional != null || positional != null) {
			scope.setParams(true);
		}
		
		ParamWrapperVar.IndexRange ir = addParams(w, sig, scope, channels, optional, positional);
					
		LinkedList<WrapperNode> blocks = checkBlockArgs(w, sig);
		
		Var.Channel vargs = null;
		ArgMappingChannel amc = null;
		if (!sig.getParams().isEmpty() || hasVargs) {
			if (hasVargs) {
				vargs = scope.lookupChannel("...");
				vargs.setValue(amc = new ArgMappingChannel(w, positional, true));
			}
			else {
				vargs = scope.addChannel("...", amc = new ArgMappingChannel(w, positional, false));
			}
		}

		return new ArgInfo(blocks, vargs, amc, optional, positional, channels, ir);
	}

	protected ParamWrapperVar.IndexRange addParams(WrapperNode w, Signature sig, Scope scope, List<Param> channels,
			List<Param> optional, List<Param> positional) throws CompilationException {
		processIdentifierArgs(w, sig);
		prepareChannelParams(scope, channels);
		scope.addChannelParams(channels);
		scope.addPositionalParams(positional);
		return scope.addOptionalParams(optional);
	}

	@Override
	protected final Node compileChildren(WrapperNode w, Scope scope) throws CompilationException {
				
		Signature sig = getSignature();
		
		Scope argScope = new Scope(w, scope);
	
		ArgInfo ai = compileArgs(w, sig, argScope);
		
		Node n = super.compileChildren(w, argScope);
		
		if (ai.ir != null) {
		    // unused optional parameters
			scope.releaseRange(ai.ir.currentIndex(), ai.ir.lastIndex());
		}
		
		setRefs(w, ai, argScope);
	
		addLocals(scope);
		
		compileBlocks(w, sig, ai.blocks, scope);
		
		if (compileBody(w, argScope, scope) == null && n == null && ai.blocks == null) {
			argScope.close();
			return null;
		}
		
		argScope.close();
		
		if (this instanceof Pure && ai.blocks == null) {
			return n;
		}
		else {
			return this;
		}
	}

	protected Node compileBody(WrapperNode w, Scope argScope, Scope scope) throws CompilationException {
		return this;
	}

	@SuppressWarnings("unchecked")
	protected void setRefs(WrapperNode w, ArgInfo ai, Scope scope) throws CompilationException {
		for (Param p : ai.optional) {
			if (p.dynamic) {
				setArg(w, p, new ArgRef.DynamicOptional<Object>(p.index, p.value));
			}
			else {
				setArg(w, p, new ArgRef.Static<Object>(p.value));
				removeOptionalParam(scope, p);
			}
		}
		
		if (ai.ir != null && ai.ir.isUsed()) {
		    firstOptionalIndex = ai.ir.firstIndex();
		    lastOptionalIndex = ai.ir.currentIndex() - 1;
		}
		
		boolean allPosStatic = true;
		boolean staticAfterDynamic = false;
		int firstDynamicIndex = -1, dynamicCount = 0; 
		for (Param p : ai.positional) {
            if (!p.dynamic) {
                setArg(w, p, new ArgRef.Static<Object>(p.value));
                if (!allPosStatic) {
                	staticAfterDynamic = true;
                }
            }
            else {
            	if (staticAfterDynamic) {
            		throw new CompilationException(w, "Cannot use positional arguments ('" 
            				+ p.name + "') after keyword argument");
            	}
            	if (firstDynamicIndex == -1) {
            		firstDynamicIndex = p.index;
            	}
            	allPosStatic = false;
            	dynamicCount++;
                setArg(w, p, new ArgRef.Dynamic<Object>(p.index));
            }
        }
		
		if (hasVargs) {
			boolean allVargsStatic = !ai.vargs.isDynamic();
			if (allPosStatic) {
				_vargs = (ChannelRef<Object>) makeChannelRef(ai.vargs, ai.vargValues);
			}
			else {
				_vargs = makeArgMappingChannel(firstDynamicIndex, dynamicCount, ai.vargs.getIndex());
				int i1 = ai.positional.size() - dynamicCount;
				int i2 = ai.positional.size();
				((ChannelRef.ArgMapping<Object>) _vargs).setNamesP(ai.positional.subList(i1, i2));
			}
			setChannelArg(w, Param.VARGS, _vargs);
        }
        else {
            if (!allPosStatic) {
            	_vargs = makeArgMappingFixedChannel(firstDynamicIndex, dynamicCount, ai.vargs.getIndex());
				int i1 = ai.positional.size() - dynamicCount;
				int i2 = ai.positional.size();
				((ChannelRef.ArgMapping<Object>) _vargs).setNamesP(ai.positional.subList(i1, i2));
            }
            else if (ai.vargs != null) {
            	_vargs = makeInvalidArgChannel(ai.vargs.getIndex());
            }
        }
		
		if (ai.channelParams != null) {
			for (Param p : ai.channelParams) {
				if (!p.name.equals("...")) {
					StaticChannel c = (StaticChannel) p.value;
					ChannelRef<?> cr = makeChannelRef(scope.lookupChannel(p), c);
					if (channelParams == null) {
						channelParams = new LinkedList<ChannelRef<?>>();
					}
					channelParams.add(cr);
					setChannelArg(w, p, cr);
				}
			}
		}
	}

	protected void removeOptionalParam(Scope scope, Param p) {
	    scope.removeVar(p.varName());
	}

	private ChannelRef<Object> makeInvalidArgChannel(int index) {
		return new ChannelRef.InvalidArg(this, "#channel#...", index);
	}

	protected ChannelRef<Object> makeArgMappingFixedChannel(int firstDynamicIndex, int dynamicCount, int vargsIndex) {
		return new ChannelRef.ArgMappingFixed<Object>(firstDynamicIndex, dynamicCount, vargsIndex);
	}

	protected ChannelRef<Object> makeArgMappingChannel(int firstDynamicIndex, int dynamicCount, int vargsIndex) {
		return new ChannelRef.ArgMapping<Object>(firstDynamicIndex, dynamicCount, vargsIndex);
	}

	private ChannelRef<?> makeChannelRef(Var.Channel vc, StaticChannel c) {
		if (vc.isDynamic()) {
			if (c.isEmpty()) {
				return makeDynamicChannel(vc.name, vc.getIndex());
			}
			else {
				return makeMixedChannel(vc.getIndex(), c.getAll());
			}
		}
		else {
			return makeStaticChannel(vc.getIndex(), c.getAll());
		}
	}

	protected ChannelRef<?> makeStaticChannel(int index, List<Object> values) {
		return new ChannelRef.Static<Object>(values);
	}

	protected ChannelRef<?> makeMixedChannel(int index, List<Object> staticValues) {
		return new ChannelRef.Mixed<Object>(index, staticValues);
	}

	protected ChannelRef<?> makeDynamicChannel(String name, int index) {
		return new ChannelRef.Dynamic<Object>(name, index);
	}

	protected void compileBlocks(WrapperNode w, Signature sig, 
			LinkedList<WrapperNode> blocks, Scope scope) throws CompilationException {
		if (blocks != null) {
			ListIterator<Param> li = sig.getParams().listIterator(sig.getParams().size());
			while (li.hasPrevious()) {
				Param r = li.previous();
				if (r.type == Param.Type.BLOCK) {
					setArg(w, r, compileBlock(blocks.removeLast(), scope));
				}
			}
		}
	}

	protected void setArg(WrapperNode w, Param r, Object value) throws CompilationException {
		IntrospectionHelper.setField(w, this, escapeKeywords(r.name), value);
	}
	
	private static final Set<String> JAVA_KEYWORDS;
	
	static {
		JAVA_KEYWORDS = new HashSet<String>();
		JAVA_KEYWORDS.add("static");
		JAVA_KEYWORDS.add("default");
	}
	
	private String escapeKeywords(String name) {
		if (JAVA_KEYWORDS.contains(name)) {
			return "_" + name;
		}
		else {
			return name;
		}
	}

	protected void setChannelArg(WrapperNode w, Param r, Object value) throws CompilationException {
		IntrospectionHelper.setField(w, this, "c_" + r.fieldName(), value);
	}
	
	protected void setChannelReturn(WrapperNode w, Param r, Object value) throws CompilationException {
		IntrospectionHelper.setField(w, this, "cr_" + r.fieldName(), value);
	}
	
	protected void setReturn(WrapperNode w, Param r, Object value) throws CompilationException {
        IntrospectionHelper.setField(w, this, "r_" + r.fieldName(), value);
    }

	private LinkedList<WrapperNode> checkBlockArgs(WrapperNode w, Signature sig) throws CompilationException {
		LinkedList<WrapperNode> l = null;
		ListIterator<Param> li = sig.getParams().listIterator(sig.getParams().size());
		boolean seenNonBlock = false;
		while (li.hasPrevious()) {
			Param r = li.previous();
			if (r.type == Param.Type.BLOCK) {
				if (seenNonBlock) {
					throw new CompilationException(w, "Cannot have normal arguments after block arguments");
				}
				if (l == null) {
					l = new LinkedList<WrapperNode>();
				}
				l.addFirst(w.removeNode(w.nodeCount() - 1));
			}
			else {
				seenNonBlock = true;
			}
		}
		return l;
	}
	
	private List<Param> getChannelParams(Signature sig) {
		return sig.getChannelParams();
	}

	private List<Param> getOptionalParams(Signature sig) {
		List<Param> l = new LinkedList<Param>();
		for (Param p : sig.getParams()) {
			if (p.type == Param.Type.OPTIONAL) {
				l.add(p);
			}
		}
		return l;
	}
	
	private List<Param> getPositionalParams(Signature sig) {
		List<Param> l = new LinkedList<Param>();
		for (Param p : sig.getParams()) {
			if (p.type == Param.Type.POSITIONAL) {
				l.add(p);
			}
		}
		return l;
	}

	protected void processIdentifierArgs(WrapperNode w, Signature sig) throws CompilationException {
		Iterator<Param> i = sig.getParams().iterator();
		boolean found = false;
		while (i.hasNext()) {
			Param p = i.next();
			if (p.type == Param.Type.IDENTIFIER) {
				if (found) {
					throw new CompilationException(w, "Only one identifier parameter allowed");
				}
				WrapperNode in = w.getNode(0);
				if (!"k:var".equals(in.getNodeType())) {
					throw new CompilationException(w, "Expected identifier");
				}
				
				setArg(w, p, in.getText());
				w.removeNode(in);
				i.remove();
			}
		}
	}

	private List<ArgRef<?>> sortParams(WrapperNode w) {
		// optionals first, 
		return null;
	}

	protected void addLocals(Scope scope) {
	}

	protected void prepareChannelParams(Scope scope, List<Param> channels) {
		if (channels != null && !channels.isEmpty()) {
			for (Param p : channels) {
				if (p.name.equals("...")) {
					hasVargs = true;
				}
				if (p.getValue() == null) {
					StaticChannel c = new StaticChannel();
					p.setValue(c);
				}
			}
		}
	}

	protected void resolveChannelReturns(WrapperNode w, Signature sig, Scope scope) throws CompilationException {
		for (Param p : sig.getChannelReturns()) {
			Var.Channel vc = scope.parent.lookupChannel(p, this);
			if (p.dynamic) {
				vc.appendDynamic();
			}
			setChannelReturn(w, p, scope.parent.getChannelRef(vc));
		}
	}
	
	protected void resolveParamReturns(WrapperNode w, Signature sig, Scope scope) throws CompilationException {
        for (Param p : sig.getReturns()) {
        	Var v = scope.parent.lookupParam(p, this);
            v.setDynamic();
            setReturn(w, p, scope.parent.getVarRef(v));
        }
    }

	protected Node compileBlock(WrapperNode w, Scope scope) throws CompilationException {
		return w.compile(this, scope);
	}
	
	protected Object unwrap(Object o) {
		if (o instanceof FutureObject) {
			return ((FutureObject) o).getValue();
		}
		else {
			return o;
		}
	}
	
	protected void waitFor(Object o) {
        if (o instanceof FutureValue) {
            ((FutureValue) o).getValue();
        }
    }
}
