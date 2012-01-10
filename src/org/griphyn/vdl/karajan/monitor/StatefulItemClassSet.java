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
 * Created on Jan 29, 2007
 */
package org.griphyn.vdl.karajan.monitor;

import java.util.Iterator;
import java.util.List;

import org.griphyn.vdl.karajan.monitor.items.StatefulItem;

public class StatefulItemClassSet {
	private RadixTree map;
	private Iterator i;
	private int crt;
	
	public StatefulItemClassSet() {
		map = new RadixTree();
	}

	public synchronized void add(StatefulItem item) {
		map.put(item.getID(), item);
		crt = Integer.MAX_VALUE;
	}

	public synchronized void remove(StatefulItem item) {
		map.remove(item.getID());
		crt = Integer.MAX_VALUE;
	}
	
	public synchronized StatefulItem getByID(String id) {
		return (StatefulItem) map.get(id);
	}
	
	public synchronized StatefulItem findWithPrefix(String prefix) {
		String key = map.find(prefix);
		if (key == null) {
			return null;
		}
		else {
			return (StatefulItem) map.get(key);
		}
	}
	
	public int size() {
		return map.size();
	}
	
	public synchronized List getAll() {
		return map.getAll();
	}
}
