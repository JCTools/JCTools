package org.jctools.channels.proxy;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.jctools.channels.spsc.SpscOffHeapFixedSizeWithReferenceSupportRingBuffer;
import org.jctools.channels.spsc.SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.WaitStrategy;
import org.jctools.util.UnsafeAccess;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.TraceClassVisitor;

import sun.misc.Unsafe;

public class ProxyChannelFactory {

    private static final boolean DEBUG = Boolean.getBoolean("jctools.debug");
    
    private static void printClassBytes(byte[] byteCode) {
        if (DEBUG) {
            TraceClassVisitor visitor = new TraceClassVisitor(new PrintWriter(System.out));
            new ClassReader(byteCode).accept(visitor, 0);
        }
    }
    
    public static <E> ProxyChannel<E> createSpscProxy(int capacity, Class<E> iFace, WaitStrategy waitStrategy) {

        if (!iFace.isInterface()) {
            throw new IllegalArgumentException("Not an interface: " + iFace);
        }

        String generatedName = Type.getInternalName(iFace) + "$JCTools$ProxyChannel";

        List<Method> relevantMethods = findRelevantMethods(iFace);
        if (relevantMethods.isEmpty()) {
            throw new IllegalArgumentException("Does not declare any abstract methods: " + iFace);
        }
        
        int arrayMessageSize = calculateMaxReferenceParameters(relevantMethods);
        Class<?> preExisting = findExisting(generatedName, iFace);
        if (preExisting != null) {
            return instantiate(preExisting, capacity, arrayMessageSize, waitStrategy);
        }

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        classWriter.visit(Opcodes.V1_4,
                Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                generatedName,
                null,
                Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class),
                new String[]{Type.getInternalName(ProxyChannel.class), Type.getInternalName(iFace)});

        implementConstructor(classWriter);
        implementProxyInstance(classWriter, iFace, generatedName);
        implementProxy(classWriter, iFace, generatedName);

        implementUserMethods(classWriter, relevantMethods);
        implementProcess(classWriter, relevantMethods, iFace, generatedName);

        classWriter.visitEnd();

        synchronized (ProxyChannelFactory.class) {
            preExisting = findExisting(generatedName, iFace);
            if (preExisting != null) {
                return instantiate(preExisting, capacity, arrayMessageSize, waitStrategy);
            }
            byte[] byteCode = classWriter.toByteArray();
            printClassBytes(byteCode);
            // Caveat: The interface and JCTools must be on the same class loader. Maybe class loader should be an argument? Overload?
            Class<?> definedClass = UnsafeAccess.UNSAFE.defineClass(generatedName, byteCode, 0, byteCode.length, iFace.getClassLoader(), null);
            return instantiate(definedClass, capacity, arrayMessageSize, waitStrategy);
        }
    }

    private static void implementUserMethods(ClassWriter classWriter, List<Method> relevantMethods) {
        int type = 1;
        for (Method method : relevantMethods) {
            implementUserMethod(method, classWriter, type++);
        }
    }

    private static int calculateMaxReferenceParameters(List<Method> relevantMethods) {
        int maxReferenceParams = 0; // max number of reference arguments to any method
        for (Method method : relevantMethods) {
            int referenceParameterCount = 0;
            for (Class<?> parameterType : method.getParameterTypes()) {
                if (!parameterType.isPrimitive()) {
                    referenceParameterCount++;
                }
            }
            maxReferenceParams = Math.max(maxReferenceParams, referenceParameterCount);
        }
        return maxReferenceParams;
    }
    
    private static List<Method> findRelevantMethods(Class<?> iFace) {
        Method[] methods = iFace.getMethods();
        List<Method> relevantMethods = new ArrayList<Method>(methods.length);
        for (Method method : methods) {
            if (Modifier.isAbstract(method.getModifiers())) {
                relevantMethods.add(method);
            }
        }
        return relevantMethods;
    }

    private static Class<?> findExisting(String generatedName, Class<?> iFace) {
        try {
            String className = generatedName.replace("/", ".");
            return Class.forName(className, true, iFace.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <E> ProxyChannel<E> instantiate(Class<?> proxy, int capacity, int arrayMessageSize, WaitStrategy waitStrategy) {
        try {
            return (ProxyChannel<E>) proxy.getDeclaredConstructor(int.class, int.class, WaitStrategy.class).newInstance(capacity, arrayMessageSize, waitStrategy);
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

        int localIndexOfLimit = 2; // int
        int localIndexOfLoopIndex = 3; // int
        int localIndexOfROffset = 4; // long
        Type rOffsetType = Type.getType(long.class);
        
        //int i = 0; // offset 3
        methodVisitor.visitInsn(Opcodes.ICONST_0);
        methodVisitor.visitVarInsn(Opcodes.ISTORE, localIndexOfLoopIndex);


        // label <loopStart>;
        Label loopStart = new Label(), loopEnd = new Label();
        methodVisitor.visitLabel(loopStart);


        // if (i < limit) goto <loopEnd>;
        methodVisitor.visitVarInsn(Opcodes.ILOAD, localIndexOfLoopIndex);
        methodVisitor.visitVarInsn(Opcodes.ILOAD, localIndexOfLimit);
        methodVisitor.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);


        // prepare switch labels
        Label endOfSwitch = new Label();
        Label[] cases = new Label[methods.size()];
        for (int index = 0; index < cases.length; index++) {
            cases[index] = new Label();
        }

        // long rOffset = this.readAcquire(); // offset 4
        readAcquire(methodVisitor);
        methodVisitor.visitVarInsn(Opcodes.LSTORE, localIndexOfROffset);


        // if (rOffset == EOF) goto <loopEnd>;
        methodVisitor.visitVarInsn(Opcodes.LLOAD, localIndexOfROffset);
        methodVisitor.visitLdcInsn(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.EOF);
        methodVisitor.visitInsn(Opcodes.LCMP);
        methodVisitor.visitJumpInsn(Opcodes.IFEQ, loopEnd);

        // switch(UnsafeAccess.UNSAFE.getInt(rOffset)) // start with case 1, increment by 1; represents "type"
        getUnsafe(methodVisitor, int.class, localIndexOfROffset, 0);
        methodVisitor.visitTableSwitchInsn(1, cases.length, endOfSwitch, cases);

        // add case statement for each method
        for (int index = 0; index < cases.length; index++) {

            // case <index>:
            methodVisitor.visitLabel(cases[index]);
            Method method = methods.get(index);

            // #PUSH: impl
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
            
            boolean containsReferences = false;
            for (Class<?> parameterType : method.getParameterTypes()) {
                containsReferences |= !parameterType.isPrimitive();
            }
            
            // Only used if references are present so set to a dummy variable
            int localIndexOfArrayReferenceBaseIndex = Integer.MIN_VALUE;
            if (containsReferences) {
                localIndexOfArrayReferenceBaseIndex = localIndexOfROffset + rOffsetType.getSize();
                consumerReferenceArrayIndex(methodVisitor);
                methodVisitor.visitVarInsn(Opcodes.LSTORE, localIndexOfArrayReferenceBaseIndex);
            }

            // # W_OFFSET_DELTA = 4
            // #FOREACH param in method
            int wOffsetDelta = 4;
            int arrayReferenceBaseIndexDelta = 0;
            for (Class<?> parameterType : method.getParameterTypes()) {
                if (parameterType.isPrimitive()) {
                    // #PUSH: UnsafeAccess.UNSAFE.get[param.type](rOffset + #W_OFFSET_DELTA);
                    // #W_OFFSET_DELTA += if param.type in {long, double} 8 else 4;
                    getUnsafe(methodVisitor, parameterType, localIndexOfROffset, wOffsetDelta);
                    wOffsetDelta += memorySize(parameterType);
                } else {
                    getReference(methodVisitor, parameterType, localIndexOfArrayReferenceBaseIndex, arrayReferenceBaseIndexDelta);
                    arrayReferenceBaseIndexDelta++;
                }
            }
            // #END

            // this.readRelease(rOffset);
            readRelease(methodVisitor, localIndexOfROffset);

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
        String constructorMethodDescriptor = Type.getMethodDescriptor(Type.getType(void.class), 
                Type.getType(int.class), 
                Type.getType(int.class), 
                Type.getType(WaitStrategy.class));
        MethodVisitor methodVisitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC,
                "<init>",
                constructorMethodDescriptor,
                null,
                null);
        methodVisitor.visitCode();

        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 1); // capacity by caller
        methodVisitor.visitIntInsn(Opcodes.BIPUSH, 13); // messageSize hardcoded
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 2); // arrayMessageSize by caller
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 3); // waitStrategy by caller


        String superConstructorMethodDescriptor = Type.getMethodDescriptor(Type.getType(void.class), 
                Type.getType(int.class), 
                Type.getType(int.class), 
                Type.getType(int.class), 
                Type.getType(WaitStrategy.class));
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class),
                "<init>",
                superConstructorMethodDescriptor,
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

        Type wOffsetType = Type.getType(long.class);
        // Compute space that is occupied by this and method arguments to find offset for wOffset variable
        int wOffset = 1;
        boolean containsReferences = false;
        for (Class<?> parameterType : method.getParameterTypes()) {
            wOffset += Type.getType(parameterType).getSize();
            containsReferences |= !parameterType.isPrimitive();
        }

        // long wOffset = this.writeAcquireWithWaitStrategy();
        writeAcquireWithWaitStrategy(methodVisitor);
//        Label loopStart = new Label(), loopEnd = new Label();
//        methodVisitor.visitLabel(loopStart);
        
        methodVisitor.visitVarInsn(Opcodes.LSTORE, wOffset);
        
        int localIndexOfArrayReferenceBaseIndex = wOffset + wOffsetType.getSize();
        if (containsReferences) {
            producerReferenceArrayIndex(methodVisitor);
            methodVisitor.visitVarInsn(Opcodes.LSTORE, localIndexOfArrayReferenceBaseIndex);
        }

        // #W_OFFSET_DELTA = 4
        // #ARGUMENT = 1 // not zero based (zero references "this")
        // #FOREACH param in method
        int wOffsetDelta = 4, varOffset = 1;
        int arrayReferenceBaseIndexDelta = 0;
        for (Class<?> parameterType : method.getParameterTypes()) {
            // UnsafeAccess.UNSAFE.put[param.type](wOffset + #W_OFFSET_DELTA, #ARGUMENT);
            // #W_OFFSET_DELTA += if param.type in {long, double} 8 else 4;
            
            /*
             * wOffset here is being used to identify an index in the local call
             * frame, do not get it confused with the wOffset local variable
             * used in examples which is a position in a buffer that we can
             * write to. wOffsetDelta is the position from the VALUE of the
             * wOffset local variable to write to. varOffset is the index in the
             * local variables to read from, i.e. the parameter index in locals
             */

            if (parameterType.isPrimitive()) {
                varOffset += putUnsafe(methodVisitor, parameterType, wOffset, wOffsetDelta, varOffset);
                wOffsetDelta += memorySize(parameterType);
            } else {
                putReference(methodVisitor, parameterType, localIndexOfArrayReferenceBaseIndex, arrayReferenceBaseIndexDelta, varOffset);
                varOffset += Type.getType(parameterType).getSize();
                arrayReferenceBaseIndexDelta++;
            }
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

    private static void producerReferenceArrayIndex(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class), "producerReferenceArrayIndex", "()J", false);
    }

    private static void consumerReferenceArrayIndex(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class), "consumerReferenceArrayIndex", "()J", false);
    }

    private static void writeAcquireWithWaitStrategy(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class),
                "writeAcquireWithWaitStrategy",
                "()J",
                false);
    }

    private static void writeRelease(MethodVisitor methodVisitor, int wOffset, int type) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitVarInsn(Opcodes.LLOAD, wOffset);
        methodVisitor.visitLdcInsn(type);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class), "writeRelease", "(JI)V", false);
    }

    private static void readAcquire(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class), "readAcquire", "()J", false);
    }

    private static void readRelease(MethodVisitor methodVisitor, int wOffset) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitVarInsn(Opcodes.LLOAD, wOffset);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class), "readRelease", "(J)V", false);
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

    private static void getReference(MethodVisitor methodVisitor, Class<?> parameterType, int localIndexOfArrayReferenceBaseIndex, int arrayReferenceBaseIndexDelta) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0); // Load this
        loadLocalIndexAndApplyDelta(methodVisitor, localIndexOfArrayReferenceBaseIndex, arrayReferenceBaseIndexDelta);
        readReference(methodVisitor);
        if (parameterType != Object.class) {
            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(parameterType));
        }
    }

    private static void putReference(MethodVisitor methodVisitor, Class<?> parameterType, int localIndexOfArrayReferenceBaseIndex, int arrayReferenceBaseIndexDelta, int varOffset) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0); // Load this
        loadLocalIndexAndApplyDelta(methodVisitor, localIndexOfArrayReferenceBaseIndex, arrayReferenceBaseIndexDelta);
        methodVisitor.visitVarInsn(Type.getType(parameterType).getOpcode(Opcodes.ILOAD), varOffset);
        
        writeReference(methodVisitor);
    }

    private static void loadUnsafe(MethodVisitor methodVisitor) {
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(UnsafeAccess.class), "UNSAFE", "L" + Type.getInternalName(Unsafe.class) + ";");
    }

    private static void loadWOffset(MethodVisitor methodVisitor, Class<?> parameterType, int baseOffset, long wOffsetDelta) {
        if (parameterType == boolean.class) {
            methodVisitor.visitInsn(Opcodes.ACONST_NULL);
        }
        loadLocalIndexAndApplyDelta(methodVisitor, baseOffset, wOffsetDelta);
    }

    private static void loadLocalIndexAndApplyDelta(MethodVisitor methodVisitor, int localVariableIndex, long delta) {
        methodVisitor.visitVarInsn(Opcodes.LLOAD, localVariableIndex);
        if (delta != 0) {
            methodVisitor.visitLdcInsn(delta);
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

    private static void writeReference(MethodVisitor methodVisitor) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class),
                "writeReference",
                ("(JLjava/lang/Object;)V"),
                false);
    }

    private static void readReference(MethodVisitor methodVisitor) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class),
                "readReference",
                ("(J)Ljava/lang/Object;"),
                false);
    }

    private static int memorySize(Class<?> type) {
        if (!type.isPrimitive()) {
            throw new IllegalArgumentException("Cannot handle non-primtive parameter type: " + type); // TODO: Add handling for reference parameters.
        }
        return type == long.class || type == double.class ? 8 : 4;
    }
}
