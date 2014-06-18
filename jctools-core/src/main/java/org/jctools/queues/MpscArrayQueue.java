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

import static org.jctools.util.UnsafeAccess.UNSAFE;

import java.util.Queue;

import org.jctools.queues.alt.ConcurrentQueue;
import org.jctools.queues.alt.ConcurrentQueueConsumer;
import org.jctools.queues.alt.ConcurrentQueueProducer;

abstract class MpscArrayQueueL1Pad<E> extends ConcurrentCircularArrayQueue<E> {
    long p10, p11, p12, p13, p14, p15, p16;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscArrayQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpscArrayQueueTailField<E> extends MpscArrayQueueL1Pad<E> {
    private final static long TAIL_OFFSET;

    static {
        try {
            TAIL_OFFSET = UNSAFE.objectFieldOffset(MpscArrayQueueTailField.class.getDeclaredField("tail"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long tail;

    public MpscArrayQueueTailField(int capacity) {
        super(capacity);
    }

    protected final long lvTail() {
        return tail;
    }

    protected final boolean casTail(long expect, long newValue) {
        return UNSAFE.compareAndSwapLong(this, TAIL_OFFSET, expect, newValue);
    }
}

abstract class MpscArrayQueueMidPad<E> extends MpscArrayQueueTailField<E> {
    long p20, p21, p22, p23, p24, p25, p26;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscArrayQueueMidPad(int capacity) {
        super(capacity);
    }
}

abstract class MpscArrayQueueHeadCacheField<E> extends MpscArrayQueueMidPad<E> {
    private volatile long headCache;

    public MpscArrayQueueHeadCacheField(int capacity) {
        super(capacity);
    }

    protected final long lvHeadCache() {
        return headCache;
    }

    protected final void svHeadCache(long v) {
        headCache = v;
    }
}

abstract class MpscArrayQueueL2Pad<E> extends MpscArrayQueueHeadCacheField<E> {
    long p20, p21, p22, p23, p24, p25, p26;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscArrayQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpscArrayQueueHeadField<E> extends MpscArrayQueueL2Pad<E> {
    private final static long HEAD_OFFSET;
    static {
        try {
            HEAD_OFFSET = UNSAFE.objectFieldOffset(MpscArrayQueueHeadField.class.getDeclaredField("head"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long head;

    public MpscArrayQueueHeadField(int capacity) {
        super(capacity);
    }

    protected final long lvHead() {
        return head;
    }

    protected void soHead(long l) {
        UNSAFE.putOrderedLong(this, HEAD_OFFSET, l);
    }
}

public final class MpscArrayQueue<E> extends MpscArrayQueueHeadField<E> implements Queue<E> {
    long p40, p41, p42, p43, p44, p45, p46;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscArrayQueue(final int capacity) {
        super(capacity);
    }

    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }
        final long currHeadCache = lvHeadCache();
        long currentTail;
        do {
            currentTail = lvTail();
            final long wrapPoint = currentTail - capacity;
            if (currHeadCache <= wrapPoint) {
                long currHead = lvHead();
                if (currHead <= wrapPoint) {
                    return false;
                } else {
                    svHeadCache(currHead);
                }
            }
        } while (!casTail(currentTail, currentTail + 1));
        final long offset = calcElementOffset(currentTail);
        soElement(offset, e);
        return true;
    }

    /**
     * @param e a bludgeoned hamster
     * @return 1 if full, -1 if CAS failed, 0 if successful
     */
    public int tryOffer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        long currentTail = lvTail();
        final long currHeadCache = lvHeadCache();
        final long wrapPoint = currentTail - capacity;
        if (currHeadCache <= wrapPoint) {
            long currHead = lvHead();
            if (currHead <= wrapPoint) {
                return 1; // FULL
            } else {
                svHeadCache(currHead);
            }
        }
        if (!casTail(currentTail, currentTail + 1)) {
            return -1; // CAS FAIL
        }
        final long offset = calcElementOffset(currentTail);
        soElement(offset, e);
        return 0; // AWESOME
    }

    @Override
    public E poll() {
        final long currHead = lvHead();
        final long offset = calcElementOffset(currHead);
        final E[] lb = buffer;
        // If we can't see the next available element, consider the queue empty
        final E e = lvElement(lb, offset);
        if (null == e) {
            return null; // EMPTY
        }
        spElement(lb, offset, null);
        soHead(currHead + 1);
        return e;
    }

    @Override
    public E peek() {
        return lpElement(calcElementOffset(lvHead()));
    }

    @Override
    public int size() {
        return (int) (lvTail() - lvHead());
    }
}
