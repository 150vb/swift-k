//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Jan 21, 2010
 */
package org.globus.cog.abstraction.impl.file.coaster.handlers.providers;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.log4j.Logger;

/**
 * Sample indirect provider
 * 
 */
public class LocalCopyIOProvider implements IOProvider {
    public static final Logger logger = Logger.getLogger(LocalIOProvider.class);

    public void abort(IOHandle handle) throws IOException {
    }

    public boolean isDirect() {
        return false;
    }

    public IOReader pull(String src, String dest, ReadIOCallback cb) throws IOException {
        return new Reader(src, dest, cb);
    }

    private static class Copy implements Runnable, IOHandle {
        private String src, dest;
        private IOCallback cb;

        public Copy(String src, String dest, IOCallback cb) {
            this.src = src;
            this.dest = dest;
            this.cb = cb;
        }

        public void start() throws IOException {
            new Thread(this).start();
        }

        public void close() {
        }

        public void dataSent() {
        }

        public void abort() throws IOException {
        }

        public void setLength(long len) throws IOException {
        }

        public void write(boolean last, byte[] data) throws IOException {
        }

        protected static String getPath(String suri) throws IOException {
            try {
                URI uri = new URI(suri);
                return uri.getPath().substring(1);
            }
            catch (URISyntaxException e) {
                throw new IOException(e.toString());
            }
        }

        public void run() {
            try {
                int ec = 0;
                if (new File(src).exists()) {
                    new File(dest).getParentFile().mkdirs();
                    Process p = Runtime.getRuntime().exec(new String[] { "cp", src, dest });
                    ec = p.waitFor();
                }
                logger.info(ec + ": " + src + " -> " + dest);
                if (ec != 0) {
                    cb.error(this, new Exception("cp job failed with exit code " + ec));
                }
                else {
                    cb.done(this);
                }
            }
            catch (Exception e) {
                cb.error(this, e);
            }
        }
    }

    private static class Reader extends Copy implements IOReader {
        public Reader(String src, String dest, ReadIOCallback cb) throws IOException {
            super(getPath(src), dest, cb);
        }

        public void resume() {
        	// not needed here
        }

        public void suspend() {
        	// not needed here
        }
    }

    public IOWriter push(String src, String dest, WriteIOCallback cb) throws IOException {
        return new Writer(src, dest, cb);
    }

    private static class Writer extends Copy implements IOWriter {
        public Writer(String src, String dest, WriteIOCallback cb) throws IOException {
            super(src, getPath(dest), cb);
            start();
        }

        public void suspend() {
            // not used
        }

        public void resume() {
            // not used
        }

        public void setUpThrottling() {
            // not used
        }

        public void cancelThrottling() {
            // not used
        }
    }
}
