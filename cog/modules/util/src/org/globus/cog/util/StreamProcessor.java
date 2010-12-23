package org.globus.cog.util;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;

import org.apache.log4j.Logger;

/**
 * Utility class to read a stream and report when a pattern is found
 * @author wozniak
 */
public class StreamProcessor extends Streamer {

    public static final Logger logger = Logger.getLogger(StreamProcessor.class);

    /**
       Object to notify when pattern is found
     */
    Object object;

    String pattern;

    boolean matched = false;

    public StreamProcessor(InputStream istream, OutputStream ostream,
                           Object object, String pattern) {
        super(istream, ostream);
        this.object = object;
        this.pattern = pattern;

        setName("StreamProcessor["+pattern+"]");
        logger.debug(getName());
    }

    public void run() {
        status = Status.ACTIVE;
        matched = false;

        BufferedReader reader =
            new BufferedReader(new InputStreamReader(istream));
        PrintStream writer =
            new PrintStream(new BufferedOutputStream(ostream));

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.contains(pattern)) {
                    writer.flush();
                    matched = true;
                    synchronized(object) {
                        object.notify();
                        break;
                    }
                }
                else
                    writer.println(line);
            }
            writer.flush();
        }
        catch (IOException e) {
            status = Status.FAILED;
            e.printStackTrace();
        }
        status = Status.COMPLETED;
    }

    public boolean matched() {
        return matched;
    }
}
