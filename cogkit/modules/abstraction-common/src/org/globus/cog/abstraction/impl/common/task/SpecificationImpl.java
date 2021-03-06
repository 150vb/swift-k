/*
 * Swift Parallel Scripting Language (http://swift-lang.org)
 * Code from Java CoG Kit Project (see notice below) with modifications.
 *
 * Copyright 2005-2014 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// ----------------------------------------------------------------------
// This code is developed as part of the Java CoG Kit project
// The terms of the license can be found at http://www.cogkit.org/license
// This message may not be removed or altered.
// ----------------------------------------------------------------------



package org.globus.cog.abstraction.impl.common.task;

import org.globus.cog.abstraction.interfaces.Specification;

public class SpecificationImpl implements Specification, Cloneable {

    private static final long serialVersionUID = 1L;
    private int type;
    private String specification;

    public SpecificationImpl(int type) {
        this.type = type;
    }

    public SpecificationImpl(int type, String specification) {
        this.type = type;
        this.specification = specification;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getType() {
        return this.type;
    }

    public void setSpecification(String specification) {
        this.specification = specification;
    }

    public String getSpecification() {
        return this.specification;
    }
    
    public Object clone() {
        SpecificationImpl result = null;
        try {
            result = (SpecificationImpl) super.clone();
        }
        catch (CloneNotSupportedException e) {
            e.printStackTrace();
        } 
        return result;
    }

    @Override
    public Object getAttribute(String name) {
        return null;
    }
}
