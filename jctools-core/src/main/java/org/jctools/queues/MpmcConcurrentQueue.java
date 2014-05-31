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
package org.jctools.queues;

import java.util.Queue;

import org.jctools.queues.alt.ConcurrentQueue;
import org.jctools.queues.alt.ConcurrentQueueConsumer;
import org.jctools.queues.alt.ConcurrentQueueProducer;
import org.jctools.util.UnsafeAccess;

abstract class MpmcConcurrentQueueL1Pad<E> extends ConcurrentSequencedCircularArray<E> {
    long p10, p11, p12, p13, p14, p15, p16;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpmcConcurrentQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpmcConcurrentQueueTailField<E> extends MpmcConcurrentQueueL1Pad<E> {
    private final static long TAIL_OFFSET;
    static {
        try {
            TAIL_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(MpmcConcurrentQueueTailField.class
                    .getDeclaredField("tail"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long tail;

    public MpmcConcurrentQueueTailField(int capacity) {
        super(capacity);
    }

    protected final long lvTail() {
        return tail;
    }

    protected final boolean casTail(long expect, long newValue) {
        return UnsafeAccess.UNSAFE.compareAndSwapLong(this, TAIL_OFFSET, expect, newValue);
    }
}

abstract class MpmcConcurrentQueueL2Pad<E> extends MpmcConcurrentQueueTailField<E> {
    long p20, p21, p22, p23, p24, p25, p26;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpmcConcurrentQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpmcConcurrentQueueHeadField<E> extends MpmcConcurrentQueueL2Pad<E> {
    private final static long HEAD_OFFSET;
    static {
        try {
            HEAD_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(MpmcConcurrentQueueHeadField.class
                    .getDeclaredField("head"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long head;

    public MpmcConcurrentQueueHeadField(int capacity) {
        super(capacity);
    }

    protected final long lvHead() {
        return head;
    }

    protected final boolean casHead(long expect, long newValue) {
        return UnsafeAccess.UNSAFE.compareAndSwapLong(this, HEAD_OFFSET, expect, newValue);
    }
}

public final class MpmcConcurrentQueue<E> extends MpmcConcurrentQueueHeadField<E> implements Queue<E>,
        ConcurrentQueue<E>, ConcurrentQueueProducer<E>, ConcurrentQueueConsumer<E> {
    long p40, p41, p42, p43, p44, p45, p46;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpmcConcurrentQueue(final int capacity) {
        super(Math.max(2, capacity));
    }

    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }
        final long[] lsb = sequenceBuffer;
        long currentTail;
        long pOffset;

        for (;;) {
            currentTail = lvTail();
            pOffset = calcSequenceOffset(currentTail);
            long seq = lvSequenceElement(lsb, pOffset);
            long delta = seq - currentTail;
            if (delta == 0) {
                // this is expected if we see this first time around
                if (casTail(currentTail, currentTail + 1)) {
                    break;
                }
                // failed cas, retry 1
            } else if (delta < 0) {
                // poll has not moved this value forward,
                return false;
            } else {
                // another producer beat us
            }
        }
        long offset = calcOffset(currentTail);
        spElement(offset, e);
        // increment position, seeing this value again should lead to retry 2
        soSequenceElement(lsb, pOffset, currentTail + 1);
        return true;
    }

    @Override
    public E poll() {
        final long[] lsb = sequenceBuffer;
        long currentHead;
        long pOffset;
        for (;;) {
            currentHead = lvHead();
            pOffset = calcSequenceOffset(currentHead);
            long seq = lvSequenceElement(lsb, pOffset);
            long delta = seq - (currentHead + 1);
            if (delta == 0) {
                if (casHead(currentHead, currentHead + 1)) {
                    break;
                }
                // failed cas, retry 1
            } else if (delta < 0) {
                return null;
            } else {

            }
        }
        long offset = calcOffset(currentHead);
        final E[] lb = buffer;
        E e = lvElement(lb, offset);
        spElement(lb, offset, null);
        soSequenceElement(lsb, pOffset, currentHead + capacity);
        return e;
    }

    @Override
    public E peek() {
        return lpElement(calcOffset(lvHead()));
    }

    @Override
    public int size() {
        return (int) (lvTail() - lvHead());
    }

    @Override
    public ConcurrentQueueConsumer<E> consumer() {
        return this;
    }

    @Override
    public ConcurrentQueueProducer<E> producer() {
        return this;
    }

    @Override
    public int capacity() {
        return capacity;
    }
}
