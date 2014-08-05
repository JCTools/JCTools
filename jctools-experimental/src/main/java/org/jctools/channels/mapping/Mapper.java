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
import org.jctools.channels.spsc.SpscChannelConsumer;
import org.jctools.channels.spsc.SpscChannelProducer;
import org.jctools.channels.spsc.SpscOffHeapFixedSizeRingBuffer;
import org.objectweb.asm.*;

import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;

import static org.jctools.channels.mapping.BytecodeGenerator.Customisation;
import static org.jctools.channels.mapping.Primitive.replaceWithPrimitive;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * Entry class into type mapping code. Does flyweight generation.
 *
 * @param <S> the struct element type
 */
public final class Mapper<S> {

	private final TypeInspector inspector;
    private final Class<S> structInterface;
    private final boolean classFileDebugEnabled;

    public Mapper(Class<S> structInterface, boolean classFileDebugEnabled) {
        this.structInterface = structInterface;
        this.classFileDebugEnabled = classFileDebugEnabled;
        inspector = new TypeInspector(structInterface);
    }

    /**
     * @return the size that each message takes up in bytes
     */
    public int getSizeInBytes() {
        return inspector.getSizeInBytes();
    }

    public SpscChannelProducer<S> newProducer(Object... args) {
        Customisation customisation = new Customisation() {
            @Override
            public void customise(ClassVisitor writer) {
                declareGetWriter(writer, getType(structInterface));

                // Still need to generate the java.lang.Object version because of erasure
                declareGetWriter(writer, getType(Object.class));
            }

            private void declareGetWriter(ClassVisitor writer, Type returnType) {
                // public ? currentElement()
                String descriptor = getMethodDescriptor(returnType);
                MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "currentElement", descriptor, null, null);
                method.visitCode();

                // return this;
                method.visitVarInsn(ALOAD, 0);
                method.visitInsn(ARETURN);
                method.visitMaxs(1, 1);
                method.visitEnd();
            }
        };

        return newFlyweight(SpscChannelProducer.class, customisation, args);
    }

    public SpscChannelConsumer<S> newConsumer(Object... args) {
        Customisation customisation = new Customisation() {
            @Override
            public void customise(ClassVisitor writer) {
                final String channelConsumer = getInternalName(SpscChannelConsumer.class);
                final String channelReceiver = getInternalName(ChannelReceiver.class);
                final String spscOffHeapFixedSizeRingBuffer = getInternalName(SpscOffHeapFixedSizeRingBuffer.class);

                // public boolean read() {
                String returnBoolean = getMethodDescriptor(Type.BOOLEAN_TYPE);
                MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "read", returnBoolean, null, null);
                method.visitCode();

                // pointer = readAcquire();
                method.visitVarInsn(ALOAD, 0);
                method.visitVarInsn(ALOAD, 0);
                method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        spscOffHeapFixedSizeRingBuffer,
                        "readAcquire",
                        "()J",
                        false);
                method.visitFieldInsn(
                        PUTFIELD,
                        channelConsumer,
                        "pointer",
                        "J");

                // if (pointer == EOF) {
                Label accept = new Label();
                method.visitVarInsn(ALOAD, 0);
                method.visitFieldInsn(
                        GETFIELD,
                        channelConsumer,
                        "pointer",
                        "J");
                method.visitLdcInsn(SpscOffHeapFixedSizeRingBuffer.EOF);
                method.visitInsn(LCMP);
                method.visitJumpInsn(IFNE, accept);
                //     return false;
                method.visitLdcInsn(0);
                method.visitInsn(IRETURN);
                // }

                // receiver.accept((E) this);
                method.visitLabel(accept);
                method.visitVarInsn(ALOAD, 0);
                method.visitFieldInsn(
                        GETFIELD,
                        channelConsumer,
                        "receiver",
                        getDescriptor(ChannelReceiver.class));
                method.visitVarInsn(ALOAD, 0);
                method.visitMethodInsn(
                        INVOKEINTERFACE,
                        channelReceiver,
                        "accept",
                        getMethodDescriptor(VOID_TYPE, getType(Object.class)),
                        true);

                // readRelease(pointer);
                method.visitVarInsn(ALOAD, 0);
                method.visitVarInsn(ALOAD, 0);
                method.visitFieldInsn(
                        GETFIELD,
                        channelConsumer,
                        "pointer",
                        "J");
                method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        spscOffHeapFixedSizeRingBuffer,
                        "readRelease",
                        "(J)V",
                        false);

                // return true;
                method.visitLdcInsn(1);
                method.visitInsn(IRETURN);

                //  }
                method.visitMaxs(2, 2);
                method.visitEnd();
            }
        };

        return newFlyweight(SpscChannelConsumer.class, customisation, args);
    }

    private <I> I newFlyweight(Class<I> type, Customisation customisation, Object[] args) {
        try {
            Class<?>[] constructorParameterTypes = getTypes(args);
            Class<I> implementation = new BytecodeGenerator<S,I>(inspector, type, constructorParameterTypes,
                                                                 structInterface, classFileDebugEnabled,
                                                                 customisation).generate();

            Constructor<I> constructor = implementation.getConstructor(constructorParameterTypes);
            return constructor.newInstance(args);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Class<?>[] getTypes(Object... args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            Class<?> type = args[i].getClass();
            type = replaceWithPrimitive(type);
            type = usePublicApiClass(type);
            types[i] = type;
        }
        return types;
    }

    private Class<?> usePublicApiClass(Class<?> type) {
        if ("DirectByteBuffer".equals(type.getSimpleName()))
            return ByteBuffer.class;

        if (ChannelReceiver.class.isAssignableFrom(type))
            return ChannelReceiver.class;

        return type;
    }

}
