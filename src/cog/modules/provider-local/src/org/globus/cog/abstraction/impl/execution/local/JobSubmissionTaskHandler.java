//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

package org.globus.cog.abstraction.impl.execution.local;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.impl.common.AbstractDelegatedTaskHandler;
import org.globus.cog.abstraction.impl.common.AbstractionFactory;
import org.globus.cog.abstraction.impl.common.StatusImpl;
import org.globus.cog.abstraction.impl.common.execution.JobException;
import org.globus.cog.abstraction.impl.common.task.IllegalSpecException;
import org.globus.cog.abstraction.impl.common.task.InvalidSecurityContextException;
import org.globus.cog.abstraction.impl.common.task.InvalidServiceContactException;
import org.globus.cog.abstraction.impl.common.task.ServiceContactImpl;
import org.globus.cog.abstraction.impl.common.task.TaskSubmissionException;
import org.globus.cog.abstraction.impl.common.util.NullOutputStream;
import org.globus.cog.abstraction.impl.common.util.OutputStreamMultiplexer;
import org.globus.cog.abstraction.interfaces.CleanUpSet;
import org.globus.cog.abstraction.interfaces.FileLocation;
import org.globus.cog.abstraction.interfaces.FileResource;
import org.globus.cog.abstraction.interfaces.JobSpecification;
import org.globus.cog.abstraction.interfaces.ServiceContact;
import org.globus.cog.abstraction.interfaces.StagingSet;
import org.globus.cog.abstraction.interfaces.StagingSetEntry;
import org.globus.cog.abstraction.interfaces.Status;
import org.globus.cog.abstraction.interfaces.Task;

import edu.emory.mathcs.backport.java.util.concurrent.ExecutorService;
import edu.emory.mathcs.backport.java.util.concurrent.Executors;
import edu.emory.mathcs.backport.java.util.concurrent.ThreadFactory;

/**
 * @author Kaizar Amin (amin@mcs.anl.gov)
 * 
 */
public class JobSubmissionTaskHandler extends AbstractDelegatedTaskHandler implements Runnable {
    private static Logger logger = Logger.getLogger(JobSubmissionTaskHandler.class);

    private static ExecutorService pool =
            Executors.newCachedThreadPool(new DaemonThreadFactory(Executors.defaultThreadFactory()));

    private static final int STDOUT = 0;
    private static final int STDERR = 1;

    public static final int BUFFER_SIZE = 1024;

    private Process process;
    private volatile boolean killed;

    public void submit(Task task) throws IllegalSpecException, InvalidSecurityContextException,
            InvalidServiceContactException, TaskSubmissionException {
        checkAndSetTask(task);
        task.setStatus(Status.SUBMITTING);
        JobSpecification spec;
        try {
            spec = (JobSpecification) task.getSpecification();
        }
        catch (Exception e) {
            throw new IllegalSpecException("Exception while retrieving Job Specification", e);
        }

        if (logger.isDebugEnabled()) {
            logger.debug(spec.toString());
        }

        try {
            int count = 1;
            Object cv = spec.getAttribute("count");
            if (cv != null) {
                count = Integer.parseInt(cv.toString());
            }
            if (logger.isInfoEnabled()) {
                logger.info("Submitting task " + task);
            }
            if (count == 1) {
                logger.info("Submitting single job");
            }
            else {
                logger.info("Submitting " + count + " jobs");
            }
            synchronized (this) {
                if (task.getStatus().getStatusCode() != Status.CANCELED) {
                    for (int i = 0; i < count; i++) {
                        pool.submit(this);
                    }
                    task.setStatus(Status.SUBMITTED);
                    if (spec.isBatchJob()) {
                        task.setStatus(Status.COMPLETED);
                    }
                }
            }
        }
        catch (Exception e) {
            throw new TaskSubmissionException("Cannot submit job", e);
        }
    }

    public void suspend() throws InvalidSecurityContextException, TaskSubmissionException {
    }

    public void resume() throws InvalidSecurityContextException, TaskSubmissionException {
    }

    public void cancel(String message) throws InvalidSecurityContextException,
            TaskSubmissionException {
        synchronized (this) {
            killed = true;
            process.destroy();
            getTask().setStatus(new StatusImpl(Status.CANCELED, message, null));
        }
    }

    private static final FileLocation REDIRECT_LOCATION =
            FileLocation.MEMORY.and(FileLocation.LOCAL);

    public void run() {
        try {
            // TODO move away from the multi-threaded approach
            JobSpecification spec = (JobSpecification) getTask().getSpecification();

            File dir = null;
            if (spec.getDirectory() != null) {
                dir = new File(spec.getDirectory());
            }
            else {
                dir = new File(".");
            }

            stageIn(spec, dir);

            process = Runtime.getRuntime().exec(buildCmdArray(spec), buildEnvp(spec), dir);

            getTask().setStatus(Status.ACTIVE);

            processIN(spec.getStdInput(), dir);

            List pairs = new LinkedList();

            if (!FileLocation.NONE.equals(spec.getStdOutputLocation())) {
                OutputStream os =
                        prepareOutStream(spec.getStdOutput(), spec.getStdOutputLocation(), dir,
                            getTask(), STDOUT);
                if (os != null) {
                    pairs.add(new StreamPair(process.getInputStream(), os));
                }
            }

            if (!FileLocation.NONE.equals(spec.getStdErrorLocation())) {
                OutputStream os =
                        prepareOutStream(spec.getStdError(), spec.getStdErrorLocation(), dir,
                            getTask(), STDERR);
                if (os != null) {
                    pairs.add(new StreamPair(process.getErrorStream(), os));
                }
            }

            Processor p = new Processor(process, pairs);

            if (spec.isBatchJob()) {
                pool.execute(p);
                return;
            }

            p.run();
            int exitCode = p.getExitCode();

            if (logger.isDebugEnabled()) {
                logger.debug("Exit code was " + exitCode);
            }
            if (killed) {
                return;
            }

            if (exitCode == 0) {
                stageOut(spec, dir);
                cleanUp(spec, dir);
                getTask().setStatus(Status.COMPLETED);
            }
            else {
                throw new JobException(exitCode);
            }
        }
        catch (Exception e) {
            if (killed) {
                return;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Exception while running local executable", e);
            }
            failTask(null, e);
        }
    }

    private void cleanUp(JobSpecification spec, File dir) throws TaskSubmissionException {
        CleanUpSet cs = spec.getCleanUpSet();
        if (cs == null || cs.isEmpty()) {
            return;
        }
        Iterator i = cs.iterator();
        while (i.hasNext()) {
            String e = (String) i.next();
            File f = new File(e);
            if (!f.getAbsolutePath().startsWith(dir.getAbsolutePath())) {
                throw new TaskSubmissionException("Cleaning outside of the job directory is not allowed " +
                		"(cleanup entry: " + f.getAbsolutePath() + ", jobdir: " + dir.getAbsolutePath());
            }
            removeRecursively(f);
        }
    }

    private void removeRecursively(File f) throws TaskSubmissionException {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            for (int i = 0; i < files.length; i++) {
                removeRecursively(files[i]);
            }
        }
        else {
            if (f.exists() && !f.delete()) {
                throw new TaskSubmissionException("Failed to remove file: " + f.getAbsolutePath());
            }
        }
    }

    private void stageOut(JobSpecification spec, File dir) throws Exception {
        StagingSet s = spec.getStageOut();
        if (s == null || s.isEmpty()) {
            return;
        }

        getTask().setStatus(Status.STAGE_OUT);
        stage(s, dir);
    }

    private void stage(StagingSet s, File dir) throws Exception {
        Iterator i = s.iterator();
        while (i.hasNext()) {
            StagingSetEntry e = (StagingSetEntry) i.next();
            copy(e.getSource(), e.getDestination(), dir);
        }
    }

    private void stageIn(JobSpecification spec, File dir) throws Exception {
        StagingSet s = spec.getStageIn();
        if (s == null || s.isEmpty()) {
            return;
        }

        getTask().setStatus(Status.STAGE_IN);
        stage(s, dir);
    }

    private void copy(String src, String dest, File dir) throws Exception {
        URI suri = new URI(src);
        URI duri = new URI(dest);

        String srcScheme = defaultToLocal(suri.getScheme());
        String dstScheme = defaultToLocal(duri.getScheme());
        FileResource sres = AbstractionFactory.newFileResource(srcScheme);
        FileResource dres = AbstractionFactory.newFileResource(dstScheme);
        sres.setServiceContact(getServiceContact(suri));
        sres.start();

        dres.setServiceContact(getServiceContact(duri));
        dres.start();

        InputStream is = sres.openInputStream(getPath(suri, dir));
        OutputStream os = dres.openOutputStream(getPath(duri, dir));
        byte[] buffer = new byte[BUFFER_SIZE];

        int len = is.read(buffer);
        while (len != -1) {
            os.write(buffer, 0, len);
            len = is.read(buffer);
        }

        sres.stop();
        dres.stop();
    }

    private String getPath(URI uri, File dir) {
        if (uri.getScheme() == null && !uri.getPath().startsWith("/")) {
            return new File(dir, uri.getPath()).getAbsolutePath();
        }
        else {
            return uri.getPath();
        }
    }

    private ServiceContact getServiceContact(URI uri) {
        ServiceContact sc = new ServiceContactImpl();
        if (uri.getHost() != null) {
            sc.setHost(uri.getHost());
        }
        if (uri.getPort() != -1) {
            sc.setPort(uri.getPort());
        }
        return sc;
    }

    private String defaultToLocal(String scheme) {
        return scheme == null ? "local" : scheme;
    }

    protected void processIN(String in, File dir) throws IOException {

        byte[] buf = new byte[BUFFER_SIZE];
        if (in != null) {
            OutputStream out = process.getOutputStream();

            File stdin;
            if (dir != null) {
                stdin = new File(dir, in);
            }
            else {
                stdin = new File(in);
            }

            FileInputStream file = new FileInputStream(stdin);
            int read = file.read(buf);
            while (read != -1) {
                out.write(buf, 0, read);
                read = file.read(buf);
            }
            file.close();
            out.close();
        }
    }

    protected OutputStream prepareOutStream(String out, FileLocation loc, File dir, Task task,
            int stream) throws IOException {

        OutputStream os = null;
        TaskOutputStream baos = null;
        if (FileLocation.MEMORY.overlaps(loc)) {
            baos = new TaskOutputStream(task, stream);
            os = baos;
        }
        if ((FileLocation.LOCAL.overlaps(loc) || FileLocation.REMOTE.equals(loc)) && out != null) {
            if (os != null) {
                os = new OutputStreamMultiplexer(os, new FileOutputStream(out));
            }
            else {
                os = new FileOutputStream(out);
            }
        }
        if (os == null) {
            os = new NullOutputStream();
        }

        return os;
    }

    private String[] buildCmdArray(JobSpecification spec) {
        List arguments = spec.getArgumentsAsList();
        String[] cmdarray = new String[arguments.size() + 1];

        cmdarray[0] = spec.getExecutable();
        Iterator i = arguments.iterator();
        int index = 1;
        while (i.hasNext()) {
            cmdarray[index++] = (String) i.next();
        }
        return cmdarray;
    }

    private String[] buildEnvp(JobSpecification spec) {
        Collection names = spec.getEnvironmentVariableNames();
        if (names.size() == 0) {
            /*
             * Questionable. An envp of null will cause the parent environment
             * to be inherited, while an empty one will cause no environment
             * variables to be set for the process. Or so it seems from the
             * Runtime.exec docs.
             */
            return null;
        }
        String[] envp = new String[names.size()];
        Iterator i = names.iterator();
        int index = 0;
        while (i.hasNext()) {
            String name = (String) i.next();
            envp[index++] = name + "=" + spec.getEnvironmentVariable(name);
        }
        return envp;
    }

    private static class StreamPair {
        public InputStream is;
        public OutputStream os;

        public StreamPair(InputStream is, OutputStream os) {
            this.is = is;
            this.os = os;
        }
    }

    private static class TaskOutputStream extends ByteArrayOutputStream {
        public static final int MAX_SIZE = 8192;
        private Task task;
        private int stream;

        public TaskOutputStream(Task task, int stream) {
            this.task = task;
            this.stream = stream;
        }

        public void close() throws IOException {
            super.close();
            String value = toString();
            if (logger.isDebugEnabled()) {
                logger.debug((stream == STDOUT ? "STDOUT" : "STDERR") + " from job: " + value);
            }
            if (stream == STDOUT) {
                task.setStdOutput(value);
            }
            else {
                task.setStdError(value);
            }
        }

        public synchronized void write(byte[] b, int off, int len) {
            super.write(b, off, len);
            checkSize();
        }

        public synchronized void write(int b) {
            super.write(b);
            checkSize();
        }

        private void checkSize() {
            if (count > MAX_SIZE) {
                System.arraycopy(buf, MAX_SIZE / 2, buf, 0, MAX_SIZE / 2);
                count = MAX_SIZE / 2;
            }
        }
    }

    private static class Processor implements Runnable {
        private Process p;
        private List streamPairs;
        byte[] buf;

        public Processor(Process p, List streamPairs) {
            this.p = p;
            this.streamPairs = streamPairs;
        }

        public void run() {
            try {
                run2();
            }
            catch (Exception e) {
                logger.warn("Exception caught while running process", e);
            }
        }

        public void run2() throws IOException, InterruptedException {
            IOException e = null;
            while (true) {
                boolean any;
                try {
                    any = processPairs();
                }
                catch (IOException ex) {
                    e = ex;
                    any = false;
                }
                if (processDone()) {
                    processPairs();
                    closePairs();
                    return;
                }
                else {
                    if (e != null) {
                        p.destroy();
                        throw e;
                    }
                    if (!any) {
                        Thread.sleep(20);
                    }
                }
            }
        }

        private boolean processPairs() throws IOException {
            boolean any = false;
            Iterator i = streamPairs.iterator();
            while (i.hasNext()) {
                if (buf == null) {
                    buf = new byte[BUFFER_SIZE];
                }
                StreamPair sp = (StreamPair) i.next();
                int avail = sp.is.available();
                if (avail > 0) {
                    any = true;
                    int len = sp.is.read(buf);
                    sp.os.write(buf, 0, len);
                }
            }
            return any;
        }

        private boolean processDone() {
            try {
                p.exitValue();
                return true;
            }
            catch (IllegalThreadStateException e) {
                return false;
            }
        }

        private void closePairs() throws IOException {
            Iterator i = streamPairs.iterator();
            while (i.hasNext()) {
                StreamPair sp = (StreamPair) i.next();
                sp.os.close();
                sp.is.close();
            }
        }

        public int getExitCode() {
            return p.exitValue();
        }
    }

    static class DaemonThreadFactory implements ThreadFactory {
        private ThreadFactory delegate;

        public DaemonThreadFactory(ThreadFactory delegate) {
            this.delegate = delegate;
        }

        public Thread newThread(Runnable r) {
            Thread t = delegate.newThread(r);
            t.setDaemon(true);
            return t;
        }

    }
}
