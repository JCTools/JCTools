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
package org.jctools.util;

import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;

abstract class PaddedAtomicLongL1Pad extends Number implements java.io.Serializable {
    private static final long serialVersionUID = 1;

    transient long p01, p02, p03, p04, p05, p06, p07;
    transient long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class PaddedAtomicLongL1Field extends PaddedAtomicLongL1Pad {
    volatile long value;
}

abstract class PaddedAtomicLongL2Pad extends PaddedAtomicLongL1Field {

    transient long p01, p02, p03, p04, p05, p06, p07;
    transient long p10, p11, p12, p13, p14, p15, p16, p17;
}

/**
 * A padded version of the {@link java.util.concurrent.atomic.AtomicLong}.
 */
public class PaddedAtomicLong extends PaddedAtomicLongL2Pad {

    private static final long VALUE_OFFSET = fieldOffset(PaddedAtomicLongL1Field.class, "value");

    /**
     * Creates a new PaddedAtomicLong with initial value {@code 0}.
     */
    public PaddedAtomicLong() {
    }

    /**
     * Creates a new PaddedAtomicLong with the given initial value.
     *
     * @param initialValue the initial value
     */
    public PaddedAtomicLong(long initialValue) {
        value = initialValue;
    }

    /**
     * Gets the current value.
     *
     * @return the current value
     * @see java.util.concurrent.atomic.AtomicLong#get()
     */
    public long get() {
        return value;
    }

    /**
     * Sets to the given value.
     *
     * @param newValue the new value
     * @see java.util.concurrent.atomic.AtomicLong#set(long)
     */
    public void set(long newValue) {
        value = newValue;
    }

    /**
     * Eventually sets to the given value.
     *
     * @param newValue the new value
     * @see java.util.concurrent.atomic.AtomicLong#lazySet(long)
     */
    public void lazySet(long newValue) {
        UNSAFE.putOrderedLong(this, VALUE_OFFSET, newValue);
    }

    /**
     * Atomically sets to the given value and returns the old value.
     *
     * @param newValue the new value
     * @return the previous value
     * @see java.util.concurrent.atomic.AtomicLong#getAndSet(long)
     */
    public long getAndSet(long newValue) {
        return getAndSet0(newValue);
    }

    /**
     * Atomically sets the value to the given updated value
     * if the current value {@code ==} the expected value.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that
     * the actual value was not equal to the expected value.
     * @see java.util.concurrent.atomic.AtomicLong#compareAndSet(long, long)
     */
    public boolean compareAndSet(long expect, long update) {
        return UNSAFE.compareAndSwapLong(this, VALUE_OFFSET, expect, update);
    }

    /**
     * Atomically sets the value to the given updated value
     * if the current value {@code ==} the expected value.
     *
     * <p><a href="package-summary.html#weakCompareAndSet">May fail
     * spuriously and does not provide ordering guarantees</a>, so is
     * only rarely an appropriate alternative to {@code compareAndSet}.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful
     * @see java.util.concurrent.atomic.AtomicLong#weakCompareAndSet(long, long)
     */
    public boolean weakCompareAndSet(long expect, long update) {
        return UNSAFE.compareAndSwapLong(this, VALUE_OFFSET, expect, update);
    }

    /**
     * Atomically increments the current value by 1.
     *
     * @return the previous value
     * @see java.util.concurrent.atomic.AtomicLong#getAndIncrement()
     */
    public long getAndIncrement() {
        return getAndAdd0(1L);
    }

    /**
     * Atomically decrements the current value by 1.
     *
     * @return the previous value
     * @see java.util.concurrent.atomic.AtomicLong#getAndDecrement()
     */
    public long getAndDecrement() {
        return getAndAdd0(-1L);
    }

    /**
     * Atomically adds to the current value the given value.
     *
     * @param delta the value to add
     * @return the previous value
     * @see java.util.concurrent.atomic.AtomicLong#getAndAdd(long)
     */
    public long getAndAdd(long delta) {
        return getAndAdd0(delta);
    }

    /**
     * Atomically increments the current value by one.
     *
     * @return the updated value
     * @see java.util.concurrent.atomic.AtomicLong#incrementAndGet()
     */
    public long incrementAndGet() {
        return getAndAdd0(1L) + 1L;
    }

    /**
     * Atomically decrements the current value by one.
     *
     * @return the updated value
     * @see java.util.concurrent.atomic.AtomicLong#decrementAndGet()
     */
    public long decrementAndGet() {
        return getAndAdd0(-1L) - 1L;
    }

    /**
     * Atomically adds to current value te given value.
     *
     * @param delta the value to add
     * @return the updated value
     * @see java.util.concurrent.atomic.AtomicLong#addAndGet(long)
     */
    public long addAndGet(long delta) {
        return getAndAdd0( delta) + delta;
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function, returning the previous value. The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.
     *
     * @param updateFunction a side-effect-free function
     * @return the previous value
     * @see java.util.concurrent.atomic.AtomicLong#getAndUpdate(LongUnaryOperator)
    */
    public long getAndUpdate(LongUnaryOperator updateFunction) {
        long prev, next;
        do {
            prev = get();
            next = updateFunction.applyAsLong(prev);
        } while (!compareAndSet(prev, next));
        return prev;
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function, returning the updated value. The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.
     *
     * @param updateFunction a side-effect-free function
     * @return the updated value
     * @see java.util.concurrent.atomic.AtomicLong#updateAndGet(LongUnaryOperator)
     */
    public long updateAndGet(LongUnaryOperator updateFunction) {
        long prev, next;
        do {
            prev = get();
            next = updateFunction.applyAsLong(prev);
        } while (!compareAndSet(prev, next));
        return next;
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function to the current and given values,
     * returning the previous value. The function should be
     * side-effect-free, since it may be re-applied when attempted
     * updates fail due to contention among threads.  The function
     * is applied with the current value as its first argument,
     * and the given update as the second argument.
     *
     * @param v the update value
     * @param f a side-effect-free function of two arguments
     * @return the previous value
     * @see java.util.concurrent.atomic.AtomicLong#getAndAccumulate(long, LongBinaryOperator)
     */
    public long getAndAccumulate(long v, LongBinaryOperator f) {
        long prev, next;
        do {
            prev = get();
            next = f.applyAsLong(prev, v);
        } while (!compareAndSet(prev, next));
        return prev;
    }


    /**
     * {@link java.util.concurrent.atomic.AtomicLong#accumulateAndGet(long, LongBinaryOperator)}
     */
    public long accumulateAndGet(long x, LongBinaryOperator f) {
        long prev, next;
        do {
            prev = get();
            next = f.applyAsLong(prev, x);
        } while (!compareAndSet(prev, next));
        return next;
    }

    /**
     * Returns the String representation of the current value.
     *
     * @return the String representation of the current value
     */
    @Override
    public String toString() {
        return Long.toString(get());
    }

    /**
     * Returns the value as an {@code int}.
     *
     * @see java.util.concurrent.atomic.AtomicLong#intValue()
     */
    @Override
    public int intValue() {
        return (int) get();
    }

    /**
     * Returns the value as a {@code long}.
     *
     * @see java.util.concurrent.atomic.AtomicLong#longValue()
     */
    @Override
    public long longValue() {
        return get();
    }

    /**
     * Returns the value of a {@code float}.
     *
     * @see java.util.concurrent.atomic.AtomicLong#floatValue()
     */
    @Override
    public float floatValue() {
        return (float) get();
    }

    /**
     * Returns the value of a {@code double}.
     *
     * @see java.util.concurrent.atomic.AtomicLong#doubleValue()
     */
    @Override
    public double doubleValue() {
        return (double) get();
    }


    private long getAndSet0(long newVal)
    {
        if (UnsafeAccess.SUPPORTS_GET_AND_SET)
        {
            return UNSAFE.getAndSetLong(this, VALUE_OFFSET , newVal);
        }
        else
        {
            long oldVal;
            do
            {
                oldVal = value;
            }
            while (!UNSAFE.compareAndSwapLong(this, VALUE_OFFSET, oldVal, newVal));
            return oldVal;
        }
    }

    private long getAndAdd0(long inc)
    {
        if (UnsafeAccess.SUPPORTS_GET_AND_SET)
        {
            return UNSAFE.getAndAddLong(this, VALUE_OFFSET , inc);
        }
        else
        {
            long oldVal;
            do
            {
                oldVal = value;
            }
            while (!UNSAFE.compareAndSwapLong(this, VALUE_OFFSET, oldVal, oldVal + inc));
            return oldVal;
        }
    }
}
