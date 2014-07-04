//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Mar 28, 2014
 */
package org.griphyn.vdl.mapping.nodes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import k.rt.FutureListener;

import org.globus.cog.karajan.futures.FutureNotYetAvailable;
import org.griphyn.vdl.karajan.WaitingThreadsMonitor;
import org.griphyn.vdl.mapping.ClosedArrayEntries;
import org.griphyn.vdl.mapping.DSHandle;
import org.griphyn.vdl.mapping.DependentException;
import org.griphyn.vdl.mapping.HandleOpenException;
import org.griphyn.vdl.mapping.InvalidPathException;
import org.griphyn.vdl.mapping.MappingDependentException;
import org.griphyn.vdl.mapping.OpenArrayEntries;
import org.griphyn.vdl.mapping.Path;
import org.griphyn.vdl.mapping.RootHandle;
import org.griphyn.vdl.type.Field;

public abstract class AbstractFutureArrayDataNode extends AbstractFutureDataNode implements ArrayHandle {
    private Map<Comparable<?>, DSHandle> handles;
    private RuntimeException exception;
    private List<Comparable<?>> keyList;
    
    public AbstractFutureArrayDataNode(Field field) {
        super(field);
        handles = new HashMap<Comparable<?>, DSHandle>();
    }
    
    public void addField(Comparable<?> key, DSHandle n) {
        synchronized(this) {
            handles.put(key, n);
            addKey(key);
        }
        notifyListeners();
    }
    
    private void addKey(Comparable<?> key) {
        if (keyList != null) {
            keyList.add(key);
        }
    }
    
        
    @Override
    public RootHandle getRoot() {
        return null;
    }

    @Override
    public DSHandle getParent() {
        return null;
    }

    @Override
    public Path getPathFromRoot() {
        return null;
    }

    @Override
    protected AbstractDataNode getParentNode() {
        return null;
    }
    
    @Override
    public void addListener(FutureListener l, boolean partialUpdates) {
        boolean shouldNotify;
        WaitingThreadsMonitor.addThread(l, this);
        synchronized(this) {
            shouldNotify = addListener0(l);
            if (keyList != null && partialUpdates) {
                shouldNotify = true;
            }
        }
        if (shouldNotify) {
            notifyListeners();
        }
    }
    
    @Override
    public Iterable<List<?>> entryList() {
        synchronized(this) {
            if (isClosed()) {
                return new ClosedArrayEntries(getArrayValue());
            }
            else {
                keyList = new ArrayList<Comparable<?>>(getArrayValue().keySet());
                return new OpenArrayEntries(keyList, getArrayValue(), this);
            }
        }
    }

    @Override
    protected Object getRawValue() {
        return handles;
    }
        
    public synchronized DSHandle getField(Comparable<?> key) throws NoSuchFieldException {
        DSHandle handle = handles.get(key);
        if (handle == null) {
            if (isClosed()) {
                throw new NoSuchFieldException(key.toString());
            }
            handle = createField(key);
        }
        return handle;
    }

    private DSHandle createField(Comparable<?> key) {
        DSHandle h = NodeFactory.newNode(Field.Factory.createField(key, field.getType().itemType()), getRoot(), this);
        synchronized(this) {
            handles.put(key, h);
            addKey(key);
        }
        notifyListeners();
        return h;
    }
    
    @Override
    public Collection<DSHandle> getAllFields() throws InvalidPathException, HandleOpenException {
        synchronized(this) {
            if (!isClosed()) {
                throw new HandleOpenException(this);
            }
        }
        return handles.values();
    }

    protected void checkDataException() {
        if (exception instanceof DependentException) {
            throw (DependentException) exception;
        }
    }
    
    protected void checkMappingException() {
        if (exception instanceof MappingDependentException) {
            throw (MappingDependentException) exception;
        }
    }

    public synchronized Object getValue() {
        checkDataException();
        return handles;
    }
    
    public Map<Comparable<?>, DSHandle> getArrayValue() {
        checkDataException();
        return handles;
    }
    
    @Override
    public synchronized void closeShallow() {
        super.closeShallow();
        this.keyList = null;
        if (handles.isEmpty()) {
            handles = Collections.emptyMap();
        }
    }
    
    @Override
    public void closeArraySizes() {
        closeShallow();
        try {
            for (DSHandle h : getAllFields()) {
                h.closeArraySizes();
            }
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void closeDeep() {
        closeShallow();
        // closeShallow provides a synchronization point, so we know handles cannot be modified after that
        for (DSHandle handle : handles.values()) {
            handle.closeDeep();
        }
    }
    
    @Override
    public void getFringePaths(List<Path> list, Path myPath) throws HandleOpenException {
        checkMappingException();
        if (!isClosed()) {
            throw new FutureNotYetAvailable(this);
        }
        for (Map.Entry<Comparable<?>, DSHandle> e : handles.entrySet()) {
            AbstractDataNode child = (AbstractDataNode) e.getValue();
            Path fullPath = myPath.addLast(e.getKey(), true);
            child.getFringePaths(list, fullPath);
        }
    }
    
    @Override
    protected void getLeaves(List<DSHandle> list) throws HandleOpenException {
        checkMappingException();
        if (!isClosed()) {
            throw new HandleOpenException(this);
        }
        for (DSHandle h : handles.values()) {
            AbstractDataNode child = (AbstractDataNode) h;
            child.getLeaves(list);
        }
    }
    
    public boolean isArray() {
        return true;
    }
    
    @Override
    protected void clean0() {
        for (DSHandle h : handles.values()) {
            ((AbstractDataNode) h).clean0();
        }
        handles = null;
        super.clean0();
    }

    @Override
    protected void checkNoValue() {
        if (exception != null) {
            throw exception;
        }
    }
    
    @Override
    public int arraySize() {
        return handles.size();
    }
    
    public synchronized void setException(RuntimeException e) {
        this.exception = e;
        closeShallow();
    }
}
