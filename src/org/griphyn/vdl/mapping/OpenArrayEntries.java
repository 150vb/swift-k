//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Feb 5, 2013
 */
package org.griphyn.vdl.mapping;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import k.rt.ConditionalYield;

import org.griphyn.vdl.karajan.Pair;

public class OpenArrayEntries implements Iterable<List<?>> {
    private List<Comparable<?>> keyList;
    private Map<Comparable<?>, DSHandle> array;
    private ArrayDataNode source;

    public OpenArrayEntries(List<Comparable<?>> keyList, Map<Comparable<?>, DSHandle> array, ArrayDataNode source) {
        this.keyList = keyList;
        this.array = array;
        this.source = source;
    }

    @Override
    public Iterator<List<?>> iterator() {
        return new Iterator<List<?>>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                synchronized(source) {
                    if (index < keyList.size()) {
                        return true;
                    }
                    else {
                        if (source.isClosed()) {
                            return false;
                        }
                        else {
                            throw new ConditionalYield(source);
                        }
                    }
                }
            }

            @Override
            public List<?> next() {
                synchronized(source) {
                    if (index < keyList.size()) {
                        Comparable<?> key = keyList.get(index++);
                        return new Pair<Object>(key, array.get(key));
                    }
                    else {
                        if (source.isClosed()) {
                            throw new NoSuchElementException();
                        }
                        else {
                            throw new ConditionalYield(source);
                        }
                    }
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
