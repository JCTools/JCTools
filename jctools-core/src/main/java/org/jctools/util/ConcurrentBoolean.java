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

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A concurrent boolean represents a condition of state which can be accessed by multiple threads.
 */
public class ConcurrentBoolean
{
    private boolean value;

    // Packed counters: 8 bits each for false, true, changed, set
    // Bits 0-7: becameFalse, 8-15: becameTrue, 16-23: changed, 24-31: set
    private int packedCounters = 0;

    private static final int FALSE_SHIFT = 0;
    private static final int TRUE_SHIFT = 8;
    private static final int CHANGED_SHIFT = 16;
    private static final int SET_SHIFT = 24;
    private static final int COUNTER_MAX = 0xFF;

    private final Lock lock;
    private final Condition isFalse;
    private final Condition isTrue;
    private final Condition isChanged;
    private final Condition isSet;

    /**
     * Private constructor only for serialization.
     */
    private ConcurrentBoolean()
    {
        this.value = false;
        this.lock = new ReentrantLock();
        this.isFalse = lock.newCondition();
        this.isTrue = lock.newCondition();
        this.isChanged = lock.newCondition();
        this.isSet = lock.newCondition();
    }

    /**
     * Construct an object with an initial boolean value.
     *
     * @param value the initial boolean value
     */
    public ConcurrentBoolean(boolean value)
    {
        this();
        this.value = value;
    }
    
    /**
     * Atomically set this to a new value and return the previous value.
     */
    public boolean setAndGet(boolean newValue)
    {
        try
        {
            this.lock.lock();
            boolean result = this.value;
            this.value = newValue;

            // release signals to waiting threads
            if (false == this.value)
            {
                incrementCounter(FALSE_SHIFT);
                isFalse.signalAll();
            }
            if (true == this.value)
            {
                incrementCounter(TRUE_SHIFT);
                isTrue.signalAll();
            }
            if (result != this.value)
            {
                incrementCounter(CHANGED_SHIFT);
                isChanged.signalAll();
            }
            incrementCounter(SET_SHIFT);
            isSet.signalAll();

            return result;
        }
        finally
        {
            this.lock.unlock();
        }
    }

    /**
     * Increment a packed counter, wrapping to 0 if it exceeds 255.
     * Must be called while holding the lock.
     */
    private void incrementCounter(int shift)
    {
        int mask = COUNTER_MAX << shift;
        int current = (packedCounters & mask) >>> shift;
        if (current >= COUNTER_MAX)
        {
            packedCounters = (packedCounters & ~mask);
        }
        else
        {
            packedCounters = (packedCounters & ~mask) | ((current + 1) << shift);
        }
    }

    /**
     * Get the value of a packed counter.
     * Must be called while holding the lock.
     */
    private int getCounter(int shift)
    {
        int mask = COUNTER_MAX << shift;
        return (packedCounters & mask) >>> shift;
    }

    /**
     * Syntactic sugar for setAndGet().
     *
     * @param newValue the new boolean value
     */
    public void set(boolean newValue)
    {
        setAndGet(newValue);
    }

    /**
     * Blocks the calling thread until the value is false. If the value is currently false this method
     * returns immediately. If the value becomes false while waiting, this method returns even if the
     * value subsequently changes back to true before the thread wakes up.
     *
     * @throws InterruptedException
     */
    public void awaitFalse() throws InterruptedException
    {
        try
        {
            this.lock.lock();
            int startCounter = getCounter(FALSE_SHIFT);
            while (getCounter(FALSE_SHIFT) == startCounter) // avoid spurious wakes
            {
                isFalse.await();
            }
        }
        finally
        {
            this.lock.unlock();
        }
    }

    /**
     * Blocks the calling thread until the value is true. If the value is currently true this method
     * returns immediately. If the value becomes true while waiting, this method returns even if the
     * value subsequently changes back to false before the thread wakes up.
     *
     * @throws InterruptedException
     */
    public void awaitTrue() throws InterruptedException
    {
        try
        {
            this.lock.lock();
            int startCounter = getCounter(TRUE_SHIFT);
            while (getCounter(TRUE_SHIFT) == startCounter) // avoid spurious wakes
            {
                isTrue.await();
            }
        }
        finally
        {
            this.lock.unlock();
        }
    }

    /**
     * Blocks the calling thread until the value changes, and returns the new value. This method does not return
     * if the value is set but does not change.
     *
     * @throws InterruptedException
     */
    public boolean awaitChange() throws InterruptedException
    {
        try
        {
            this.lock.lock();
            int startCounter = getCounter(CHANGED_SHIFT);
            while (getCounter(CHANGED_SHIFT) == startCounter) // avoid spurious wakes
            {
                isChanged.await();
            }
            return this.value;
        }
        finally
        {
            this.lock.unlock();
        }
    }

    /**
     * Blocks the calling thread until the value is set, and returns the new value. Any time the value is set, this method
     * returns, even if it is set to the same value. If the value is false, and it is set to false, this method returns.
     *
     * @throws InterruptedException
     */
    public boolean awaitSet() throws InterruptedException
    {
        try
        {
            this.lock.lock();
            int startCounter = getCounter(SET_SHIFT);
            while (getCounter(SET_SHIFT) == startCounter) // avoid spurious wakes
            {
                isSet.await();
            }
            return this.value;
        }
        finally
        {
            this.lock.unlock();
        }
    }
}
