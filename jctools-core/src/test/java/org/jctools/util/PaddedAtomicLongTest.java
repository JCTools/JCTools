package org.jctools.util;

import org.junit.Test;

import java.util.function.LongBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PaddedAtomicLongTest {

    @Test
    public void testDefaultConstructor() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        assertEquals(0L, counter.get());
    }

    @Test
    public void testConstructor_withValue() {
        PaddedAtomicLong counter = new PaddedAtomicLong(20);

        assertEquals(20, counter.get());
    }


    @Test
    public void testSet() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        counter.set(10);

        assertEquals(10L, counter.get());
    }

    @Test
    public void lazySet() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        counter.lazySet(10);

        assertEquals(10L, counter.get());
    }

    @Test
    public void testGetAndSet() {
        PaddedAtomicLong counter = new PaddedAtomicLong(1);

        long result = counter.getAndSet(2);

        assertEquals(1, result);
        assertEquals(2, counter.get());
    }

    @Test
    public void testCompareAndSet_whenSuccess() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        assertTrue(counter.compareAndSet(0, 1));
        assertEquals(1, counter.get());
    }

    @Test
    public void testCompareAndSet_whenFailure() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        assertFalse(counter.compareAndSet(1, 2));
        assertEquals(0, counter.get());
    }

    @Test
    public void testWeakCompareAndSet_whenSuccess() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        assertTrue(counter.weakCompareAndSet(0, 1));
        assertEquals(1, counter.get());
    }

    @Test
    public void testWeakCompareAndSet_whenFailure() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        assertFalse(counter.weakCompareAndSet(1, 2));
        assertEquals(0, counter.get());
    }

    @Test
    public void testGetAndIncrement() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        long value = counter.getAndIncrement();
        assertEquals(0L, value);
        assertEquals(1, counter.get());
    }

    @Test
    public void testGetAndDecrement() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        long value = counter.getAndDecrement();
        assertEquals(0L, value);
        assertEquals(-1, counter.get());
    }

    @Test
    public void testGetAndAdd() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        long value = counter.getAndAdd(10);
        assertEquals(0L, value);
        assertEquals(10, counter.get());
    }

    @Test
    public void testIncrementAndGet() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        long value = counter.incrementAndGet();
        assertEquals(1L, value);
        assertEquals(1L, counter.get());
    }

    @Test
    public void testDecrementAndGet() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        long value = counter.decrementAndGet();
        assertEquals(-1, value);
        assertEquals(-1, counter.get());
    }

    @Test
    public void testAddAndGet() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        long value = counter.addAndGet(1);
        assertEquals(1, value);
        assertEquals(1, counter.get());
    }

    @Test
    public void testGetAndUpdate() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        long value = counter.getAndUpdate(operand -> operand + 2);
        assertEquals(0, value);
        assertEquals(2, counter.get());
    }

    @Test
    public void testUpdateAndGet() {
        PaddedAtomicLong counter = new PaddedAtomicLong();

        long value = counter.updateAndGet(operand -> operand + 2);
        assertEquals(2, value);
        assertEquals(2, counter.get());
    }

    @Test
    public void testGetAndAccumulate() {
        PaddedAtomicLong counter = new PaddedAtomicLong(10);
        
        long value = counter.getAndAccumulate(1, (left, right) -> left+right);

        assertEquals(value, 10);
        assertEquals(11, counter.get());
    }

    @Test
    public void testAccumulateAndGet() {
        PaddedAtomicLong counter = new PaddedAtomicLong(10);

        long value = counter.accumulateAndGet(1, (left, right) -> left+right);

        assertEquals(value, 11);
        assertEquals(11, counter.get());
    }

    @Test
    public void testIntValue() {
        PaddedAtomicLong counter = new PaddedAtomicLong(10);

        assertEquals(10, counter.intValue());
    }

    @Test
    public void testLongValue() {
        PaddedAtomicLong counter = new PaddedAtomicLong(10);

        assertEquals(10, counter.longValue());
    }

    @Test
    public void testFloatValue() {
        PaddedAtomicLong counter = new PaddedAtomicLong(10);

        assertEquals(10f, counter.floatValue(), 0.01);
    }

    @Test
    public void testDoubleValue() {
        PaddedAtomicLong counter = new PaddedAtomicLong(10);

        assertEquals(10d, counter.doubleValue(), 0.01);
    }

    @Test
    public void testToString() {
        PaddedAtomicLong counter = new PaddedAtomicLong(10);

        assertEquals("10", counter.toString());
    }
}
