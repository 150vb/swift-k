//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Jun 21, 2005
 */
package org.globus.cog.karajan.util;

import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

public class KarajanProperties extends Properties {
	private static final long serialVersionUID = 1198467724006509622L;

	private static final Logger logger = Logger.getLogger(KarajanProperties.class);

	private static KarajanProperties def, restricted;

	private final List defaultIncludeDirs;

	public synchronized static KarajanProperties getDefault() {
		if (def == null) {
			def = parseProperties();
		}
		return def;
	}
	
	public synchronized static KarajanProperties getRestricted() {
		if (restricted == null) {
			restricted = parseRestrictedProperties();
		}
		return restricted;
	}


	protected static KarajanProperties parseProperties(String name, KarajanProperties properties)
			throws Exception {
		URL url = KarajanProperties.class.getClassLoader().getResource(name);
		if (properties == null) {
			properties = new KarajanProperties();
		}
		if (url != null) {
			properties.load(url.openStream());
		}
		else {
			throw new Exception("Invalid resource: " + name);
		}
		properties.defaultIncludeDirs.clear();
		Enumeration e = properties.propertyNames();
		while (e.hasMoreElements()) {
			String propName = (String) e.nextElement();
			if (propName.equals("include.dirs")) {
				properties.addDefaultIncludeDirs(properties.getProperty(propName));
			}
		}
		return properties;
	}

	protected static KarajanProperties parseProperties() {
		KarajanProperties props = null;
		try {
			props = parseProperties("karajan-default.properties", props);
		}
		catch (Exception e) {
			logger.warn("Failed to load default properties", e);
		}
		try {
			props = parseProperties("karajan.properties", props);
		}
		catch (Exception e) {
			logger.debug("Failed to load properties", e);
		}
		if (props == null) {
			return new KarajanProperties();
		}
		else {
			return props;
		}
	}
	
	protected static KarajanProperties parseRestrictedProperties() {
		KarajanProperties props = null;
		try {
			props = parseProperties("karajan-default.properties", props);
		}
		catch (Exception e) {
			logger.warn("Failed to load default properties", e);
		}
		try {
			props = parseProperties("karajan-restricted.properties", props);
		}
		catch (Exception e) {
			logger.error("Failed to load restricted properties", e);
			throw new RuntimeException("Failed to load restricted properties");
		}
		return props;
	}

	public KarajanProperties() {
		this.defaultIncludeDirs = new LinkedList();
	}

	public List getDefaultIncludeDirs() {
		return defaultIncludeDirs;
	}

	public void addDefaultIncludeDir(String dir) {
		defaultIncludeDirs.add(dir);
	}

	public void addDefaultIncludeDirs(String dirs) {
		StringTokenizer tokenizer = new StringTokenizer(dirs, ":");
		while (tokenizer.hasMoreTokens()) {
			addDefaultIncludeDir(tokenizer.nextToken());
		}
	}

	public void insertDefaultIncludeDir(String dir) {
		defaultIncludeDirs.add(0, dir);
	}

	public void removeDefaultIncludeDir(String dir) {
		defaultIncludeDirs.remove(dir);
	}

}
