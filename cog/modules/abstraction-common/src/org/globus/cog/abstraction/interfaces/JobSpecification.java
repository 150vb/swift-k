// ----------------------------------------------------------------------
// This code is developed as part of the Java CoG Kit project
// The terms of the license can be found at http://www.cogkit.org/license
// This message may not be removed or altered.
// ----------------------------------------------------------------------

package org.globus.cog.abstraction.interfaces;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Vector;

/**
 * The <code>JobSpecification</code> represents all the parameters required
 * for the remote job execution <code>Task</code>.
 */
public interface JobSpecification extends Specification {

    /**
     * Sets the name of the executable to be run remotely.
     * 
     * @param executable
     *            a string representing the absolute location of the executable.
     */
    public void setExecutable(String executable);

    /**
     * Returns the absolute location of the executable
     */
    public String getExecutable();

    /**
     * Sets the working directory on the remote machine.
     * 
     * @param directory
     *            a string representing the absolute path name of the remote
     *            working directory.
     */
    public void setDirectory(String directory);

    /**
     * Returns the absolute path name of the remote working directory.
     */
    public String getDirectory();

    /**
     * Sets the comandline arguments for the remote executable. A set of space
     * seperated arguments can be supplied: "arg1 arg2 agr3 ..."
     * 
     * @param arguments
     *            a string representing the set of arguments for the remote
     *            executable.
     */
    public void setArguments(String arguments);

    /**
     * Sets the comandline arguments for the remote executable.
     * 
     * @param arguments
     *            a Vector representing the set of arguments for the remote
     *            executable.
     */
    public void setArguments(Vector arguments);

    /**
     * Returns the set of space-separated arguments supplied for the remote
     * executable.
     */
    public String getArguments();

    /**
     * Returns the set of space-separated arguments supplied for the remote
     * executable.
     */
    public String getArgumentsAsString();

    /**
     * Adds a commandline argument for the remote exectable. Multiple
     * commandline arguments can be set by making multiple calls to this
     * function.
     * 
     * @param argument
     *            a string representing an argument for the remote executable.
     */
    public void addArgument(String argument);

    /**
     * Adds a commandline argument for the remote exectable at the given index.
     * Multiple commandline arguments can be set by making multiple calls to
     * this function.
     * 
     * @param index
     *            the index in the argument list
     * @param argument
     *            a string representing an argument for the remote executable.
     * 
     */
    public void addArgument(int index, String argument);

    /**
     * Removes the given argument from the argument list
     * 
     * @param argument
     *            the String argument to be removed
     */
    public void removeArgument(String argument);

    /**
     * Removes the argument at the given index from the argument list
     * 
     * @param index
     *            the index of the argument to be removed
     */
    public String removeArgument(int index);

    /**
     * Retruns a Vector representing the set of commandline arguments for the
     * executable.
     * 
     */
    public Vector getArgumentsAsVector();

    /**
     * Adds an environment variable to the remote execution environment.
     * Multiple environment variables can be created by making multiple calls to
     * this method.
     * 
     * @param name
     *            the name of the environment variable
     * @param value
     *            the value of the environment variable
     */
    public void addEnvironmentVariable(String name, String value);

    /**
     * Removes the environment variable with the given name from the remote
     * execution environment.
     * 
     * @param name
     *            the name of the environment variable
     * @return the value of the environment variable
     */
    public String removeEnvironmentVariable(String name);

    /**
     * Returns the environment variable with the given name.
     * 
     * @param name
     *            the name of the environment variable
     * @return the value of the environment variable
     */
    public String getEnvironmentVariable(String name);

    /**
     * Returns a collection representing all the environment variable names
     * associated with the remote execution environment.
     */
    public Collection getEnvironment();

    /**
     * Sets the file for redirecting the output produced on the stdout of the
     * remote machine.
     * 
     * @param output
     *            a string representing the file for redirecting the remote
     *            stdout.
     */
    public void setStdOutput(String output);

    /**
     * Returns the file used for redirecting the output produced on the stdout
     * of the remote machine.
     */
    public String getStdOutput();

    /**
     * Sets the file from which to redirect the data as stdin on the remote
     * machine.
     * 
     * @param output
     *            a string representing the file for stdin
     */
    public void setStdInput(String input);

    /**
     * Returns the file used as stdin on the remote machine.
     */
    public String getStdInput();

    /**
     * Sets the file for redirecting the error produced on the stderr of the
     * remote machine.
     * 
     * @param error
     *            a string representing the file for redirecting the remote
     *            error.
     */
    public void setStdError(String error);

    /**
     * Returns the file used for redirecting the error produced on the stderr of
     * the remote machine.
     */
    public String getStdError();

    /**
     * Specifies that the <code>Task</code> is to be executed as a batch job.
     * If it is a batch job, then the client machine will not be notified
     * regarding the stautus of the remote execution. From the client's
     * perspective, the <code>Task</code> is completed as soon as it is
     * submitted remotely. The execution status and the output/error must be
     * retrieved by the user in an offline fashion.
     * 
     * @param bool
     *            a boolean value indicating if the <code>Task</code> is a
     *            batch job.
     */
    public void setBatchJob(boolean bool);

    /**
     * Chacks if the <code>Task</code> is to be executed as a batch job.
     */
    public boolean isBatchJob();

    /**
     * Specifies if the stdout and stderr have to be redirected.
     * <p>
     * If filename for the <code>setStdOutput</code> in this
     * <code>Specification</code> is <code>null</code> and the
     * <code>setRedirected</code> is <code>true</code>, then the remote
     * stdout is redirected to the local machine and can be retrieved from the
     * <code>getOutput</code> method of the container {@link Task}.
     * 
     */
    public void setRedirected(boolean bool);

    /**
     * Checks if the stdout and stderror is redirected to the local machine.
     */
    public boolean isRedirected();

    /**
     * Specifies that the stdin must be staged-in from the local machine.
     */
    public void setLocalInput(boolean bool);

    /**
     * Checks if the stdin is staged-in from the local machine.
     */
    public boolean isLocalInput();

    /**
     * Specifies that the executable must be staged-in from the local machine.
     */
    public void setLocalExecutable(boolean bool);

    /**
     * Checks if the executable is staged-in from the local machine.
     */
    public boolean isLocalExecutable();

    public void setAttribute(String name, Object value);

    public Object getAttribute(String name);

    public Enumeration getAllAttributes();

    /**
     * Queries whether delegation is enabled for this job
     */
    public boolean isDelegationEnabled();

    /**
     * Enables credential delegation for this job. Not all providers may support
     * credential delegation. Delegation is disabled by default.
     */
    public void setDelegationEnabled(boolean delegation);
}