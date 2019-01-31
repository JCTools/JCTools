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

import org.jctools.util.UnsafeAccess;
import org.jctools.util.UnsafeRefArrayAccess;

import java.util.Queue;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.lvElement;
import static org.jctools.util.UnsafeRefArrayAccess.soElement;

abstract class FFBufferL1Pad<E> extends ConcurrentCircularArrayQueue<E>
{
    long p10, p11, p12, p13, p14, p15, p16;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    FFBufferL1Pad(int capacity)
    {
        super(capacity);
    }
}

abstract class FFBufferProducerField<E> extends FFBufferL1Pad<E>
{
    protected long pIndex;

    FFBufferProducerField(int capacity)
    {
        super(capacity);
    }
}

abstract class FFBufferL2Pad<E> extends FFBufferProducerField<E>
{
    long p20, p21, p22, p23, p24, p25, p26;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    FFBufferL2Pad(int capacity)
    {
        super(capacity);
    }
}

abstract class FFBufferConsumerField<E> extends FFBufferL2Pad<E>
{
    protected long cIndex;

    FFBufferConsumerField(int capacity)
    {
        super(capacity);
    }
}

abstract class FFBufferL3Pad<E> extends FFBufferConsumerField<E>
{
    long p40, p41, p42, p43, p44, p45, p46;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    FFBufferL3Pad(int capacity)
    {
        super(capacity);
    }
}

public final class FFBuffer<E> extends FFBufferL3Pad<E> implements Queue<E>
{
    private final static long P_INDEX_OFFSET = fieldOffset(FFBufferProducerField.class, "pIndex");
    private final static long C_INDEX_OFFSET = fieldOffset(FFBufferConsumerField.class, "cIndex");

    public FFBuffer(final int capacity)
    {
        super(capacity);
    }

    @Override
    public final long lvConsumerIndex()
    {
        return UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
    }

    @Override
    public final long lvProducerIndex()
    {
        return UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
    }

    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException("Null is not a valid element");
        }

        final E[] lb = buffer;
        final long t = pIndex;
        final long offset = calcElementOffset(t);
        if (null != lvElement(lb, offset))
        { // read acquire
            return false;
        }
        soElement(lb, offset, e); // write release
        pIndex = t + 1;
        return true;
    }

    @Override
    public E poll()
    {
        long cIndex = this.cIndex;
        final long offset = calcElementOffset(cIndex);
        final E[] lb = buffer;
        final E e = lvElement(lb, offset); // write acquire
        if (null == e)
        {
            return null;
        }
        soElement(lb, offset, null); // read release
        this.cIndex = cIndex + 1;
        return e;
    }

    @Override
    public E peek()
    {
        long currentHead = lvProducerIndex();
        return lvElement(buffer, calcElementOffset(currentHead));
    }

    @Override
    public boolean relaxedOffer(E message)
    {
        return offer(message);
    }

    @Override
    public E relaxedPoll()
    {
        return poll();
    }

    @Override
    public E relaxedPeek()
    {
        return peek();
    }

    @Override
    public int drain(Consumer<E> c)
    {
        final int limit = capacity();
        return drain(c, limit);
    }

    @Override
    public int fill(Supplier<E> s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int drain(Consumer<E> c, int limit)
    {
        for (int i = 0; i < limit; i++)
        {
            E e = relaxedPoll();
            if (e == null)
            {
                return i;
            }
            c.accept(e);
        }
        return limit;
    }

    @Override
    public int fill(Supplier<E> s, int limit)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void drain(
        Consumer<E> c,
        WaitStrategy wait,
        ExitCondition exit)
    {
        int idleCounter = 0;
        while (exit.keepRunning())
        {
            E e = relaxedPoll();
            if (e == null)
            {
                idleCounter = wait.idle(idleCounter);
                continue;
            }
            idleCounter = 0;
            c.accept(e);
        }
    }

    @Override
    public void fill(
        Supplier<E> s,
        WaitStrategy wait,
        ExitCondition exit)
    {
        throw new UnsupportedOperationException();
    }
}
