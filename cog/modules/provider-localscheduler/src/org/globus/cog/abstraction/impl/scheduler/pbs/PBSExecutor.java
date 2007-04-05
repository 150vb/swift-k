//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Oct 11, 2005
 */
package org.globus.cog.abstraction.impl.scheduler.pbs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.impl.scheduler.common.Job;
import org.globus.cog.abstraction.impl.scheduler.common.ProcessException;
import org.globus.cog.abstraction.impl.scheduler.common.ProcessListener;
import org.globus.cog.abstraction.interfaces.JobSpecification;
import org.globus.cog.abstraction.interfaces.Task;
import org.globus.gsi.gssapi.auth.AuthorizationException;
import org.ietf.jgss.GSSException;

public class PBSExecutor implements ProcessListener {
    public static final Logger logger = Logger.getLogger(PBSExecutor.class);

    private JobSpecification spec;
    private Task task;
    private static QueuePoller poller;
    private ProcessListener listener;
    private String stdout, stderr, exitcode;
    private File script;

    public PBSExecutor(Task task, ProcessListener listener) {
        this.task = task;
        this.spec = (JobSpecification) task.getSpecification();
        this.listener = listener;
    }

    private static synchronized QueuePoller getProcessPoller() {
        if (poller == null) {
            poller = new QueuePoller();
            poller.start();
        }
        return poller;
    }

    public void start() throws AuthorizationException, GSSException,
            IOException, ProcessException {
        File scriptdir = new File(System.getProperty("user.home")
                + File.separatorChar + ".globus" + File.separatorChar
                + "scripts");
        scriptdir.mkdirs();
        if (!scriptdir.exists()) {
            throw new IOException("Failed to create script directory ("
                    + scriptdir + ")");
        }
        script = File.createTempFile("pbs", ".qsub", scriptdir);
        stdout = spec.getStdOutput() == null ? script.getAbsolutePath()
                + ".stdout" : spec.getStdOutput();
        stderr = spec.getStdError() == null ? script.getAbsolutePath()
                + ".stderr" : spec.getStdError();
        exitcode = script.getAbsolutePath() + ".exitcode";
        writePBSScript(new BufferedWriter(new FileWriter(script)), exitcode,
                stdout, stderr);
        if (logger.isDebugEnabled()) {
            logger.debug("Wrote PBS script to " + script);
        }

        String[] cmdline = new String[] { Properties.getProperties().getQSub(),
                script.getAbsolutePath() };
        if (logger.isDebugEnabled()) {
            logger.debug(cmdline[0]+" "+cmdline[1]);
        }
        Process process = Runtime.getRuntime().exec(cmdline, null, null);

        try {
            process.getOutputStream().close();
        }
        catch (IOException e) {
        }

        try {
            int code = process.waitFor();
            if (code != 0) {
                throw new ProcessException(
                        "Could not submit job (qsub reported an exit code of "
                                + code + "). "
                                + getOutput(process.getErrorStream()));
            }
            if (logger.isDebugEnabled()) {
                logger.debug("QSub done (exit code " + code + ")");
            }
        }
        catch (InterruptedException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Interrupted exception while waiting for qsub", e);
            }
            if (listener != null) {
                listener
                        .processFailed("The submission process was interrupted");
            }
        }

        String jobid = getOutput(process.getInputStream());

        getProcessPoller().addJob(
                new Job(jobid, spec.isRedirected() ? stdout : null, spec
                        .isRedirected() ? stderr : null, exitcode, this));
    }

    private void error(String message) {
        listener.processFailed(message);
    }

    protected void writeAttr(String attrName, String arg, Writer wr)
            throws IOException {
        Object value = spec.getAttribute(attrName);
        if (value != null) {
            wr.write("#PBS " + arg + String.valueOf(value) + '\n');
        }
    }

    protected void writePBSScript(Writer wr, String exitcodefile,
            String stdout, String stderr) throws IOException {
        wr.write("#PBS -S /bin/sh\n");
        wr.write("#PBS -N " + task.getName() + '\n');
        wr.write("#PBS -m n\n");
        writeAttr("count", "-l nodes=", wr);
        writeAttr("maxwalltime", "-l walltime=", wr);
        writeAttr("queue", "-q ", wr);
        if (spec.getDirectory() != null) {
            wr.write("#PBS -d " + quote(spec.getDirectory()) + '\n');
        }
        if (spec.getStdInput() != null) {
            throw new IOException("The PBSlocal provider cannot redirect STDIN");
        }
        wr.write("#PBS -o " + quote(stdout) + '\n');
        wr.write("#PBS -e " + quote(stderr) + '\n');
        Iterator i = spec.getEnvironmentVariableNames().iterator();
        while (i.hasNext()) {
            String name = (String) i.next();
            wr.write(name);
            wr.write('=');
            wr.write(quote(spec.getEnvironmentVariable(name)));
            wr.write('\n');
        }
        wr.write(quote(spec.getExecutable()));
        List args = spec.getArgumentsAsList();
        if (args != null && args.size() > 0) {
            wr.write(' ');
            i = args.iterator();
            while (i.hasNext()) {
                wr.write(quote((String) i.next()));
                if (i.hasNext()) {
                    wr.write(' ');
                }
            }
        }
        wr.write('\n');

        wr.write("/bin/echo $? >" + exitcodefile + '\n');
        wr.close();
    }

    protected String quote(String s) {
        if ("".equals(s)) {
            return "\"\"";
        }
        boolean quotes = false;
        if (s.indexOf(' ') != -1) {
            quotes = true;
        }
        StringBuffer sb = new StringBuffer();
        if (quotes) {
            sb.append('"');
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
                break;
            }
            sb.append(c);
        }
        if (quotes) {
            sb.append('"');
        }
        return sb.toString();
    }

    protected String getOutput(InputStream is) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Waiting for output from qsub");
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuffer sb = new StringBuffer();
        String out = br.readLine();
        if (logger.isDebugEnabled()) {
            logger.debug("Output from qsub is: \"" + out + "\"");
        }
        if ("".equals(out)) {
            throw new IOException("Qsub returned empty job ID");
        }
        return out;
    }

    protected void cleanup() {
        script.delete();
        new File(exitcode).delete();
        if (spec.getStdOutput() == null && stdout != null) {
            new File(stdout).delete();
        }
        if (spec.getStdError() == null && stderr != null) {
            new File(stderr).delete();
        }
    }

    public void processCompleted(int exitCode) {
        cleanup();
        if (listener != null) {
            listener.processCompleted(exitCode);
        }
    }

    public void processFailed(String message) {
        cleanup();
        if (listener != null) {
            listener.processFailed(message);
        }
    }

    public void statusChanged(int status) {
        if (listener != null) {
            listener.statusChanged(status);
        }
    }

    public void stderrUpdated(String stderr) {
        if (listener != null) {
            listener.stderrUpdated(stderr);
        }
    }

    public void stdoutUpdated(String stdout) {
        if (listener != null) {
            listener.stdoutUpdated(stdout);
        }
    }

    public void processFailed(Exception e) {
        cleanup();
        if (listener != null) {
            listener.processFailed(e);
        }
    }
}
