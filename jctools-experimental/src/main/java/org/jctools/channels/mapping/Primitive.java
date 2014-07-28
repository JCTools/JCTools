/*
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
package org.jctools.channels.mapping;

import org.objectweb.asm.Opcodes;

enum Primitive implements Opcodes {

	BYTE(1, Byte.TYPE, ILOAD, IRETURN),
	SHORT(2, Short.TYPE, ILOAD, IRETURN),
    INT(4, Integer.TYPE, ILOAD, IRETURN),
    LONG(8, Long.TYPE, LLOAD, LRETURN),
	FLOAT(4, Float.TYPE, FLOAD, FRETURN),
    DOUBLE(8, Double.TYPE, DLOAD, DRETURN),
    BOOLEAN(1, Byte.TYPE, ILOAD, IRETURN),
    CHAR(2, Character.TYPE, ILOAD, IRETURN);

    final int sizeInBytes;
    final Class<?> javaEquivalent;
	int loadOpcode;
	int returnOpcode;

    private Primitive(int size, Class<?> javaType, int loadOpcode, int returnOpcode) {
        this.sizeInBytes = size;
		this.javaEquivalent = javaType;
		this.loadOpcode = loadOpcode;
		this.returnOpcode = returnOpcode;
    }

    String unsafeMethodSuffix() {
    	if (this == BOOLEAN) {
    		return "Byte";
    	}

    	String name = name();
    	return name.charAt(0) + name.substring(1).toLowerCase();
    }

	static Primitive of(Class<?> type) {
		String name = type.getName().toUpperCase();
		return Primitive.valueOf(name);
	}

}
