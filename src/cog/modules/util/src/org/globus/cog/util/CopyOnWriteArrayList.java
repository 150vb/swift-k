//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Jul 21, 2006
 */
package org.globus.cog.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * An implementation of <tt>List</tt> which guarantees that iterations
 * on the list always occur on a consistent snapshot of the list.
 * 
 * For details see {@link CopyOnWriteHashSet} and s/set/list/.
 * 
 */

public class CopyOnWriteArrayList<T> implements List<T> {
	private List<T> list = Collections.emptyList();
	private int lock;

	public int size() {
		return list.size();
	}

	public boolean isEmpty() {
		return list.isEmpty();
	}

	public boolean contains(Object o) {
		return list.contains(o);
	}
	
	public synchronized void release(Iterator<T> it) {
	    if (((LIterator) it).list == list) {
	        if (lock > 0) {
	            lock--;
	        }
	    }
	}

	public synchronized Iterator<T> iterator() {
		lock++;
		return new LIterator(list);
	}

	public Object[] toArray() {
		return list.toArray();
	}

    public <S> S[] toArray(S[] a) {
        return list.toArray(a);
    }

    public synchronized boolean add(T o) {
		if (lock > 0 || list.isEmpty()) {
			return copyAndAdd(o);
		}
		else {
			return list.add(o);
		}
	}
    
    @Override
    public synchronized void add(int index, T o) {
        if (lock > 0 || list.isEmpty()) {
            copyAndAdd(index, o);
        }
        else {
            list.add(index, o);
        }
    }
	
	private boolean copyAndAdd(T o) {
		List<T> newlist = new ArrayList<T>(list);
		boolean b = newlist.add(o);
		list = newlist;
		lock = 0;
		return b;
	}
	
	private void copyAndAdd(int index, T o) {
        List<T> newlist = new ArrayList<T>(list);
        newlist.add(index, o);
        list = newlist;
        lock = 0;
    }

	public synchronized boolean remove(Object o) {
		if (lock > 0) {
			return copyAndRemove(o);
		}
		else {
			return list.remove(o);
		}
	}
	
	@Override
    public synchronized T remove(int index) {
        if (lock > 0) {
            return copyAndRemove(index);
        }
        else {
            return list.remove(index);
        }
    }
	
	private boolean copyAndRemove(Object o) {
		List<T> newlist = new ArrayList<T>(list);
		boolean b = newlist.remove(o);
		list = newlist;
		lock = 0;
		return b;
	}
	
	private T copyAndRemove(int index) {
        List<T> newlist = new ArrayList<T>(list);
        T b = newlist.remove(index);
        list = newlist;
        lock = 0;
        return b;
    }

	public boolean containsAll(Collection<?> c) {
		return list.containsAll(c);
	}

	public synchronized boolean addAll(Collection<? extends T> c) {
		if (lock > 0 || list.isEmpty()) {
			return copyAndAddAll(c);
		}
		else {
			return list.addAll(c);
		}
	}
	
	@Override
    public boolean addAll(int index, Collection<? extends T> c) {
        if (lock > 0 || list.isEmpty()) {
            return copyAndAddAll(index, c);
        }
        else {
            return list.addAll(index, c);
        }
    }
	
	private boolean copyAndAddAll(Collection<? extends T> c) {
		List<T> newlist = new ArrayList<T>(list);
		boolean b = newlist.addAll(c);
		list = newlist;
		lock = 0;
		return b;
	}
	
	private boolean copyAndAddAll(int index, Collection<? extends T> c) {
        List<T> newlist = new ArrayList<T>(list);
        boolean b = newlist.addAll(index, c);
        list = newlist;
        lock = 0;
        return b;
    }

	public synchronized boolean retainAll(Collection<?> c) {
		if (lock > 0) {
			return copyAndRetainAll(c);
		}
		else {
			return list.retainAll(c);
		}
	}
	
	private boolean copyAndRetainAll(Collection<?> c) {
		List<T> newlist = new ArrayList<T>(list);
		boolean b = newlist.retainAll(c);
		list = newlist;
		lock = 0;
		return b;
	}

	public boolean removeAll(Collection<?> c) {
		if (lock > 0) {
			return copyAndRemoveAll(c);
		}
		else {
			return list.removeAll(c);
		}
	}
	
	private boolean copyAndRemoveAll(Collection<?> c) {
		List<T> newlist = new ArrayList<T>(list);
		boolean b = newlist.removeAll(c);
		list = newlist;
		lock = 0;
		return b;
	}

	public synchronized void clear() {
		list = Collections.emptyList();
		lock = 0;
	}

	public boolean equals(Object obj) {
		if (obj instanceof CopyOnWriteArrayList<?>) {
			return list.equals(((CopyOnWriteArrayList<?>) obj).list);
		}
		else if (obj instanceof List<?>) {
			return list.equals(obj);
		}
		return false;
	}

	public int hashCode() {
		return list.hashCode();
	}

	public String toString() {
		return list.toString();
	}

    @Override
    public T get(int index) {
        return list.get(index);
    }

    @Override
    public int indexOf(Object o) {
        return list.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return list.lastIndexOf(o);
    }

    @Override
    public ListIterator<T> listIterator() {
    	throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T set(int index, T o) {
        return list.set(index, o);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    };
	
	private class LIterator implements Iterator<T> {
	    public List<T> list;
	    private Iterator<T> it;
	    
	    public LIterator(List<T> list){
	        this.list = list;
	        this.it = list.iterator();
	    }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public T next() {
            return it.next();
        }

        @Override
        public void remove() {
            it.remove();
        }
	}
}
