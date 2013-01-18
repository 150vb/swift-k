//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Feb 15, 2008
 */
package org.globus.cog.abstraction.coaster.service;

import java.util.Map;

import org.globus.cog.karajan.workflow.service.channels.ChannelException;
import org.globus.cog.karajan.workflow.service.channels.KarajanChannel;

public interface Registering {
    String registrationReceived(String id, String url, KarajanChannel channel, Map<String, String> options) throws ChannelException;

    void unregister(String id);
}
