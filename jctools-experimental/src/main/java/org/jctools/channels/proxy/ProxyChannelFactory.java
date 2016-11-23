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
    /**
     * The index of the 'this' object in instance methods
     */
    private static final int LOCALS_INDEX_THIS = 0;

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
                methodDescriptor(int.class, iFace, int.class),
                null,
                null);
        methodVisitor.visitCode();
        
        LocalsHelper locals = LocalsHelper.forInstanceMethod();
        int localIndexOfImpl = locals.newLocal(iFace);
        int localIndexOfLimit = locals.newLocal(int.class);
        int localIndexOfLoopIndex = locals.newLocal(int.class);
        int localIndexOfROffset = locals.newLocal(long.class);
        
        //int i = 0;
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

        // long rOffset = this.readAcquire();
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
            methodVisitor.visitVarInsn(Opcodes.ALOAD, localIndexOfImpl);
            
            int localIndexOfArrayReferenceBaseIndex = Integer.MIN_VALUE;
            for (Class<?> parameterType : method.getParameterTypes()) {
                if (!parameterType.isPrimitive()) {
                    // long referenceArrayIndex = this.consumerReferenceArrayIndex();
                    consumerReferenceArrayIndex(methodVisitor);
                    localIndexOfArrayReferenceBaseIndex = locals.newLocal(long.class);
                    methodVisitor.visitVarInsn(Opcodes.LSTORE, localIndexOfArrayReferenceBaseIndex);
                    break;
                }
            }

            // # R_OFFSET_DELTA = 4
            // #FOREACH param in method
            int rOffsetDelta = 4;
            int arrayReferenceBaseIndexDelta = 0;
            for (Class<?> parameterType : method.getParameterTypes()) {
                if (parameterType.isPrimitive()) {
                    // #PUSH: UnsafeAccess.UNSAFE.get[param.type](rOffset + #R_OFFSET_DELTA);
                    // #R_OFFSET_DELTA += if param.type in {long, double} 8 else 4;
                    getUnsafe(methodVisitor, parameterType, localIndexOfROffset, rOffsetDelta);
                    rOffsetDelta += memorySize(parameterType);
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
        methodVisitor.visitVarInsn(Opcodes.ILOAD, localIndexOfLoopIndex);
        methodVisitor.visitInsn(Opcodes.IRETURN);

        // size requirement is computed by ASM; complete method.
        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();

        implementBridgeMethod(classVisitor, generatedName, "process", int.class, iFace, int.class);
    }
    
    private static void implementConstructor(ClassVisitor classVisitor) {
        MethodVisitor methodVisitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC,
                "<init>",
                methodDescriptor(void.class,
                        int.class,
                        int.class,
                        WaitStrategy.class),
                null,
                null);
        methodVisitor.visitCode();
        
        LocalsHelper locals = LocalsHelper.forInstanceMethod();
        int localIndexOfCapacity = locals.newLocal(int.class);
        int localIndexOfarrayMessageSize = locals.newLocal(int.class);
        int localIndexOfWaitStrategy = locals.newLocal(WaitStrategy.class);

        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitVarInsn(Opcodes.ILOAD, localIndexOfCapacity);
        methodVisitor.visitIntInsn(Opcodes.BIPUSH, 13); // messageSize hardcoded
        methodVisitor.visitVarInsn(Opcodes.ILOAD, localIndexOfarrayMessageSize);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, localIndexOfWaitStrategy);

        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class),
                "<init>",
                methodDescriptor(void.class,
                        int.class,
                        int.class,
                        int.class,
                        WaitStrategy.class),
                false);

        methodVisitor.visitInsn(Opcodes.RETURN);

        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
    }

    private static void implementProxyInstance(ClassVisitor classVisitor, Class<?> iFace, String generatedName) {
        MethodVisitor methodVisitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC,
                "proxyInstance",
                methodDescriptor(iFace, iFace),
                null,
                null);
        methodVisitor.visitCode();

        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitInsn(Opcodes.ARETURN);

        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
        
        implementBridgeMethod(classVisitor, generatedName, "proxyInstance", iFace, iFace);
    }

    private static void implementProxy(ClassVisitor classVisitor, Class<?> iFace, String generatedName) {
        MethodVisitor methodVisitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC,
                "proxy",
                methodDescriptor(iFace),
                null,
                null);

        methodVisitor.visitCode();

        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitInsn(Opcodes.ARETURN);

        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
        
        implementBridgeMethod(classVisitor, generatedName, "proxy", iFace);
    }
    
    private static void implementBridgeMethod(ClassVisitor classVisitor, String generatedName, String methodName, Class<?> returnType, Class<?>... parameterTypes) {
        Class<?> bridgeMethodReturnType = returnType.isPrimitive() ? returnType : Object.class;
        Class<?>[] bridgeMethodParameterTypes = new Class<?>[parameterTypes.length];
        int parameterIndex = 0;
        // Bridge methods use only Object's, so replace all non-Object types.
        for (Class<?> parameterType : parameterTypes) {
            bridgeMethodParameterTypes[parameterIndex++] = parameterType.isPrimitive() ? parameterType : Object.class;
        }
        
        MethodVisitor methodVisitor = classVisitor.visitMethod(Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PUBLIC,
                methodName,
                methodDescriptor(bridgeMethodReturnType, bridgeMethodParameterTypes),
                null,
                null);

        methodVisitor.visitCode();

        LocalsHelper locals = LocalsHelper.forInstanceMethod();

        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        for (Class<?> parameterType : parameterTypes) {
            int localIndexOfParameter = locals.newLocal(parameterType);
            int loadOpCode = Type.getType(parameterType).getOpcode(Opcodes.ILOAD);
            methodVisitor.visitVarInsn(loadOpCode, localIndexOfParameter);
            
            if (!parameterType.isPrimitive()) {
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(parameterType));
            }
        }
        
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                generatedName,
                methodName,
                methodDescriptor(returnType, parameterTypes),
                false);
        
        if (!returnType.isPrimitive()) {
            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(returnType));
        }
        int returnOpcode = Type.getType(returnType).getOpcode(Opcodes.IRETURN);
        methodVisitor.visitInsn(returnOpcode);
        
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
        
        LocalsHelper locals = LocalsHelper.forInstanceMethod();
        
        boolean containsReferences = false;
        for (Class<?> parameterType : method.getParameterTypes()) {
            locals.newLocal(parameterType);
            containsReferences |= !parameterType.isPrimitive();
        }
        
        int localIndexOfWOffset = locals.newLocal(long.class);

        // long wOffset = this.writeAcquireWithWaitStrategy();
        writeAcquireWithWaitStrategy(methodVisitor);        
        methodVisitor.visitVarInsn(Opcodes.LSTORE, localIndexOfWOffset);
        

        int localIndexOfArrayReferenceBaseIndex = Integer.MIN_VALUE;
        if (containsReferences) {
            // long arrayReferenceBaseIndex = this.producerReferenceArrayIndex();
            producerReferenceArrayIndex(methodVisitor);
            localIndexOfArrayReferenceBaseIndex = locals.newLocal(long.class);
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
                varOffset += putUnsafe(methodVisitor, parameterType, localIndexOfWOffset, wOffsetDelta, varOffset);
                wOffsetDelta += memorySize(parameterType);
            } else {
                putReference(methodVisitor, parameterType, localIndexOfArrayReferenceBaseIndex, arrayReferenceBaseIndexDelta, varOffset);
                varOffset += Type.getType(parameterType).getSize();
                arrayReferenceBaseIndexDelta++;
            }
        }
        // #END

        // this.writeRelease(wOffset, #TYPE);
        writeRelease(methodVisitor, localIndexOfWOffset, type);

        // return;
        methodVisitor.visitInsn(Opcodes.RETURN);

        // complete method, ASM computes size requirement.
        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
    }

    private static void producerReferenceArrayIndex(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class), "producerReferenceArrayIndex", "()J", false);
    }

    private static void consumerReferenceArrayIndex(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class), "consumerReferenceArrayIndex", "()J", false);
    }

    private static void writeAcquireWithWaitStrategy(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class),
                "writeAcquireWithWaitStrategy",
                "()J",
                false);
    }

    private static void writeRelease(MethodVisitor methodVisitor, int wOffset, int type) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitVarInsn(Opcodes.LLOAD, wOffset);
        methodVisitor.visitLdcInsn(type);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class), "writeRelease", "(JI)V", false);
    }

    private static void readAcquire(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class), "readAcquire", "()J", false);
    }

    private static void readRelease(MethodVisitor methodVisitor, int wOffset) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitVarInsn(Opcodes.LLOAD, wOffset);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.class), "readRelease", "(J)V", false);
    }

    private static int getUnsafe(MethodVisitor methodVisitor, Class<?> parameterType, int localIndexOfROffset, int rOffsetDelta) {
        loadUnsafe(methodVisitor);
        loadWOffset(methodVisitor, parameterType, localIndexOfROffset, rOffsetDelta);
        return parameterTypeUnsafe(methodVisitor, parameterType, false);
    }

    private static int putUnsafe(MethodVisitor methodVisitor, Class<?> parameterType, int wOffset, int wOffsetDelta, int varOffset) {
        loadUnsafe(methodVisitor);
        loadWOffset(methodVisitor, parameterType, wOffset, wOffsetDelta);
        methodVisitor.visitVarInsn(Type.getType(parameterType).getOpcode(Opcodes.ILOAD), varOffset);
        return parameterTypeUnsafe(methodVisitor, parameterType, true);
    }

    private static void getReference(MethodVisitor methodVisitor, Class<?> parameterType, int localIndexOfArrayReferenceBaseIndex, int arrayReferenceBaseIndexDelta) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        loadLocalIndexAndApplyDelta(methodVisitor, localIndexOfArrayReferenceBaseIndex, arrayReferenceBaseIndexDelta);
        readReference(methodVisitor);
        if (parameterType != Object.class) {
            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(parameterType));
        }
    }

    private static void putReference(MethodVisitor methodVisitor, Class<?> parameterType, int localIndexOfArrayReferenceBaseIndex, int arrayReferenceBaseIndexDelta, int varOffset) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
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
    
    private static String methodDescriptor(Class<?> returnType, Class<?>... parameterTypes) {
        Type[] argumentTypes = new Type[parameterTypes.length];
        int typeIndex = 0;
        for (Class<?> parameterType : parameterTypes) {
            argumentTypes[typeIndex++] = Type.getType(parameterType);
        }
        return Type.getMethodDescriptor(Type.getType(returnType), argumentTypes);
    }
}
