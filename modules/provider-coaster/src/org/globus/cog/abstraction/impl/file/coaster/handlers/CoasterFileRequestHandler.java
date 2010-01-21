//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Sep 29, 2008
 */
package org.globus.cog.abstraction.impl.file.coaster.handlers;

import java.io.File;

import org.globus.cog.abstraction.impl.execution.coaster.NotificationManager;
import org.globus.cog.karajan.workflow.service.ProtocolException;
import org.globus.cog.karajan.workflow.service.handlers.RequestHandler;

public abstract class CoasterFileRequestHandler extends RequestHandler {
    private static final String HOME = System.getProperty("user.home");
    private static final String CWD = new File(".").getAbsolutePath();

    public static File normalize(String name) {
        File f = new File(name);

        if (f.isAbsolute()) {
            return f;
        }
        else {
            return new File(CWD, name);
        }
    }

    protected String getProtocol(String file) {
        int index = file.indexOf(':');
        if (index == -1) {
            return "file";
        }
        else {
            return file.substring(0, index);
        }
    }

    protected void sendReply() throws ProtocolException {
        NotificationManager.getDefault().notIdle();
        super.sendReply();
    }
}
