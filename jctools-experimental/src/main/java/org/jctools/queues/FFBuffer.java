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

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.*;

abstract class FFBufferL1Pad<E> extends ConcurrentCircularArrayQueue<E>
{
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
//    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

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
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
//    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

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
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
//    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

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
        final long offset = calcCircularRefElementOffset(t, mask);
        if (null != lvRefElement(lb, offset))
        { // read acquire
            return false;
        }
        soRefElement(lb, offset, e); // write release
        pIndex = t + 1;
        return true;
    }

    @Override
    public E poll()
    {
        long cIndex = this.cIndex;
        final long offset = calcCircularRefElementOffset(cIndex, mask);
        final E[] lb = buffer;
        final E e = lvRefElement(lb, offset); // write acquire
        if (null == e)
        {
            return null;
        }
        soRefElement(lb, offset, null); // read release
        this.cIndex = cIndex + 1;
        return e;
    }

    @Override
    public E peek()
    {
        long currentHead = lvProducerIndex();
        return lvRefElement(buffer, calcCircularRefElementOffset(currentHead, mask));
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
