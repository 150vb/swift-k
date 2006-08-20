// ----------------------------------------------------------------------
// This code is developed as part of the Java CoG Kit project
// The terms of the license can be found at http://www.cogkit.org/license
// This message may not be removed or altered.
// ----------------------------------------------------------------------

package org.globus.cog.abstraction.interfaces;

/**
 * An abstract representation of file or directory in the remote resource.
 * Carries properties and access rights information of the file.
 */
public interface GridFile {
    public static final byte UNKNOWN = 0;
    public static final byte FILE = 1;
    public static final byte DIRECTORY = 2;
    public static final byte SOFTLINK = 3;
    public static final byte DEVICE = 4;

    /** set size of the file */
    public void setSize(long size);

    /** get size of the file */
    public long getSize();

    /** set name of the file */
    public void setName(String name);

    /** get name of the file */
    public String getName();

    /** set absolute path name of the file */
    public void setAbsolutePathName(String name);

    /** return absolute path name of the file */
    public String getAbsolutePathName();

    /** set last modified date of the file */
    public void setLastModified(String date);

    /** return last modified date */
    public String getLastModified();

    /** set file type */
    public void setFileType(byte type);

    /** return file type */
    public byte getFileType();

    /** return true if it is a file */
    public boolean isFile();

    /** return true if it is a directory */
    public boolean isDirectory();

    /** return true if it is soft link */
    public boolean isSoftLink();

    /** return true if it is device */
    public boolean isDevice();

    /** set mode of the file */
    public void setMode(String mode);

    /** return mode of the file */
    public String getMode();

    /** set permissions for the user */
    public void setUserPermissions(Permissions userPermissions);

    /** get permissions of the user */
    public Permissions getUserPermissions();

    /** set permissions for the group users */
    public void setGroupPermissions(Permissions groupPermissions);

    /** get permissions of the group users */
    public Permissions getGroupPermissions();

    /** set permissions for all users */
    public void setAllPermissions(Permissions allPermissions);

    /** get permissions of all users */
    public Permissions getAllPermissions();

    /** return true if the user can read the file */
    public boolean userCanRead();

    /** return true if the user can write into the file */
    public boolean userCanWrite();

    /** return true if the user can execute the file */
    public boolean userCanExecute();

    /** return true if the group can read from the file */
    public boolean groupCanRead();

    /** return true if the group can write into the file */
    public boolean groupCanWrite();

    /** return true if the group can execute the file */
    public boolean groupCanExecute();

    /** return true if all users can read from the file */
    public boolean allCanRead();

    /** return true if all users can write into the file */
    public boolean allCanWrite();

    /** return true if all users can execute the file */
    public boolean allCanExecute();

}