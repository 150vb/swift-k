//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Jul 21, 2013
 */
package org.griphyn.vdl.karajan.monitor.monitors.http;

import java.nio.ByteBuffer;
import java.util.Map;

public abstract class StateDataBuilder {
    public abstract ByteBuffer getData(Map<String, String> params);
    
    protected void add(StringBuilder sb, String key, String value) {
        sb.append(key);
        sb.append(": ");
        sb.append(value);
        sb.append('\n');
    }
    
    protected void add(StringBuilder sb, String key, int value) {
        sb.append(key);
        sb.append(": ");
        sb.append(value);
        sb.append('\n');
    }
    
    protected void add(StringBuilder sb, String key, long value) {
        sb.append(key);
        sb.append(": ");
        sb.append(value);
        sb.append('\n');
    }
}