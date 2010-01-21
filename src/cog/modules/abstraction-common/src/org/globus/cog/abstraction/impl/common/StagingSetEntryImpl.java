//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Jan 11, 2010
 */
package org.globus.cog.abstraction.impl.common;

import org.globus.cog.abstraction.interfaces.StagingSetEntry;

public class StagingSetEntryImpl implements StagingSetEntry {
    private final String source, destination;

    public StagingSetEntryImpl(String source, String destination) {
        if (source == null) {
            throw new NullPointerException("Source cannot be null");
        }
        if (destination == null) {
            throw new NullPointerException("Destination cannot be null");
        }
        this.source = source;
        this.destination = destination;
    }

    public String getDestination() {
        return destination;
    }

    public String getSource() {
        return source;
    }

    public boolean equals(Object obj) {
        if (obj instanceof StagingSetEntry) {
            StagingSetEntry other = (StagingSetEntry) obj;
            return source.equals(other.getSource())
                    && destination.equals(other.getDestination());
        }
        else {
            return false;
        }
    }

    public int hashCode() {
        return source.hashCode() + destination.hashCode();
    }

    public String toString() {
        return source + " -> " + destination;
    }
}
