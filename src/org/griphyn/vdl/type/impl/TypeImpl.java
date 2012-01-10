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


package org.griphyn.vdl.type.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.griphyn.vdl.type.DuplicateFieldException;
import org.griphyn.vdl.type.Field;
import org.griphyn.vdl.type.Type;
import org.griphyn.vdl.type.Types;

public class TypeImpl extends UnresolvedType {
	private boolean primitive;
	private Map<String, Field> fields;
	private Type baseType;
	
	public TypeImpl() {
		this((URI) null, null, false);
	}

	public TypeImpl(String namespace, String name, boolean primitive) {
		super(namespace, name);
		init(primitive);
	}

	public TypeImpl(URI namespace, String name, boolean primitive) {
		super(namespace, name);
		init(primitive);
	}

	private void init(boolean primitive) {
		this.primitive = primitive;
		fields = new HashMap<String, Field>();
		baseType = null;
	}

	public TypeImpl(String name, boolean primitive) {
		this((URI) null, name, primitive);
	}

	public TypeImpl(String name) {
		this(name, false);
	}

	public void addField(Field field) throws DuplicateFieldException {
		String name = (String) field.getId();
		if (name == null) {
			return;
		}
		if (fields.get(name) != null) {
			throw new DuplicateFieldException(name);
		}
		else {
			fields.put(name, field);
		}
	}

	public void addField(String name, Type type) throws DuplicateFieldException {
		Field field = Field.Factory.createField(name, type);
		addField(field);
	}

	public Field getField(String name) throws NoSuchFieldException {
		if (name == null) {
			throw new NullPointerException();
		}

		Field field = fields.get(name);
		if (field != null) {
			return field;
		}
		else {
			throw new NoSuchFieldException(name);
		}
	}

	public List<String> getFieldNames() {
		List<String> list = new ArrayList<String>();
		list.addAll(fields.keySet());
		return list;
	}

	public List<Field> getFields() {
		return new ArrayList<Field>(fields.values());
	}
	
    public Type getBaseType() {
		return baseType;
	}

	public Type arrayType() {
		return new Array(this, Types.INT);
	}

	public Type itemType() {
		return this;
	}

	public boolean isPrimitive() {
		return primitive;
	}

	public void setBaseType(Type base) {
		baseType = base;
	}

	public void setPrimitive() {
		primitive = true;
	}
	

    public boolean isComposite() {
        return isArray() || !fields.isEmpty();
    }

    public static class Array extends TypeImpl {
		private Field field;
		private Type keyType;

		/** Constructs an array that will contain elements of the
		    specified type. */
		public Array(Type valueType, Type keyType) {
			super(valueType.getNamespaceURI(), valueType.getName()+"[" + keyType.getName() + "]", false);
			field = Field.Factory.newInstance();
			field.setType(valueType);
			this.keyType = keyType;
		}

/*
		public Type arrayType() {
			return this;
		}
*/

		public boolean isArray() {
			return true;
		}

		public Field getField(String name) throws NoSuchFieldException {
			return field;
		}

		public Type itemType() {
			return field.getType();
		}
		
		public Type keyType() {
		    return keyType;
		}
	}
}
