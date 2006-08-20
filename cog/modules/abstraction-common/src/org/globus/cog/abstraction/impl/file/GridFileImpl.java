// ----------------------------------------------------------------------
// This code is developed as part of the Java CoG Kit project
// The terms of the license can be found at http://www.cogkit.org/license
// This message may not be removed or altered.
// ----------------------------------------------------------------------

package org.globus.cog.abstraction.impl.file;

import org.globus.cog.abstraction.interfaces.GridFile;
import org.globus.cog.abstraction.interfaces.Permissions;

/**
 * GridFileImpl contains information pertaining to a file. 
 */
public class GridFileImpl implements GridFile {

    private long size = 0;
    private String name = null;
    private String absolutePathName = null;
    private String date = null;

    private byte fileType;
    private String mode = null;

    private Permissions userPermissions = null;
    private Permissions groupPermissions = null;
    private Permissions allPermissions = null;

    public GridFileImpl() {
        userPermissions = new PermissionsImpl();
        groupPermissions = new PermissionsImpl();
        allPermissions = new PermissionsImpl();
    }

    /** set name for the file */
    public void setName(String name) {
        this.name = name;
    }

    /** return name of the file */
    public String getName() {
        return name;
    }

    /** set the absolute path name */
    public void setAbsolutePathName(String name) {
        this.absolutePathName = name;
    }

    /** return the absolute path name */
    public String getAbsolutePathName() {
        return this.absolutePathName;
    }

    /** set the size of the file */
    public void setSize(long size) {
        this.size = size;
    }

    /** return the size of the file */
    public long getSize() {
        return size;
    }

    /** set the last modified date for this file */
    public void setLastModified(String date) {
        this.date = date;
    }

    /** return last modified date for this file */
    public String getLastModified() {
        return date;
    }

    /** set file type to UNKNOWN/ FILE/ DIRECTORY/ SOFTLINK/ DEVICE */
    public void setFileType(byte type) {
        this.fileType = type;
    }

    /** return file type */
    public byte getFileType() {
        return this.fileType;
    }

    /** return true if the GridFile represents a file */
    public boolean isFile() {
        return (fileType == FILE);
    }

    /** return true if the GridFile represents a directory */
    public boolean isDirectory() {
        return (fileType == DIRECTORY);
    }

    /** return true if the GridFile represents a softlink */
    public boolean isSoftLink() {
        return (fileType == SOFTLINK);
    }

    /** return true if the GridFile represents a device */
    public boolean isDevice() {
        return (fileType == DEVICE);
    }

    /** set mode for the current file. Mode is in the form 777 */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /** return mode as a string of the form 777 */
    public String getMode() {
        return mode;
    }

    /** set permissions for the user */
    public void setUserPermissions(Permissions userPermissions) {
        this.userPermissions = userPermissions;
    }

    /** get permissions for the user */
    public Permissions getUserPermissions() {
        return userPermissions;
    }

    /** set permissions for the group */
    public void setGroupPermissions(Permissions groupPermissions) {
        this.groupPermissions = groupPermissions;
    }

    /** return permissions for the group */
    public Permissions getGroupPermissions() {
        return groupPermissions;
    }

    /** set permissions for all users */
    public void setAllPermissions(Permissions allPermissions) {
        this.allPermissions = allPermissions;
    }

    /** return permissions for all users */
    public Permissions getAllPermissions() {
        return allPermissions;
    }

    /** return true if the user can read from this file */
    public boolean userCanRead() {
        return userPermissions.getRead();
    }

    /** return true if the user can write into this file */
    public boolean userCanWrite() {
        return userPermissions.getWrite();
    }

    /** return true of the user can execute the current file */
    public boolean userCanExecute() {
        return userPermissions.getExecute();
    }

    /** return true if the group members can read from this file */
    public boolean groupCanRead() {
        return groupPermissions.getRead();
    }

    /** return true if the group members can write into this file */
    public boolean groupCanWrite() {
        return groupPermissions.getWrite();
    }

    /** return true of the group members can execute the current file */
    public boolean groupCanExecute() {
        return groupPermissions.getExecute();
    }

    /** return true if all users can read from this file */
    public boolean allCanRead() {
        return allPermissions.getRead();
    }

    /** return true if all users can write into this file */
    public boolean allCanWrite() {
        return allPermissions.getWrite();
    }

    /** return true of all users can execute the current file */
    public boolean allCanExecute() {
        return allPermissions.getExecute();
    }

    /** represents the entire grid file properties as a string */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("GridFile: ");
        buf.append(getName() + " ");
        buf.append(getSize() + " ");
        buf.append(getLastModified() + " ");
        switch (fileType) {
            case DIRECTORY :
                buf.append("directory");
                break;
            case FILE :
                buf.append("file");
                break;
            case SOFTLINK :
                buf.append("softlink");
                break;
            default :
                buf.append("unknown type");
        }
        buf.append(" " + getMode());

        return buf.toString();
    }

}
