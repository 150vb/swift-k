//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

package org.globus.cog.abstraction.impl.file.ftp;

import java.net.PasswordAuthentication;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.impl.common.task.SecurityContextImpl;

public class InteractiveFTPSecurityContextImpl extends SecurityContextImpl {

    private static Logger logger = Logger
            .getLogger(InteractiveFTPSecurityContextImpl.class.getName());

    public InteractiveFTPSecurityContextImpl() {
    }

    public InteractiveFTPSecurityContextImpl(PasswordAuthentication credentials) {
        setCredentials(credentials);
    }

    public void setCredentials(Object credentials, String alias) {
        setCredentials(credentials);
    }

    public synchronized Object getCredentials() {
        Object credentials = getCredentials();
        if (credentials == null) {
            credentials = CredentialsDialog.showCredentialsDialog();
            setCredentials(credentials);
        }
        return credentials;
    }
}
