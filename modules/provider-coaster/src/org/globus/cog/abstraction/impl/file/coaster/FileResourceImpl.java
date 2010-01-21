//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Sep 24, 2008
 */
package org.globus.cog.abstraction.impl.file.coaster;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.coaster.service.local.LocalRequestManager;
import org.globus.cog.abstraction.impl.common.task.IllegalSpecException;
import org.globus.cog.abstraction.impl.common.task.InvalidSecurityContextException;
import org.globus.cog.abstraction.impl.common.task.ServiceContactImpl;
import org.globus.cog.abstraction.impl.common.task.TaskSubmissionException;
import org.globus.cog.abstraction.impl.execution.coaster.ServiceManager;
import org.globus.cog.abstraction.impl.file.AbstractFileResource;
import org.globus.cog.abstraction.impl.file.DirectoryNotFoundException;
import org.globus.cog.abstraction.impl.file.FileResourceException;
import org.globus.cog.abstraction.impl.file.IllegalHostException;
import org.globus.cog.abstraction.impl.file.IrrecoverableResourceException;
import org.globus.cog.abstraction.impl.file.coaster.commands.ChmodCommand;
import org.globus.cog.abstraction.impl.file.coaster.commands.DeleteCommand;
import org.globus.cog.abstraction.impl.file.coaster.commands.ExistsCommand;
import org.globus.cog.abstraction.impl.file.coaster.commands.FileInfoCommand;
import org.globus.cog.abstraction.impl.file.coaster.commands.GetFileCommand;
import org.globus.cog.abstraction.impl.file.coaster.commands.IsDirectoryCommand;
import org.globus.cog.abstraction.impl.file.coaster.commands.ListCommand;
import org.globus.cog.abstraction.impl.file.coaster.commands.MkdirCommand;
import org.globus.cog.abstraction.impl.file.coaster.commands.PutFileCommand;
import org.globus.cog.abstraction.impl.file.coaster.commands.RenameCommand;
import org.globus.cog.abstraction.impl.file.coaster.commands.RmdirCommand;
import org.globus.cog.abstraction.interfaces.ExecutableObject;
import org.globus.cog.abstraction.interfaces.GridFile;
import org.globus.cog.abstraction.interfaces.ProgressMonitor;
import org.globus.cog.abstraction.interfaces.SecurityContext;
import org.globus.cog.abstraction.interfaces.ServiceContact;
import org.globus.cog.karajan.workflow.service.ProtocolException;
import org.globus.cog.karajan.workflow.service.channels.ChannelException;
import org.globus.cog.karajan.workflow.service.channels.ChannelManager;
import org.globus.cog.karajan.workflow.service.channels.KarajanChannel;
import org.globus.cog.karajan.workflow.service.commands.Command;
import org.ietf.jgss.GSSCredential;

public class FileResourceImpl extends AbstractFileResource {
    public static final Logger logger = Logger.getLogger(FileResourceImpl.class);
    
    private boolean autostart;
    private String url, provider;
    
    public FileResourceImpl() {
        this.autostart = true;
    }

    public FileResourceImpl(String name, String protocol,
            ServiceContact serviceContact, SecurityContext securityContext) {
        super(name, protocol, serviceContact, securityContext);
        this.autostart = true;
    }

    private void run(Command cmd) throws FileResourceException {
        try {
            KarajanChannel channel = ChannelManager.getManager().reserveChannel(
                    url, (GSSCredential) getSecurityContext().getCredentials(),
                    LocalRequestManager.INSTANCE);
            try {
                cmd.execute(channel);
            }
            catch (ProtocolException e) {
                throw new FileResourceException(e);
            }
            catch (IOException e) {
                throw new IrrecoverableResourceException(e);
            }
        }
        catch (ChannelException e) {
            throw new IrrecoverableResourceException(e);
        }
    }

    public void changeMode(String fileName, int mode)
            throws FileResourceException {
        throw new UnsupportedOperationException();
    }

    public void changeMode(GridFile gridFile) throws FileResourceException {
        run(new ChmodCommand(gridFile));
    }

    public void createDirectory(String directoryName)
            throws FileResourceException {
        run(new MkdirCommand(directoryName));
    }

    public void deleteDirectory(String directoryName, boolean force)
            throws DirectoryNotFoundException, FileResourceException {
        run(new RmdirCommand(directoryName, force));
    }

    public void deleteFile(String fileName) throws FileResourceException {
        run(new DeleteCommand(fileName));
    }

    public boolean exists(String fileName) throws FileResourceException {
        ExistsCommand ec = new ExistsCommand(fileName);
        run(ec);
        return ec.getResult();
    }

    public String getCurrentDirectory() throws FileResourceException {
        throw new UnsupportedOperationException();
    }

    public void getFile(String remoteFileName, String localFileName)
            throws FileResourceException {
        getFile(remoteFileName, localFileName, null);
    }

    public void getFile(String remoteFileName, String localFileName,
            ProgressMonitor progressMonitor) throws FileResourceException {
        try {
            run(new GetFileCommand(remoteFileName, localFileName, progressMonitor));
        }
        catch (IOException e) {
            throw new FileResourceException(e);
        }
    }

    public GridFile getGridFile(String fileName) throws FileResourceException {
        FileInfoCommand fc = new FileInfoCommand(fileName);
        run(fc);
        return fc.getResult();
    }

    public boolean isDirectory(String name)
            throws FileResourceException {
        IsDirectoryCommand ic = new IsDirectoryCommand(name);
        run(ic);
        return ic.getResult();
    }

    public Collection list() throws FileResourceException {
        return list(".");
    }

    public Collection list(String directoryName)
            throws DirectoryNotFoundException, FileResourceException {
        ListCommand lc = new ListCommand(directoryName);
        run(lc);
        try {
            return lc.getResult();
        }
        catch (ProtocolException e) {
            throw new FileResourceException(e);
        }
    }

    public void putFile(String localFileName, String remoteFileName)
            throws FileResourceException {
        try {
            run(new PutFileCommand(localFileName, remoteFileName));
        }
        catch (Exception e) {
            throw new FileResourceException(e);
        }

    }

    public void putFile(String localFileName, String remoteFileName,
            ProgressMonitor progressMonitor) throws FileResourceException {
        putFile(localFileName, remoteFileName);
    }

    public void rename(String oldFileName, String newFileName)
            throws FileResourceException {
        run(new RenameCommand(oldFileName, newFileName));
    }

    public void setCurrentDirectory(String directoryName)
            throws FileResourceException {
        throw new UnsupportedOperationException();
    }

    public void setServiceContact(ServiceContact serviceContact) {
        String contact = serviceContact.getContact();
        int pi = contact.indexOf("://");
        if (pi == -1) {
            throw new IllegalArgumentException(
                    "You need to specify a provider as the url scheme (e.g. coaster-gt2://host:port)");
        }
        String scheme = contact.substring(0, pi);
        if (scheme.startsWith("coaster-")) {
            this.provider = scheme.substring("coaster-".length());
        }
        else {
            this.provider = scheme;
        }
        super.setServiceContact(new ServiceContactImpl(contact.substring(pi + 3)));
    }

    public void start() throws IllegalHostException,
            InvalidSecurityContextException, FileResourceException {
        if (autostart) {
            try {
                url = ServiceManager.getDefault().reserveService(
                        getServiceContact(), getSecurityContext(), provider);
            }
            catch (TaskSubmissionException e) {
                logger.warn("Failed to start coaster resource on " + getServiceContact(), e);
                throw new FileResourceException(
                        "Failed to start coaster resource on "
                                + getServiceContact(), e);
            }
        }
        else {
            url = this.getServiceContact().getContact();
        }
    }

    public void stop() throws FileResourceException {
    }

    public void submit(ExecutableObject commandWorkflow)
            throws IllegalSpecException, TaskSubmissionException {
        throw new UnsupportedOperationException();
    }
}
