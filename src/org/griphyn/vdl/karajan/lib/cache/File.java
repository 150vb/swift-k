/*
 * Created on Dec 29, 2006
 */
package org.griphyn.vdl.karajan.lib.cache;

import java.util.LinkedList;

public class File {
	private String path;
	private Object host;
	private long size, lastAccess;
	private int locked;
	private boolean processingLock;
	private LinkedList processingListeners;

	public File(String file, String dir, Object host, long size) {
		if (dir.endsWith("/")) {
			path = dir + file;
		}
		else {
			path = dir + '/' + file;
		}
		path = normalize(path);
		this.host = host;
		this.size = size;
	}

	public File(String fullPath, Object host, long size) {
		this.path = normalize(fullPath);
		this.host = host;
		this.size = size;
	}
	
	private String normalize(String path) {
		if (path.indexOf("//") == -1) {
			return path;
		}
		else {
			StringBuffer sb = new StringBuffer();
			boolean lastWasSlash = false;
			for(int i = 0; i < path.length(); i++) {
				char c = path.charAt(i);
				if (c == '/') {
					if (!lastWasSlash) {
						sb.append(c);
					}
					lastWasSlash = true;
				}
				else {
					sb.append(c);
					lastWasSlash = false;
				}
			}
			return sb.toString();
		}
	}

	public boolean equals(Object other) {
		if (other instanceof File) {
			File ce = (File) other;
			return path.equals(ce.path) && host.equals(ce.host);
		}
		return false;
	}

	public int hashCode() {
		return path.hashCode() + host.hashCode();
	}

	public String toString() {
		return host + "/" + path;
	}

	public String getPath() {
		return path;
	}

	public Object getHost() {
		return host;
	}

	public void setHost(Object host) {
		this.host = host;
	}

	public synchronized boolean isLocked() {
		return locked > 0;
	}

	public synchronized void lock() {
		locked++;
	}

	public synchronized boolean unlock() {
		locked--;
		return locked == 0;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public long getLastAccess() {
		return lastAccess;
	}

	public void setLastAccess(long lastAccess) {
		this.lastAccess = lastAccess;
	}

	public long touch() {
		try {
			return lastAccess;
		}
		finally {
			lastAccess = System.currentTimeMillis();
		}
	}

	/**
	 * This means that the cache has decided that the file should be removed and
	 * nothing else can be done on it. It cannot be added or removed from the
	 * cache.
	 */
	public void lockForProcessing() {
		processingLock = true;
	}
	
	public void unlockFromProcessing() {
		processingLock = false;
		notifyListeners();
	}

	public boolean isLockedForProcessing() {
		return processingLock;
	}

	public synchronized void addProcessingListener(ProcessingListener l, Object param) {
		if (processingListeners == null) {
			processingListeners = new LinkedList();
		}
		processingListeners.add(new Object[] { l, param });
	}

	public synchronized void notifyListeners() {
		if (processingListeners != null) {
			while (processingListeners.size() > 0) {
				Object[] p = (Object[]) processingListeners.removeFirst();
				((ProcessingListener) p[0]).processingComplete(this, p[1]);
			}
		}
	}
}