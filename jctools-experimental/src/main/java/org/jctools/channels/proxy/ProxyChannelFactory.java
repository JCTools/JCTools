package org.jctools.channels.proxy;

import org.jctools.channels.spsc.SpscOffHeapFixedSizeRingBuffer;
import org.jctools.util.UnsafeAccess;
import org.objectweb.asm.*;
import sun.misc.Unsafe;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ProxyChannelFactory {

    static <E> ProxyChannel<E> createSpscProxy(int capacity, Class<E> iFace) {

        if (!iFace.isInterface()) {
            throw new IllegalArgumentException("Not an interface: " + iFace);
        }

        String generatedName = Type.getInternalName(iFace) + "$JCTools$ProxyChannel";

        Class<?> preExisting = findExisting(generatedName, iFace);
        if (preExisting != null) {
            return instantiate(preExisting, capacity);
        }

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        classWriter.visit(Opcodes.V1_4,
                Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                generatedName,
                null,
                Type.getInternalName(SpscOffHeapFixedSizeRingBuffer.class),
                new String[]{Type.getInternalName(ProxyChannel.class), Type.getInternalName(iFace)});

        implementConstructor(classWriter);

        implementProxyInstance(classWriter, iFace, generatedName);
        implementProxy(classWriter, iFace, generatedName);

        Method[] methods = iFace.getMethods();

        int type = 1;
        List<Method> relevantMethods = new ArrayList<Method>(methods.length);
        for (Method method : methods) {
            if (Modifier.isAbstract(method.getModifiers())) {
                relevantMethods.add(method);
                implementUserMethod(method, classWriter, type++);
            }
        }

        if (relevantMethods.isEmpty()) {
            throw new IllegalArgumentException("Does not declare any abstract methods: " + iFace);
        }

        implementProcess(classWriter, relevantMethods, iFace, generatedName);

        classWriter.visitEnd();

        synchronized (ProxyChannelFactory.class) {
            preExisting = findExisting(generatedName, iFace);
            if (preExisting != null) {
                return instantiate(preExisting, capacity);
            }
            byte[] byteCode = classWriter.toByteArray();
            // Caveat: The interface and JCTools must be on the same class loader. Maybe class loader should be an argument? Overload?
            return instantiate(UnsafeAccess.UNSAFE.defineClass(generatedName, byteCode, 0, byteCode.length, iFace.getClassLoader(), null), capacity);
        }
    }

    private static Class<?> findExisting(String generatedName, Class<?> iFace) {
        try {
            return Class.forName(generatedName, true, iFace.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <E> ProxyChannel<E> instantiate(Class<?> proxy, int capacity) {
        try {
            return (ProxyChannel<E>) proxy.getDeclaredConstructor(int.class).newInstance(capacity);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void implementProcess(ClassVisitor classVisitor, List<Method> methods, Class<?> iFace, String generatedName) {


        // public int process (E impl, int limit)
        MethodVisitor methodVisitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC,
                "process",
                "(" + Type.getDescriptor(iFace) + "I)I",
                null,
                null);
        methodVisitor.visitCode();

        //int i = 0; // offset 3
        methodVisitor.visitInsn(Opcodes.ICONST_0);
        methodVisitor.visitVarInsn(Opcodes.ISTORE, 3);


        // label <loopStart>;
        Label loopStart = new Label(), loopEnd = new Label();
        methodVisitor.visitLabel(loopStart);


        // if (i < limit) goto <loopEnd>;
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 3);
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 2);
        methodVisitor.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);


        // prepare switch labels
        Label endOfSwitch = new Label();
        Label[] cases = new Label[methods.size()];
        for (int index = 0; index < cases.length; index++) {
            cases[index] = new Label();
        }

        // long rOffset = this.readAcquire(); // offset 4
        readAcquire(methodVisitor);
        methodVisitor.visitVarInsn(Opcodes.LSTORE, 4);


        // if (rOffset == EOF) goto <loopEnd>;
        methodVisitor.visitVarInsn(Opcodes.LLOAD, 4);
        methodVisitor.visitLdcInsn(SpscOffHeapFixedSizeRingBuffer.EOF);
        methodVisitor.visitInsn(Opcodes.LCMP);
        methodVisitor.visitJumpInsn(Opcodes.IFEQ, loopEnd);

        // switch(UnsafeAccess.UNSAFE.getInt(rOffset)) // start with case 1, increment by 1; represents "type"
        getUnsafe(methodVisitor, int.class, 4, 0);
        methodVisitor.visitTableSwitchInsn(1, cases.length, endOfSwitch, cases);

        // add case statement for each method
        for (int index = 0; index < cases.length; index++) {

            // case <index>:
            methodVisitor.visitLabel(cases[index]);
            Method method = methods.get(index);

            // #PUSH: impl
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);

            // # W_OFFSET_DELTA = 4
            // #FOREACH param in method
            int wOffsetDelta = 4;
            for (Class<?> parameterType : method.getParameterTypes()) {
                // #PUSH: UnsafeAccess.UNSAFE.get[param.type](rOffset + #W_OFFSET_DELTA);
                // #W_OFFSET_DELTA += if param.type in {long, double} 8 else 4;
                getUnsafe(methodVisitor, parameterType, 4, wOffsetDelta);
                wOffsetDelta += memorySize(parameterType);
            }
            // #END

            // this.readRelease(rOffset);
            readRelease(methodVisitor, 4);

            // method.invoke(impl, <args>);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                    Type.getInternalName(iFace),
                    method.getName(),
                    Type.getMethodDescriptor(method),
                    true);

            // break;
            methodVisitor.visitJumpInsn(Opcodes.GOTO, endOfSwitch);
        }

        // label <endOfSwitch>;
        methodVisitor.visitLabel(endOfSwitch);

        // i++;
        methodVisitor.visitIincInsn(3, 1);

        // goto <loopStart>;
        methodVisitor.visitJumpInsn(Opcodes.GOTO, loopStart);
        methodVisitor.visitLabel(loopEnd);

        // return i;
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 3);
        methodVisitor.visitInsn(Opcodes.IRETURN);

        // size requirement is computed by ASM; complete method.
        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();

        // Add generic bridge method for erasure public int process (Object impl, int limit)
        methodVisitor = classVisitor.visitMethod(Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PUBLIC,
                "process",
                "(" + Type.getDescriptor(Object.class) + "I)I",
                null,
                null);
        methodVisitor.visitCode();

        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(iFace));
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 2);

        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                generatedName,
                "process",
                "(" + Type.getDescriptor(iFace) + "I)I",
                false);

        methodVisitor.visitInsn(Opcodes.IRETURN);

        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
    }

    private static void implementConstructor(ClassVisitor classVisitor) {
        MethodVisitor methodVisitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null);
        methodVisitor.visitCode();

        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 1);
        methodVisitor.visitIntInsn(Opcodes.BIPUSH, 13);

        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                Type.getInternalName(SpscOffHeapFixedSizeRingBuffer.class),
                "<init>",
                "(II)V",
                false);

        methodVisitor.visitInsn(Opcodes.RETURN);

        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
    }

    private static void implementProxyInstance(ClassVisitor classVisitor, Class<?> iFace, String generatedName) {
        MethodVisitor methodVisitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC,
                "proxyInstance",
                "(" + Type.getDescriptor(iFace) + ")" + Type.getDescriptor(iFace),
                null,
                null);
        methodVisitor.visitCode();

        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitInsn(Opcodes.ARETURN);

        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();

        methodVisitor = classVisitor.visitMethod(Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PUBLIC,
                "proxyInstance",
                "(" + Type.getDescriptor(Object.class) + ")" + Type.getDescriptor(Object.class),
                null,
                null);

        methodVisitor.visitCode();

        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(iFace));
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedName, "proxyInstance", "(" + Type.getDescriptor(iFace) + ")" + Type.getDescriptor(iFace), false);
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(iFace));
        methodVisitor.visitInsn(Opcodes.ARETURN);

        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
    }

    private static void implementProxy(ClassVisitor classVisitor, Class<?> iFace, String generatedName) {
        MethodVisitor methodVisitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC,
                "proxy",
                "()" + Type.getDescriptor(iFace),
                null,
                null);

        methodVisitor.visitCode();

        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitInsn(Opcodes.ARETURN);

        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();

        methodVisitor = classVisitor.visitMethod(Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PUBLIC,
                "proxy",
                "()" + Type.getDescriptor(Object.class),
                null,
                null);

        methodVisitor.visitCode();

        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedName, "proxy", "()" + Type.getDescriptor(iFace), false);
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(iFace));
        methodVisitor.visitInsn(Opcodes.ARETURN);

        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
    }

    private static void implementUserMethod(Method method, ClassVisitor classVisitor, int type) {

        if (method.getReturnType() != void.class) {
            throw new IllegalArgumentException("Method does not return void: " + method);
        }

        String[] exceptions = new String[method.getExceptionTypes().length];
        int index = 0;
        for (Class<?> exceptionType : method.getExceptionTypes()) {
            exceptions[index++] = Type.getInternalName(exceptionType);
        }

        // @Override public void <user interface method>
        MethodVisitor methodVisitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC,
                method.getName(),
                Type.getMethodDescriptor(method),
                null,
                exceptions.length == 0 ? null : exceptions);

        methodVisitor.visitCode();

        // Compute space that is occupied by this and method arguments to find offset for wOffset variable
        int wOffset = 1;
        for (Class<?> parameterType : method.getParameterTypes()) {
            wOffset += Type.getType(parameterType).getSize();
        }

        // long wOffset = this.writeAcquire();
        writeAcquire(methodVisitor);
        methodVisitor.visitVarInsn(Opcodes.LSTORE, wOffset);

        // #W_OFFSET_DELTA = 4
        // #ARGUMENT = 1 // not zero based (zero references "this")
        // #FOREACH param in method
        int wOffsetDelta = 4, varOffset = 1;
        for (Class<?> parameterType : method.getParameterTypes()) {
            // UnsafeAccess.UNSAFE.put[param.type](wOffset + #W_OFFSET_DELTA, #ARGUMENT);
            // #W_OFFSET_DELTA += if param.type in {long, double} 8 else 4;
            varOffset += putUnsafe(methodVisitor, parameterType, wOffset, wOffsetDelta, varOffset);
            wOffsetDelta += memorySize(parameterType);
        }
        // #END

        // this.writeRelease(wOffset, #TYPE);
        writeRelease(methodVisitor, wOffset, type);

        // return;
        methodVisitor.visitInsn(Opcodes.RETURN);

        // complete method, ASM computes size requirement.
        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
    }

    private static void writeAcquire(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SpscOffHeapFixedSizeRingBuffer.class), "writeAcquire", "()J", false);
    }

    private static void writeRelease(MethodVisitor methodVisitor, int wOffset, int type) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitVarInsn(Opcodes.LLOAD, wOffset);
        methodVisitor.visitLdcInsn(type);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SpscOffHeapFixedSizeRingBuffer.class), "writeRelease", "(JI)V", false);
    }

    private static void readAcquire(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SpscOffHeapFixedSizeRingBuffer.class), "readAcquire", "()J", false);
    }

    private static void readRelease(MethodVisitor methodVisitor, int wOffset) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitVarInsn(Opcodes.LLOAD, wOffset);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SpscOffHeapFixedSizeRingBuffer.class), "readRelease", "(J)V", false);
    }

    private static int getUnsafe(MethodVisitor methodVisitor, Class<?> parameterType, int wOffset, int wOffsetDelta) {
        loadUnsafe(methodVisitor);
        loadWOffset(methodVisitor, parameterType, wOffset, wOffsetDelta);
        return parameterTypeUnsafe(methodVisitor, parameterType, false);
    }

    private static int putUnsafe(MethodVisitor methodVisitor, Class<?> parameterType, int wOffset, int wOffsetDelta, int varOffset) {
        loadUnsafe(methodVisitor);
        loadWOffset(methodVisitor, parameterType, wOffset, wOffsetDelta);
        methodVisitor.visitVarInsn(Type.getType(parameterType).getOpcode(Opcodes.ILOAD), varOffset);
        return parameterTypeUnsafe(methodVisitor, parameterType, true);
    }

    private static void loadUnsafe(MethodVisitor methodVisitor) {
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(UnsafeAccess.class), "UNSAFE", "L" + Type.getInternalName(Unsafe.class) + ";");
    }

    private static void loadWOffset(MethodVisitor methodVisitor, Class<?> parameterType, int baseOffset, long wOffsetDelta) {
        if (parameterType == boolean.class) {
            methodVisitor.visitInsn(Opcodes.ACONST_NULL);
        }
        methodVisitor.visitVarInsn(Opcodes.LLOAD, baseOffset);
        if (wOffsetDelta != 0) {
            methodVisitor.visitLdcInsn(wOffsetDelta);
            methodVisitor.visitInsn(Opcodes.LADD);
        }
    }

    private static int parameterTypeUnsafe(MethodVisitor methodVisitor, Class<?> parameterType, boolean write) {
        parameterType = parameterType.isPrimitive() ? parameterType : Object.class;
        Type type = Type.getType(parameterType);
        String boolDescriptor = parameterType == boolean.class ? "Ljava/lang/Object;" : "";
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                Type.getInternalName(Unsafe.class),
                (write ? "put" : "get") + Character.toUpperCase(type.getClassName().charAt(0)) + type.getClassName().substring(1),
                write ? ("(" + boolDescriptor + "J" + type.getDescriptor() + ")V") : ("(" + boolDescriptor + "J)" + type.getDescriptor()),
                false);
        return type.getSize();
    }

    private static int memorySize(Class<?> type) {
        if (!type.isPrimitive()) {
            throw new IllegalArgumentException("Cannot handle non-primtive parameter type: " + type); // TODO: Add handling for reference parameters.
        }
        return type == long.class || type == double.class ? 8 : 4;
    }
}
