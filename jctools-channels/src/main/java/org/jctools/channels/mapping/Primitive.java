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

import org.jctools.channels.ChannelReceiver;
import org.objectweb.asm.Opcodes;

import java.nio.ByteBuffer;

enum Primitive implements Opcodes {

	BYTE(1, Byte.TYPE, Byte.class, ILOAD, IRETURN),
	SHORT(2, Short.TYPE, Short.class, ILOAD, IRETURN),
    INT(4, Integer.TYPE, Integer.class, ILOAD, IRETURN),
    LONG(8, Long.TYPE, Long.class, LLOAD, LRETURN),
	FLOAT(4, Float.TYPE, Float.class, FLOAD, FRETURN),
    DOUBLE(8, Double.TYPE, Double.class, DLOAD, DRETURN),
    BOOLEAN(1, Byte.TYPE, Byte.class, ILOAD, IRETURN),
    CHAR(2, Character.TYPE, Character.class, ILOAD, IRETURN);

    final int sizeInBytes;
    final Class<?> javaEquivalent;
    final Class<?> boxedJavaType;
    final int loadOpcode;
    final int returnOpcode;

    Primitive(int size, Class<?> javaType, Class<?> boxedJavaType, int loadOpcode, int returnOpcode) {
        this.sizeInBytes = size;
		this.javaEquivalent = javaType;
        this.boxedJavaType = boxedJavaType;
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

    static Class<?> replaceWithPrimitive(Class<?> boxedJavaType) {
        for (Primitive primitive: Primitive.values())
            if (primitive.boxedJavaType == boxedJavaType)
                return primitive.javaEquivalent;

        return boxedJavaType;
    }

    public static Class<?> simplifyType(Class<?> type) {
        type = replaceWithPrimitive(type);
        type = usePublicApiClass(type);
        return type;
    }

    private static Class<?> usePublicApiClass(Class<?> type) {
        if ("DirectByteBuffer".equals(type.getSimpleName()))
            return ByteBuffer.class;

        if (ChannelReceiver.class.isAssignableFrom(type))
            return ChannelReceiver.class;

        return type;
    }

}
