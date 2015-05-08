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

import org.jctools.util.JvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;

import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.util.UnsafeAccess.UNSAFE;

abstract class ConcurrentCircularArrayQueueL0Pad<E> extends AbstractQueue<E> implements MessagePassingQueue<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

/**
 * A concurrent access enabling class used by circular array based queues this class exposes an offset computation
 * method along with differently memory fenced load/store methods into the underlying array. The class is pre-padded and
 * the array is padded on either side to help with False sharing prvention. It is expected theat subclasses handle post
 * padding.
 * <p>
 * Offset calculation is separate from access to enable the reuse of a give compute offset.
 * <p>
 * Load/Store methods using a <i>buffer</i> parameter are provided to allow the prevention of final field reload after a
 * LoadLoad barrier.
 * <p>
 *
 * @author nitsanw
 *
 * @param <E>
 */
public abstract class ConcurrentCircularArrayQueue<E> extends ConcurrentCircularArrayQueueL0Pad<E> {
    protected static final int SPARSE_SHIFT = Integer.getInteger("sparse.shift", 0);
    protected static final int REF_BUFFER_PAD;
    private static final long REF_ARRAY_BASE;
    private static final int REF_ELEMENT_SHIFT;
    static {
        final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(Object[].class);
        if (4 == scale) {
            REF_ELEMENT_SHIFT = 2 + SPARSE_SHIFT;
        } else if (8 == scale) {
            REF_ELEMENT_SHIFT = 3 + SPARSE_SHIFT;
        } else {
            throw new IllegalStateException("Unknown pointer size");
        }
        // 2 cache lines pad
        REF_BUFFER_PAD = (JvmInfo.CACHE_LINE_SIZE * 2) / scale;
        // Including the buffer pad in the array base offset
        REF_ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(Object[].class) + (REF_BUFFER_PAD * scale);
    }
    protected final long mask;
    // @Stable :(
    protected final E[] buffer;

    @SuppressWarnings("unchecked")
    public ConcurrentCircularArrayQueue(int capacity) {
        int actualCapacity = Pow2.roundToPowerOfTwo(capacity);
        mask = actualCapacity - 1;
        // pad data on either end with some empty slots.
        buffer = (E[]) new Object[(actualCapacity << SPARSE_SHIFT) + REF_BUFFER_PAD * 2];
    }

    /**
     * @param index desirable element index
     * @return the offset in bytes within the array for a given index.
     */
    protected final long calcElementOffset(long index) {
        return calcElementOffset(index, mask);
    }
    /**
     * @param index desirable element index
     * @param mask
     * @return the offset in bytes within the array for a given index.
     */
    protected static final long calcElementOffset(long index, long mask) {
        return REF_ARRAY_BASE + ((index & mask) << REF_ELEMENT_SHIFT);
    }
    /**
     * A plain store (no ordering/fences) of an element to a given offset
     *
     * @param offset computed via {@link ConcurrentCircularArrayQueue#calcElementOffset(long)}
     * @param e a kitty
     */
    protected final void spElement(long offset, E e) {
        spElement(buffer, offset, e);
    }

    /**
     * A plain store (no ordering/fences) of an element to a given offset
     *
     * @param buffer this.buffer
     * @param offset computed via {@link ConcurrentCircularArrayQueue#calcElementOffset(long)}
     * @param e an orderly kitty
     */
    protected static final void spElement(Object[] buffer, long offset, Object e) {
        UNSAFE.putObject(buffer, offset, e);
    }

    /**
     * An ordered store(store + StoreStore barrier) of an element to a given offset
     *
     * @param offset computed via {@link ConcurrentCircularArrayQueue#calcElementOffset(long)}
     * @param e an orderly kitty
     */
    protected final void soElement(long offset, E e) {
        soElement(buffer, offset, e);
    }

    /**
     * An ordered store(store + StoreStore barrier) of an element to a given offset
     *
     * @param buffer this.buffer
     * @param offset computed via {@link ConcurrentCircularArrayQueue#calcElementOffset(long)}
     * @param e an orderly kitty
     */
    protected static final void soElement(Object[] buffer, long offset, Object e) {
        UNSAFE.putOrderedObject(buffer, offset, e);
    }

    /**
     * A plain load (no ordering/fences) of an element from a given offset.
     *
     * @param offset computed via {@link ConcurrentCircularArrayQueue#calcElementOffset(long)}
     * @return the element at the offset
     */
    @SuppressWarnings("unchecked")
    protected final E lpElement(long offset) {
        return (E) lpElement(buffer, offset);
    }

    /**
     * A plain load (no ordering/fences) of an element from a given offset.
     *
     * @param buffer this.buffer
     * @param offset computed via {@link ConcurrentCircularArrayQueue#calcElementOffset(long)}
     * @return the element at the offset
     */
    protected static final Object lpElement(Object[] buffer, long offset) {
        return UNSAFE.getObject(buffer, offset);
    }

    /**
     * A volatile load (load + LoadLoad barrier) of an element from a given offset.
     *
     * @param offset computed via {@link ConcurrentCircularArrayQueue#calcElementOffset(long)}
     * @return the element at the offset
     */
    @SuppressWarnings("unchecked")
    protected final E lvElement(long offset) {
        return (E) lvElement(buffer, offset);
    }

    /**
     * A volatile load (load + LoadLoad barrier) of an element from a given offset.
     *
     * @param buffer this.buffer
     * @param offset computed via {@link ConcurrentCircularArrayQueue#calcElementOffset(long)}
     * @return the element at the offset
     */
    protected static final Object lvElement(Object[] buffer, long offset) {
        return UNSAFE.getObjectVolatile(buffer, offset);
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }
    @Override
    public void clear() {
        // we have to test isEmpty because of the weaker poll() guarantee
        while (poll() != null || !isEmpty())
            ;
    }
}
