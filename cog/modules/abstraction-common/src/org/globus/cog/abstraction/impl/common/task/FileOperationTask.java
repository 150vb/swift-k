// ----------------------------------------------------------------------
// This code is developed as part of the Java CoG Kit project
// The terms of the license can be found at http://www.cogkit.org/license
// This message may not be removed or altered.
// ----------------------------------------------------------------------

package org.globus.cog.abstraction.impl.common.task;

import org.globus.cog.abstraction.interfaces.Task;

public class FileOperationTask extends TaskImpl {
    public FileOperationTask() {
        super();
        setType(Task.FILE_OPERATION);
        setRequiredService(1);
    }

    public FileOperationTask(String name) {
        super(name, Task.FILE_OPERATION);
        setRequiredService(1);
    }
}
