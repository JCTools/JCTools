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

import org.jctools.util.UnsafeAccess;
import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.objectweb.asm.Type.LONG_TYPE;

@SuppressWarnings("restriction")
public class BytecodeGenerator<E> implements Opcodes {

	private static final String GENERATED_CONSTRUCTOR = "(J)V";
	private static final String UNSAFE_NAME = Type.getInternalName(Unsafe.class);
	private static final String UNSAFE_DESCRIPTOR = Type.getType(Unsafe.class).getDescriptor();
	private static final String DIRECT_CLASS_NAME = Type.getInternalName(Flyweight.class);
    private static final String DIRECT_CLASS_CONSTRUCTOR;

    private static final String UNSAFE_ACCESS_CLASS_NAME = Type.getInternalName(UnsafeAccess.class);
    private static final String UNSAFE_ACCESS_FIELD_NAME = "UNSAFE";

    static {
		Constructor<?> constructor = Flyweight.class.getConstructors()[0];
		DIRECT_CLASS_CONSTRUCTOR = Type.getConstructorDescriptor(constructor);
	}

    private final TypeInspector inspector;
    private final String classExtended;
    private final String constructorExtended;
	private final String implementationName;
	private final String[] interfacesImplemented;
    private final boolean classFileDebugEnabled;

    public BytecodeGenerator(TypeInspector inspector, Class<E> representingKlass, boolean classFileDebugEnabled) {
        this.inspector = inspector;
        this.classFileDebugEnabled = classFileDebugEnabled;
        implementationName = "DirectMemory" + representingKlass.getSimpleName();
        classExtended = DIRECT_CLASS_NAME;
        constructorExtended =  DIRECT_CLASS_CONSTRUCTOR;
        interfacesImplemented = new String[] { Type.getInternalName(representingKlass) };
    }

    @SuppressWarnings("unchecked")
	public Class<E> generate() {
    	ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    	CheckClassAdapter writer = new CheckClassAdapter(out);
    	
		int offset = Flyweight.TAG_SIZE_IN_BYTES;
    	declareClass(writer);
    	declareConstructor(writer);
    	for (Method getter : inspector.getters) {
    		offset = declareField(getter, writer, offset);
    	}
    	
    	writer.visitEnd();

        return (Class<E>) new GeneratedClassLoader(classFileDebugEnabled).defineClass(implementationName, out);    }

    private void declareClass(ClassVisitor writer) {
    	writer.visit(V1_6, ACC_PUBLIC + ACC_SUPER, implementationName, null, classExtended, interfacesImplemented);
    }

    private void declareConstructor(CheckClassAdapter writer) {
    	MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "<init>", GENERATED_CONSTRUCTOR, null, null);
    	method.visitCode();
		method.visitVarInsn(ALOAD, 0);
		method.visitVarInsn(LLOAD, 1);
		method.visitMethodInsn(INVOKESPECIAL,
				classExtended,
				"<init>",
				constructorExtended);
		method.visitInsn(RETURN);
		method.visitMaxs(5, 5);
		method.visitEnd();
    }

	private int declareField(Method getter, ClassVisitor writer, int fieldOffset) {
		Primitive type = inspector.getReturn(getter);

		MethodVisitor implementingGetter = declareMethod(getter, writer);
		declareGetterBody(fieldOffset, type, implementingGetter);

		Method setter = inspector.setterFor(getter);
		MethodVisitor implementingSetter = declareMethod(setter, writer);
		declareSetterBody(fieldOffset, type, implementingSetter);

		return fieldOffset + type.sizeInBytes;
	}

	private MethodVisitor declareMethod(Method method, ClassVisitor writer) {
		String name = method.getName();
		String descriptor = Type.getMethodDescriptor(method);
		return writer.visitMethod(ACC_PUBLIC, name, descriptor, null, null);
	}

	private void declareGetterBody(int fieldOffset, Primitive type, MethodVisitor method) {
		method.visitCode();
		declareUnsafe(fieldOffset, method);
		
		// unsafe.getLong
		String unsafeGetter = "get" + type.unsafeMethodSuffix();
		String unsafeDescriptor = getUnsafeMethodDescriptor(unsafeGetter, Long.TYPE);
		method.visitMethodInsn(INVOKEVIRTUAL, UNSAFE_NAME, unsafeGetter, unsafeDescriptor, false);

		method.visitInsn(type.returnOpcode);
		method.visitMaxs(4, 4);
		method.visitEnd();
	}

	private void declareSetterBody(int fieldOffset, Primitive type, MethodVisitor method) {
		method.visitCode();
		Label start = new Label();
		method.visitLabel(start);
		declareUnsafe(fieldOffset, method);

		// load parameter 1
		method.visitVarInsn(type.loadOpcode, 1);

		// unsafe.putLong
		String unsafeSetter = "put" + type.unsafeMethodSuffix();
		String unsafeDescriptor = getUnsafeMethodDescriptor(unsafeSetter, Long.TYPE, type.javaEquivalent);
		method.visitMethodInsn(INVOKEVIRTUAL, UNSAFE_NAME, unsafeSetter, unsafeDescriptor);

		Label end = new Label();
		method.visitLabel(end);

		method.visitInsn(RETURN);
		
		method.visitLocalVariable("value", Type.getDescriptor(type.javaEquivalent), null, start, end, 0);
		method.visitMaxs(4, 4);
		method.visitEnd();
	}

	private void declareUnsafe(int fieldOffset, MethodVisitor method) {
		// DirectMemoryCursor.unsafe
        method.visitFieldInsn(GETSTATIC, UNSAFE_ACCESS_CLASS_NAME, UNSAFE_ACCESS_FIELD_NAME, UNSAFE_DESCRIPTOR);

		// this.pointer  + fieldOffset
		method.visitVarInsn(ALOAD, 0);
		method.visitFieldInsn(GETFIELD, implementationName, "pointer", LONG_TYPE.getDescriptor());
		method.visitLdcInsn((long)fieldOffset);
		method.visitInsn(LADD);
	}

	private String getUnsafeMethodDescriptor(String methodName, Class<?> ... types) {
		try {
			Method method = Unsafe.class.getMethod(methodName, types);
			return Type.getMethodDescriptor(method);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
