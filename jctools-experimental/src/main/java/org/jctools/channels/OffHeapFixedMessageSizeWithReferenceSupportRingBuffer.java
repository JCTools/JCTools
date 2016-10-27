package org.jctools.channels;

import static org.jctools.util.JvmInfo.CACHE_LINE_SIZE;
import static org.jctools.util.UnsafeDirectByteBuffer.allocateAlignedByteBuffer;

import java.nio.ByteBuffer;

import org.jctools.util.Pow2;

public abstract class OffHeapFixedMessageSizeWithReferenceSupportRingBuffer extends OffHeapFixedMessageSizeRingBuffer {

    protected final Object[] references;
    protected final int arrayMessageSize;

    /**
     * 
     * @param capacity in messages, actual capacity will be
     * @param messageSize size in bytes for each message
     * @param arrayMessageSize size in element count for each message
     */
    public OffHeapFixedMessageSizeWithReferenceSupportRingBuffer(final int capacity,
            final int messageSize,
            int arrayMessageSize) {
        this(allocateAlignedByteBuffer(getRequiredBufferSize(capacity, messageSize), CACHE_LINE_SIZE),
                Pow2.roundToPowerOfTwo(capacity),
                true,
                true,
                true,
                messageSize,
                createReferenceArray(capacity, arrayMessageSize),
                arrayMessageSize);
    }

    protected static Object[] createReferenceArray(final int capacity, int arrayMessageSize) {
        return new Object[getRequiredArraySize(capacity, arrayMessageSize)];
    }

    public OffHeapFixedMessageSizeWithReferenceSupportRingBuffer(ByteBuffer buff,
            int capacity,
            boolean isProducer,
            boolean isConsumer,
            boolean initialize,
            int messageSize,
            Object[] references,
            int arrayMessageSize) {
        super(buff, capacity, isProducer, isConsumer, initialize, messageSize);
        this.references = references;
        this.arrayMessageSize = arrayMessageSize;
    }

    public static int getRequiredArraySize(final int capacity, final int messageSize) {
        return Pow2.roundToPowerOfTwo(capacity) * messageSize;
    }

    protected final long arrayIndexForCursor(long currentHead) {
        return arrayIndexForCursor(mask, arrayMessageSize, currentHead);
    }

    protected static long arrayIndexForCursor(long mask,
            int arrayMessageSize,
            long currentHead) {
        return (currentHead & mask) * arrayMessageSize;
    }

    protected long consumerReferenceArrayIndex() {
        final long consumerIndex = lpConsumerIndex();
        return arrayIndexForCursor(consumerIndex);
    }
    
    protected long producerReferenceArrayIndex() {
        final long producerIndex = lpProducerIndex();
        return arrayIndexForCursor(producerIndex);
    }
}
