/*
 * Copyright 2012 University of Chicago
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.griphyn.vdl.mapping.file;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.griphyn.vdl.mapping.AbsFile;
import org.griphyn.vdl.mapping.AbstractMapper;
import org.griphyn.vdl.mapping.DSHandle;
import org.griphyn.vdl.mapping.InvalidPathException;
import org.griphyn.vdl.mapping.MappingParam;
import org.griphyn.vdl.mapping.Path;
import org.griphyn.vdl.mapping.PhysicalFormat;

public class ArrayFileMapper extends AbstractMapper {
    static Logger logger = Logger.getLogger(ArrayFileMapper.class);
    
	public static final MappingParam PARAM_FILES = new MappingParam("files");
	
	@Override
    protected void getValidMappingParams(Set<String> s) {
	    addParams(s, PARAM_FILES);
        super.getValidMappingParams(s);
    }

	public Collection<Path> existing() {
		List<Path> l = new ArrayList<Path>();
		DSHandle dn = (DSHandle) PARAM_FILES.getRawValue(this);
		if (dn == null) {
		    throw new RuntimeException("Missing 'files' mapper attribute");
		}
		Map<?, DSHandle> m = dn.getArrayValue();
		Set<?> s = m.keySet();
		Iterator<?> i = s.iterator();
		while(i.hasNext()) {
			Comparable<?> nextKey = (Comparable<?>) i.next();
			l.add(Path.EMPTY_PATH.addLast(nextKey, true));
		}
		return l;
	}

	public PhysicalFormat map(Path path) {
		if (path.isEmpty()) {
			throw new IllegalArgumentException("Path cannot be empty");
		}
		if (!path.isArrayIndex(0)) {
			throw new IllegalArgumentException("First element of path "+path.toString()+" must be an array index");
		}
        // we could typecheck more elegantly here to make sure that
        // we really do have an array of strings as parameter.
        DSHandle dn = (DSHandle) PARAM_FILES.getRawValue(this);
        assert(dn.isClosed());
        logger.debug("dn: " + dn);
        
        DSHandle srcNode = null;
        try {
        	srcNode = dn.getField(path);
        } catch(InvalidPathException e) {
        	logger.error("Invalid path exception "+e+" for path "+path,e);
        	return null;
        }
        String returnValue = srcNode.getValue().toString();
        return new AbsFile(returnValue);
	}

	public boolean isStatic() {
		return true;
	}
	
	public String toString() { 
	    return "ArrayFileMapper";
	}
}
