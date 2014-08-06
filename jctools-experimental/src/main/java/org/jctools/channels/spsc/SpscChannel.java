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
package org.jctools.channels.spsc;

import org.jctools.channels.Channel;
import org.jctools.channels.ChannelConsumer;
import org.jctools.channels.ChannelProducer;
import org.jctools.channels.ChannelReceiver;
import org.jctools.channels.mapping.BytecodeGenerator;
import org.jctools.channels.mapping.Mapper;
import org.jctools.util.Pow2;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.nio.ByteBuffer;

import static org.jctools.channels.spsc.SpscOffHeapFixedSizeRingBuffer.getLookaheadStep;
import static org.jctools.channels.spsc.SpscOffHeapFixedSizeRingBuffer.getRequiredBufferSize;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

public final class SpscChannel<E> implements Channel<E> {

    private final int elementSize;
    private final Mapper<E> mapper;
    private final ByteBuffer buffer;
    private final int maximumCapacity;
    private final int requestedCapacity;
    private final SpscChannelProducer<E> producer;

    /**
	 * This is to be used for an IPC queue with the direct buffer used being a memory
	 * mapped file.
	 *
	 * @param buffer
	 * @param requestedCapacity
	 */
    // TODO: take an initialize parameter
	public SpscChannel(final ByteBuffer buffer, final int requestedCapacity, final Class<E> type) {
        this.requestedCapacity = requestedCapacity;
        this.maximumCapacity = getMaximumCapacity(requestedCapacity);
        this.buffer = buffer;
        mapper = new Mapper<E>(type, false);
        elementSize = mapper.getSizeInBytes();

        checkSufficientCapacity();
        checkByteBuffer();

        producer = newProducer(type, buffer, maximumCapacity, elementSize);
    }

    private int getMaximumCapacity(int requestedCapacity) {
        return Pow2.roundToPowerOfTwo(requestedCapacity + getLookaheadStep(requestedCapacity));
    }

    private void checkByteBuffer() {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Channels only work with direct or memory mapped buffers");
        }
    }

    private void checkSufficientCapacity() {
        final int requiredCapacityInBytes = getRequiredBufferSize(maximumCapacity, elementSize);
        if (buffer.capacity() < requiredCapacityInBytes) {
            throw new IllegalArgumentException("Failed to meet required maximumCapacity in bytes: " + requiredCapacityInBytes);
        }
    }

    public ChannelConsumer consumer(ChannelReceiver<E> receiver) {
        return newConsumer(buffer, maximumCapacity, elementSize, receiver);
    }

    public ChannelProducer<E> producer() {
        return producer;
    }

    public int size() {
        return producer.size();
	}

    public int maximumCapacity() {
        return maximumCapacity;
    }

    @Override
    public int requestedCapacity() {
        return requestedCapacity;
    }

    public boolean isEmpty() {
		return size() == 0;
	}


    private SpscChannelProducer<E> newProducer(final Class<E> type, final Object... args) {
        BytecodeGenerator.Customisation customisation = new BytecodeGenerator.Customisation() {
            @Override
            public void customise(ClassVisitor writer) {
                declareGetWriter(writer, getType(type));

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

        return mapper.newFlyweight(SpscChannelProducer.class, customisation, args);
    }

    private SpscChannelConsumer<E> newConsumer(Object... args) {
        BytecodeGenerator.Customisation customisation = new BytecodeGenerator.Customisation() {
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

        return mapper.newFlyweight(SpscChannelConsumer.class, customisation, args);
    }

}
