package org.jctools.channels.proxy;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.jctools.channels.WaitStrategy;
import org.jctools.channels.mpsc.MpscOffHeapFixedSizeRingBuffer;
import org.jctools.channels.spsc.SpscOffHeapFixedSizeRingBuffer;
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
    private static final int START_TYPE_ID = 10;

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
    
    public static long writeAcquireWithWaitStrategy(ProxyChannelRingBuffer channelBackend, WaitStrategy waitStrategy) {
        long wOffset;
        int idleCounter = 0;
        while ((wOffset = channelBackend.writeAcquire()) == ProxyChannelRingBuffer.EOF) {
            idleCounter = waitStrategy.idle(idleCounter);
        }
        return wOffset;
    }
    
    /**
     * Create a default single producer single consumer (SPSC) proxy channel.
     * 
     * @param capacity
     *            The minimum capacity for unprocessed invocations the channel
     *            should support
     * @param iFace
     *            Interface the proxy must implement
     * @param waitStrategy
     *            A wait strategy to be invoked when the backing data structure
     *            is full
     * @return A proxy channel instance
     */
    public static <E> ProxyChannel<E> createSpscProxy(int capacity, 
            Class<E> iFace, 
            WaitStrategy waitStrategy) {
        return createProxy(capacity, 
                iFace, 
                waitStrategy, 
                SpscOffHeapFixedSizeRingBuffer.class);
    }

    /**
     * Create a default multi producer single consumer (MPSC) proxy channel.
     * 
     * @param capacity
     *            The minimum capacity for unprocessed invocations the channel
     *            should support
     * @param iFace
     *            Interface the proxy must implement
     * @param waitStrategy
     *            A wait strategy to be invoked when the backing data structure
     *            is full
     * @return A proxy channel instance
     */
    public static <E> ProxyChannel<E> createMpscProxy(int capacity,
            Class<E> iFace,
            WaitStrategy waitStrategy) {
        return createProxy(capacity,
                iFace,
                waitStrategy,
                MpscOffHeapFixedSizeRingBuffer.class);
    }
    
    /**
     * Create a proxy channel using a user supplied back end.
     * 
     * @param capacity
     *            The minimum capacity for unprocessed invocations the channel
     *            should support
     * @param iFace
     *            Interface the proxy must implement
     * @param waitStrategy
     *            A wait strategy to be invoked when the backing data structure
     *            is full
     * @param backendType
     *            The back end type, the proxy will inherit from this channel
     *            type. The back end type must define a constructor with signature:
     *            <code>(int capacity, int primitiveMessageSize, int referenceMessageSize)</code>
     * @return A proxy channel instance
     */
    public static <E> ProxyChannel<E> createProxy(int capacity, 
            Class<E> iFace, 
            WaitStrategy waitStrategy,
            Class<? extends ProxyChannelRingBuffer> backendType) {
        if (!iFace.isInterface()) {
            throw new IllegalArgumentException("Not an interface: " + iFace);
        }
        
        

        String generatedName = Type.getInternalName(iFace) + "$JCTools$ProxyChannel$" + backendType.getSimpleName();
        Class<?> preExisting = findExisting(generatedName, iFace);
        if (preExisting != null) {
            return instantiate(preExisting, capacity, waitStrategy);
        }

        List<Method> relevantMethods = findRelevantMethods(iFace);
        if (relevantMethods.isEmpty()) {
            throw new IllegalArgumentException("Does not declare any abstract methods: " + iFace);
        }

        // max number of reference arguments of any method
        int referenceMessageSize = 0;
        // max bytes required to store the primitive args of a call frame of any method
        int primitiveMessageSize = 0;
        
        for (Method method : relevantMethods) {
            int primitiveMethodSize = 0;
            int referenceCount = 0;
            for (Class<?> parameterType : method.getParameterTypes()) {
                if (parameterType.isPrimitive()) {
                    primitiveMethodSize += primitiveMemorySize(parameterType);
                } else {
                    referenceCount++;
                }
            }
            primitiveMessageSize = Math.max(primitiveMessageSize, primitiveMethodSize);
            referenceMessageSize = Math.max(referenceMessageSize, referenceCount);
        }
        
        // We need to add an int to this for the 'type' value on the message frame
        primitiveMessageSize += primitiveMemorySize(int.class);
        
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        classWriter.visit(Opcodes.V1_4,
                Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                generatedName,
                null,
                Type.getInternalName(backendType),
                new String[]{Type.getInternalName(ProxyChannel.class), Type.getInternalName(iFace)});
        implementInstanceFields(classWriter);
        implementConstructor(classWriter, backendType, generatedName, primitiveMessageSize, referenceMessageSize);
        implementProxyInstance(classWriter, iFace, generatedName);
        implementProxy(classWriter, iFace, generatedName);

        implementUserMethods(classWriter, relevantMethods, generatedName, backendType);
        implementProcess(classWriter, backendType, relevantMethods, iFace, generatedName);

        classWriter.visitEnd();

        synchronized (ProxyChannelFactory.class) {
            preExisting = findExisting(generatedName, iFace);
            if (preExisting != null) {
                return instantiate(preExisting, capacity, waitStrategy);
            }
            byte[] byteCode = classWriter.toByteArray();
            printClassBytes(byteCode);
            // Caveat: The interface and JCTools must be on the same class loader. Maybe class loader should be an argument? Overload?
            Class<?> definedClass = DefineClassHelper.defineClass(iFace, generatedName, byteCode);
            return instantiate(definedClass, capacity, waitStrategy);
        }
    }

    private static void implementUserMethods(ClassWriter classWriter, List<Method> relevantMethods, String generatedName, Class<?extends ProxyChannelRingBuffer> backendType) {
        int type = START_TYPE_ID;
        for (Method method : relevantMethods) {
            implementUserMethod(method, classWriter, type++, generatedName, backendType);
        }
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
    private static <E> ProxyChannel<E> instantiate(Class<?> proxy, int capacity, WaitStrategy waitStrategy) {
        try {
            return (ProxyChannel<E>) proxy.getDeclaredConstructor(int.class, WaitStrategy.class).newInstance(capacity, waitStrategy);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void implementProcess(ClassVisitor classVisitor,
            Class<? extends ProxyChannelRingBuffer> backendType, 
            List<Method> methods, 
            Class<?> iFace, 
            String generatedName) {
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
        int localIndexOfTypeId = locals.newLocal(int.class);
        
        // Label the start of scope for all locals
        Label localScopeStart = new Label();
        methodVisitor.visitLabel(localScopeStart);
        
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
        readAcquire(methodVisitor, backendType);
        methodVisitor.visitVarInsn(Opcodes.LSTORE, localIndexOfROffset);


        // if (rOffset == EOF) goto <loopEnd>;
        methodVisitor.visitVarInsn(Opcodes.LLOAD, localIndexOfROffset);
        methodVisitor.visitLdcInsn(ProxyChannelRingBuffer.EOF);
        methodVisitor.visitInsn(Opcodes.LCMP);
        methodVisitor.visitJumpInsn(Opcodes.IFEQ, loopEnd);

        // switch(UnsafeAccess.UNSAFE.getInt(rOffset)) // start with case 1, increment by 1; represents "type"
        getUnsafe(methodVisitor, int.class, localIndexOfROffset, 0);
        methodVisitor.visitVarInsn(Opcodes.ISTORE, localIndexOfTypeId);
        methodVisitor.visitVarInsn(Opcodes.ILOAD, localIndexOfTypeId);
        
        int low = START_TYPE_ID;
        int high = low + (cases.length - 1);
        methodVisitor.visitTableSwitchInsn(low, high, endOfSwitch, cases);

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
                    // long referenceArrayIndex = this.consumerReferenceArrayIndex(rOffset);
                    consumerReferenceArrayIndex(methodVisitor, localIndexOfROffset, backendType);
                    // TODO: Should reuse local indices or when an iFace grows too big we might have problems here
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
                    rOffsetDelta += primitiveMemorySize(parameterType);
                } else {
                    getReference(methodVisitor,
                            parameterType,
                            localIndexOfArrayReferenceBaseIndex,
                            arrayReferenceBaseIndexDelta,
                            backendType);
                    arrayReferenceBaseIndexDelta++;
                }
            }
            // #END

            // this.readRelease(rOffset);
            readRelease(methodVisitor, localIndexOfROffset, backendType);

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
        

        // Label the end of scope for all locals
        Label localScopeEnd = new Label();
        methodVisitor.visitLabel(localScopeEnd);

        // Declare local variables to aid in debugging
        methodVisitor.visitLocalVariable("this", "L" + generatedName + ";", null, localScopeStart, localScopeEnd, 0);
        methodVisitor.visitLocalVariable("impl", Type.getType(iFace).getDescriptor(), null, localScopeStart, localScopeEnd, localIndexOfImpl);
        methodVisitor.visitLocalVariable("limit", Type.getType(int.class).getDescriptor(), null, localScopeStart, localScopeEnd, localIndexOfLimit);
        methodVisitor.visitLocalVariable("loopIndex", Type.getType(int.class).getDescriptor(), null, localScopeStart, localScopeEnd, localIndexOfLoopIndex);
        methodVisitor.visitLocalVariable("rOffset", Type.getType(long.class).getDescriptor(), null, localScopeStart, localScopeEnd, localIndexOfROffset);
        methodVisitor.visitLocalVariable("typeId", Type.getType(int.class).getDescriptor(), null, localScopeStart, localScopeEnd, localIndexOfTypeId);
        
        // size requirement is computed by ASM; complete method.
        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();

        implementBridgeMethod(classVisitor, generatedName, "process", int.class, iFace, int.class);
    }
    
    private static void implementInstanceFields(ClassVisitor classVisitor) {
        classVisitor.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                "waitStrategy",
                Type.getDescriptor(WaitStrategy.class),
                null,
                null);
    }
    
    private static void implementConstructor(ClassVisitor classVisitor,
            Class<? extends ProxyChannelRingBuffer> parentType,
            String generatedName,
            int primitiveMessageSize,
            int referenceMessageSize) {
        MethodVisitor methodVisitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC,
                "<init>",
                methodDescriptor(void.class,
                        int.class,
                        WaitStrategy.class),
                null,
                null);
        methodVisitor.visitCode();
        
        LocalsHelper locals = LocalsHelper.forInstanceMethod();
        int localIndexOfCapacity = locals.newLocal(int.class);
        int localIndexOfWaitStrategy = locals.newLocal(WaitStrategy.class);

        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitVarInsn(Opcodes.ILOAD, localIndexOfCapacity);
        methodVisitor.visitLdcInsn(primitiveMessageSize);
        methodVisitor.visitLdcInsn(referenceMessageSize);
        
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                Type.getInternalName(parentType),
                "<init>",
                methodDescriptor(void.class,
                        int.class,
                        int.class,
                        int.class),
                false);
        
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, localIndexOfWaitStrategy);
        methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, generatedName, "waitStrategy", Type.getDescriptor(WaitStrategy.class));
        
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

    private static void implementUserMethod(Method method, 
            ClassVisitor classVisitor, 
            int type, 
            String generatedName,
            Class<? extends ProxyChannelRingBuffer> backendType) {

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
        writeAcquireWithWaitStrategy(methodVisitor, generatedName, backendType);
        methodVisitor.visitVarInsn(Opcodes.LSTORE, localIndexOfWOffset);
        

        int localIndexOfArrayReferenceBaseIndex = Integer.MIN_VALUE;
        if (containsReferences) {
            // long arrayReferenceBaseIndex = this.producerReferenceArrayIndex(wOffset);
            producerReferenceArrayIndex(methodVisitor, localIndexOfWOffset, backendType);
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
                wOffsetDelta += primitiveMemorySize(parameterType);
            } else {
                putReference(methodVisitor,
                        parameterType,
                        localIndexOfArrayReferenceBaseIndex,
                        arrayReferenceBaseIndexDelta,
                        varOffset,
                        backendType);
                varOffset += Type.getType(parameterType).getSize();
                arrayReferenceBaseIndexDelta++;
            }
        }
        // #END

        // this.writeRelease(wOffset, #TYPE);
        writeRelease(methodVisitor, localIndexOfWOffset, type, backendType);

        // return;
        methodVisitor.visitInsn(Opcodes.RETURN);

        // complete method, ASM computes size requirement.
        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
    }

    private static void producerReferenceArrayIndex(MethodVisitor methodVisitor, int localIndexOfWOffset, Class<? extends ProxyChannelRingBuffer> backendType) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitVarInsn(Opcodes.LLOAD, localIndexOfWOffset);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(backendType), "producerReferenceArrayIndex", "(J)J", false);
    }

    private static void consumerReferenceArrayIndex(MethodVisitor methodVisitor, int localIndexOfROffset, Class<? extends ProxyChannelRingBuffer> backendType) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitVarInsn(Opcodes.LLOAD, localIndexOfROffset);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(backendType), "consumerReferenceArrayIndex", "(J)J", false);
    }

    private static void writeAcquireWithWaitStrategy(MethodVisitor methodVisitor, String generatedName, Class<? extends ProxyChannelRingBuffer> backendType) {
        // One of these is for the getfield bytecode, the other is as the first arg to the writeAcquireWithWaitStrategy
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, generatedName, "waitStrategy", Type.getDescriptor(WaitStrategy.class));
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC,
                Type.getInternalName(ProxyChannelFactory.class),
                "writeAcquireWithWaitStrategy",
                methodDescriptor(long.class, ProxyChannelRingBuffer.class, WaitStrategy.class),
                false);
    }

    private static void writeRelease(MethodVisitor methodVisitor, int wOffset, int type, Class<? extends ProxyChannelRingBuffer> backendType) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitVarInsn(Opcodes.LLOAD, wOffset);
        methodVisitor.visitLdcInsn(type);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(backendType), "writeRelease", "(JI)V", false);
    }

    private static void readAcquire(MethodVisitor methodVisitor, Class<? extends ProxyChannelRingBuffer> backendType) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(backendType), "readAcquire", "()J", false);
    }

    private static void readRelease(MethodVisitor methodVisitor, int wOffset, Class<? extends ProxyChannelRingBuffer> backendType) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        methodVisitor.visitVarInsn(Opcodes.LLOAD, wOffset);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(backendType), "readRelease", "(J)V", false);
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

    private static void getReference(MethodVisitor methodVisitor,
            Class<?> parameterType,
            int localIndexOfArrayReferenceBaseIndex,
            int arrayReferenceBaseIndexDelta,
            Class<? extends ProxyChannelRingBuffer> backendType) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        loadLocalIndexAndApplyDelta(methodVisitor, localIndexOfArrayReferenceBaseIndex, arrayReferenceBaseIndexDelta);
        readReference(methodVisitor, backendType);
        if (parameterType != Object.class) {
            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(parameterType));
        }
    }

    private static void putReference(MethodVisitor methodVisitor,
            Class<?> parameterType,
            int localIndexOfArrayReferenceBaseIndex,
            int arrayReferenceBaseIndexDelta,
            int varOffset,
            Class<? extends ProxyChannelRingBuffer> backendType) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, LOCALS_INDEX_THIS);
        loadLocalIndexAndApplyDelta(methodVisitor, localIndexOfArrayReferenceBaseIndex, arrayReferenceBaseIndexDelta);
        methodVisitor.visitVarInsn(Type.getType(parameterType).getOpcode(Opcodes.ILOAD), varOffset);
        
        writeReference(methodVisitor, backendType);
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

    private static void writeReference(MethodVisitor methodVisitor, Class<? extends ProxyChannelRingBuffer> backendType) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                Type.getInternalName(backendType),
                "writeReference",
                ("(JLjava/lang/Object;)V"),
                false);
    }

    private static void readReference(MethodVisitor methodVisitor, Class<? extends ProxyChannelRingBuffer> backend) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                Type.getInternalName(backend),
                "readReference",
                ("(J)Ljava/lang/Object;"),
                false);
    }

    private static int primitiveMemorySize(Class<?> type) {
        if (!type.isPrimitive()) {
            throw new IllegalArgumentException("Cannot handle non-primtive parameter type: " + type);
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
