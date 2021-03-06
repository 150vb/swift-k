/*
 * Swift Parallel Scripting Language (http://swift-lang.org)
 * Code from Java CoG Kit Project (see notice below) with modifications.
 *
 * Copyright 2005-2014 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// ----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Oct 14, 2004
 */
package org.globus.cog.karajan.compiled.nodes;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import k.rt.ConditionalYield;
import k.rt.ExecutionException;
import k.rt.FutureCounter;
import k.rt.Stack;
import k.thr.LWThread;
import k.thr.Yield;

import org.globus.cog.karajan.analyzer.ChannelRef;
import org.globus.cog.karajan.analyzer.Signature;

public class EventWait extends InternalFunction {
	private ChannelRef<Object> c_vargs;

	@Override
	protected Signature getSignature() {
		return new Signature(params("..."));
	}

	@Override
	public synchronized void runBody(LWThread thr) {
		int i = thr.checkSliceAndPopState(2);
		Stack stack = thr.getStack();
		try {
			switch (i) {
				case 0:
					super.runBody(thr);
					i++;
				case 1:
					waitForEvent(thr, stack);
					break;
				default:
			}
		}
		catch (Yield y) {
			y.getState().push(i, 2);
			throw y;
		}
	}
		
		
	private void waitForEvent(LWThread thr, Stack stack) {
		k.rt.Channel<Object> vargs = c_vargs.get(stack);
		
		FutureCounter counter = new FutureCounter(vargs.size()); 
		for (Object arg : vargs) {
			if (!(arg instanceof List)) {
				throw new ExecutionException("Each argument must be a list");
			}
			List<?> list = (List<?>) arg;
			if (list.size() != 3) {
				throw new ExecutionException("Each argument must be a list containing 3 items");
			}
			try {
				String ret = (String) list.get(0);
				String type = (String) list.get(1);
				Object source = list.get(2);
				if ("java.awt.events.ActionEvent".equals(type)) {
					addListener(source, "addActionListener", ActionListener.class, counter);
				}
				else if ("java.awt.events.WindowEvent".equals(type)) {
					addListener(source, "addWindowListener", WindowListener.class, counter);
				}
				else {
					throw new ExecutionException("Unknown event type: " + type);
				}
			}
			catch (Exception e) {
				throw new ExecutionException("Exception caught while adding listener", e);
			}
		}
		throw new ConditionalYield(2, 3, counter);
	}

	protected void addListener(Object source, String methodName, Class<?> argType, FutureCounter counter) {
		try {
			Method method = source.getClass().getMethod(methodName, new Class[] { argType });
			method.invoke(source, new Object[] { this });
		}
		catch (SecurityException e) {
			throw new ExecutionException("No access to " + methodName + " method", e);
		}
		catch (NoSuchMethodException e) {
			throw new ExecutionException("Object does not have a " + methodName + "("
					+ argType.toString() + ") method", e);
		}
		catch (ExecutionException e) {
			throw new ExecutionException("Unsupported event type: " + argType.getName(), e);
		}
		catch (IllegalAccessException e) {
			throw new ExecutionException(
					"Cannot invoke " + methodName + " on " + source.toString(), e);
		}
		catch (InvocationTargetException e) {
			throw new ExecutionException(methodName + " threw an exception", e);
		}
	}
	
	public static class Listener implements ActionListener, WindowListener {
		private FutureCounter counter;
		
		public Listener(FutureCounter counter) {
			this.counter = counter;
		}

		public synchronized void actionPerformed(ActionEvent e) {
			counter.dec();
		}
	
		public void windowActivated(WindowEvent e) {
		}
	
		public void windowClosed(WindowEvent e) {
		}
	
		public void windowClosing(WindowEvent e) {
			counter.dec();
		}
	
		public void windowDeactivated(WindowEvent e) {
		}
	
		public void windowDeiconified(WindowEvent e) {
		}
	
		public void windowIconified(WindowEvent e) {
		}
	
		public void windowOpened(WindowEvent e) {
		}
	}
}