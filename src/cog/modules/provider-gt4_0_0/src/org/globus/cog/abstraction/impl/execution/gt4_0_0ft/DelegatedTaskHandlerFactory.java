
// ----------------------------------------------------------------------
// This code is developed as part of the Java CoG Kit project
// The terms of the license can be found at http://www.cogkit.org/license
// This message may not be removed or altered.
// ----------------------------------------------------------------------

    
package org.globus.cog.abstraction.impl.execution.gt4_0_0ft;

import org.globus.cog.abstraction.interfaces.DelegatedTaskHandler;
import org.globus.cog.abstraction.interfaces.Task;

public class DelegatedTaskHandlerFactory
{
    public static DelegatedTaskHandler newTaskHandler(int type)
    {
        switch (type)
        {
            case Task.JOB_SUBMISSION :
                return new JobSubmissionTaskHandler();
            case Task.INFORMATION_QUERY :
                return null;
            default :
                return null;
        }
    }
}
