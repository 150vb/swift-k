//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Feb 12, 2008
 */
package org.globus.cog.abstraction.impl.execution.coaster;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.apache.log4j.Logger;
import org.globus.cog.abstraction.coaster.service.SubmitJobHandler;
import org.globus.cog.abstraction.coaster.service.job.manager.Settings;
import org.globus.cog.abstraction.impl.common.execution.WallTime;
import org.globus.cog.abstraction.interfaces.ExecutionService;
import org.globus.cog.abstraction.interfaces.FileLocation;
import org.globus.cog.abstraction.interfaces.JobSpecification;
import org.globus.cog.abstraction.interfaces.Service;
import org.globus.cog.abstraction.interfaces.StagingSetEntry;
import org.globus.cog.abstraction.interfaces.StagingSetEntry.Mode;
import org.globus.cog.abstraction.interfaces.Task;
import org.globus.cog.coaster.ProtocolException;
import org.globus.cog.coaster.commands.Command;

public class SubmitJobCommand extends Command {
    public static final Logger logger = Logger.getLogger(SubmitJobCommand.class);

    public static final String NAME = "SUBMITJOB";

    public static final Set<String> IGNORED_ATTRIBUTES;

    private String id;
    
    static {
        IGNORED_ATTRIBUTES = new HashSet<String>();
        for (int i = 0; i < Settings.NAMES.length; i++) {
            IGNORED_ATTRIBUTES.add(Settings.NAMES[i].toLowerCase());
        }
    }
    
    public static final Set<String> ABSOLUTIZE = new HashSet<String>() {
        {add("sfs");}
    };

    private Task t;
    private boolean compression = SubmitJobHandler.COMPRESSION;
    private boolean simple;

    public boolean getCompression() {
        return compression;
    }

    public void setCompression(boolean compression) {
        this.compression = compression;
    }

    public SubmitJobCommand(Task t) {
        super(NAME);
        this.t = t;
    }

    public void send() throws ProtocolException {
        try {
            serialize();
        }
        catch (Exception e) {
            throw new ProtocolException("Could not serialize job specification", e);
        }
        super.send();
    }

    protected void serialize() throws IOException {
        // I'd use Java serialization if not for the fact that a similar
        // thing needs to be done to communicate with the perl client
        JobSpecification spec = (JobSpecification) t.getSpecification();
        StringBuilder sb = new StringBuilder();
        
        String identity = t.getIdentity().getValue();
        add(sb, "identity", identity);
        add(sb, "executable", spec.getExecutable());
        add(sb, "directory", spec.getDirectory());
        if (!simple) {
            add(sb, "batch", spec.isBatchJob());
        }
        add(sb, "stdin", spec.getStdInput());
        add(sb, "stdout", spec.getStdOutput());
        add(sb, "stderr", spec.getStdError());
        
        if (spec.isRedirected() || 
                spec.getStdOutputLocation().overlaps(FileLocation.MEMORY) || 
                spec.getStdErrorLocation().overlaps(FileLocation.MEMORY)) {
            add(sb, "redirect", true);
        }

        for (String arg : spec.getArgumentsAsList())
            add(sb, "arg", arg);

        for (String name : spec.getEnvironmentVariableNames())
            add(sb, "env", 
                name + "=" + spec.getEnvironmentVariable(name));
    
        if (simple) {
        	add(sb, "attr", "maxwalltime=" + formatWalltime(spec.getAttribute("maxwalltime")));
        	if (spec.getAttribute("tracePerformance") != null) {
        	    add(sb, "attr", "tracePerformance=" + spec.getAttribute("tracePerformance"));
        	}
        }
        else {
            for (String name : spec.getAttributeNames())
                if (!IGNORED_ATTRIBUTES.contains(name) || 
                        spec.isBatchJob())
                    add(sb, "attr", 
                        name + "=" + spec.getAttribute(name));
        }
            
        if (spec.getStageIn() != null) {
            for (StagingSetEntry e : spec.getStageIn())
                add(sb, "stagein", absolutize(e.getSource()) + '\n' + 
                    e.getDestination() + '\n' + Mode.getId(e.getMode()));
        }
        
        if (spec.getStageOut() != null) {
            for (StagingSetEntry e : spec.getStageOut())
                add(sb, "stageout", e.getSource() + '\n' + 
                    absolutize(e.getDestination()) + '\n' + Mode.getId(e.getMode()));
        }

        if (spec.getCleanUpSet() != null)
            for (String cleanup : spec.getCleanUpSet())
                add(sb, "cleanup", cleanup);

        if (!simple) {
            Service s = t.getService(0);
            add(sb, "contact", s.getServiceContact().toString());
            add(sb, "provider", s.getProvider());
    
            if (s instanceof ExecutionService) {
                add(sb, "jm", ((ExecutionService) s).getJobManager());
            }
            else {
                add(sb, "jm", "fork");
            }
        }
        
        String out = sb.toString();
        if (logger.isDebugEnabled()) {
            logger.debug("Job data: " + out);
        }
        
        byte[] bytes = out.getBytes(UTF8);

        if (compression) {
        	ByteArrayOutputStream baos = new ByteArrayOutputStream();
        	DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(2));
        	dos.write(bytes);
        	bytes = baos.toByteArray();
        }
        addOutData(bytes);
    }

    private String formatWalltime(Object value) {
        if (value == null) {
        	return "600";
        }
        else {
        	return String.valueOf(new WallTime(value.toString()).getSeconds());
        }
    }

    private String absolutize(String file) throws IOException {
        try {
            URL u = new URL(file);
            if (ABSOLUTIZE.contains(u.getProtocol())) {
                return u.getProtocol() + "://" + u.getHost() + 
                    (u.getPort() != -1 ? ":" + u.getPort() : "") + "/" + new File(u.getPath().substring(1)).getAbsolutePath(); 
            }
            else {
                return file;
            }
        }
        catch (MalformedURLException e) {
            throw new IOException("Invalid file specification: " + file);
        }
    }

    private void add(StringBuilder sb, String key, boolean value) throws IOException {
        add(sb, key, String.valueOf(value));
    }
    
    private static final Charset UTF8 = Charset.forName("UTF-8");

    @SuppressWarnings("fallthrough")
    private void add(final StringBuilder sb, final String key, final String value) throws IOException {
        if (value != null) {
        	sb.append(key);
            sb.append('=');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\n':
                        c = 'n';
                    case '\\':
                        sb.append('\\');
                    default:
                        sb.append(c);
                }
            }

            sb.append('\n');
        }
    }

    public void receiveCompleted() {
        id = getInDataAsString(0);
        super.receiveCompleted();
    }

    public Task getTask() {
        return t;
    }

    public boolean getSimple() {
        return simple;
    }

    public void setSimple(boolean simple) {
        this.simple = simple;
    }
    
    public static void main(String[] args) {
        try {
            URL u = new URL("/some path");
            System.out.println(u.getProtocol());
            System.out.println(u.getHost());
            System.out.println(u.getPort());
            System.out.println(u.getPath());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
