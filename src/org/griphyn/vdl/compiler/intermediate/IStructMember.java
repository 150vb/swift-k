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

/*
 * Created on Sep 3, 2015
 */
package org.griphyn.vdl.compiler.intermediate;

import org.antlr.stringtemplate.StringTemplate;

public class IStructMember extends AbstractINode {
    private String name;
    private String type;

    public IStructMember() {
        super();
    }
   

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }


    @Override
    protected void setTemplateAttributes(OutputContext oc, StringTemplate st) {
        super.setTemplateAttributes(oc, st);
        st.setAttribute("name", name);
        st.setAttribute("type", type);
    }


    @Override
    protected String getTemplateName() {
        return "memberdefinition";
    }
}
