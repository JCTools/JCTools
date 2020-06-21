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
package org.jctools.stacks;

import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jctools.util.TestUtil.TEST_TIMEOUT;
import static org.junit.Assert.*;

public class WFIStackTest
{
    private static class IntNode extends WFIStack.Node
    {
        final int value;

        private IntNode(int value)
        {
            this.value = value;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            IntNode intNode = (IntNode) o;
            return value == intNode.value;
        }

        @Override
        public int hashCode()
        {
            return value;
        }

        @Override
        public String toString()
        {
            return "IntNode{" +
                "value=" + value +
                '}';
        }
    }

    private WFIStack<IntNode> stack;

    @Before
    public void setUp()
    {
        stack = new WFIStack<>();
    }

    @Test
    public void pushAndPop()
    {
        assertTrue(stack.isEmpty());
        assertEquals(stack.size(), 0);
        assertNull(stack.pop());
        assertTrue(stack.isEmpty());
        assertEquals(stack.size(), 0);
        stack.push(new IntNode(1));
        assertFalse(stack.isEmpty());
        assertEquals(stack.size(), 1);
        stack.push(new IntNode(2));
        assertEquals(stack.pop(), new IntNode(2));
        assertEquals(stack.pop(), new IntNode(1));
        assertNull(stack.pop());
        stack.push(new IntNode(1));
        assertEquals(stack.pop(), new IntNode(1));
        assertNull(stack.pop());
        assertTrue(stack.isEmpty());
        assertEquals(stack.size(), 0);
    }

    @Test
    public void peekAndPush()
    {
        assertNull(stack.peekAndPush(new IntNode(1)));
        assertEquals(stack.peekAndPush(new IntNode(2)), new IntNode(1));
        assertEquals(stack.peekAndPush(new IntNode(3)), new IntNode(2));
        assertEquals(stack.size(), 3);
        assertEquals(stack.pop(), new IntNode(3));
        assertEquals(stack.pop(), new IntNode(2));
        assertEquals(stack.pop(), new IntNode(1));
        assertNull(stack.pop());
    }

    @Test
    public void pushAndPopAll()
    {
        assertEquals(stack.size(), 0);
        stack.push(new IntNode(1));
        assertEquals(stack.size(), 1);
        stack.push(new IntNode(2));
        assertEquals(stack.size(), 2);
        stack.push(new IntNode(3));
        assertEquals(stack.size(), 3);

        Iterable<IntNode> all = stack.popAll();
        assertEquals(stack.size(), 0);
        Iterator<IntNode> iterator = all.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(3));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(2));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(1));
        assertFalse(iterator.hasNext());

        // Iterable must be reentrant.
        iterator = all.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(3));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(2));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(1));
        assertFalse(iterator.hasNext());

        try
        {
            iterator.next();
            fail("Expected NoSuchElementException");
        }
        catch (NoSuchElementException ignore)
        {
            // Good.
        }
    }

    @Test
    public void pushAndPopAllFifo()
    {
        assertEquals(stack.size(), 0);
        stack.push(new IntNode(1));
        assertEquals(stack.size(), 1);
        stack.push(new IntNode(2));
        assertEquals(stack.size(), 2);
        stack.push(new IntNode(3));
        assertEquals(stack.size(), 3);

        Iterable<IntNode> all = stack.popAllFifo();
        assertEquals(stack.size(), 0);
        Iterator<IntNode> iterator = all.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(1));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(2));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(3));
        assertFalse(iterator.hasNext());

        // Iterable must be reentrant.
        iterator = all.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(1));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(2));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(3));
        assertFalse(iterator.hasNext());

        try
        {
            iterator.next();
            fail("Expected NoSuchElementException");
        }
        catch (NoSuchElementException ignore)
        {
            // Good.
        }
    }

    @Test
    public void pushAndReplaceAll()
    {
        stack.push(new IntNode(1));
        stack.push(new IntNode(2));
        stack.push(new IntNode(3));

        assertEquals(stack.size(), 3);
        Iterable<IntNode> all = stack.replaceAll(new IntNode(4));
        assertEquals(stack.size(), 1);
        Iterator<IntNode> iterator = all.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(3));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(2));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(1));
        assertFalse(iterator.hasNext());

        // Iterable must be reentrant.
        iterator = all.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(3));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(2));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(1));
        assertFalse(iterator.hasNext());

        try
        {
            iterator.next();
            fail("Expected NoSuchElementException");
        }
        catch (NoSuchElementException ignore)
        {
            // Good.
        }

        assertEquals(stack.pop(), new IntNode(4));
        assertNull(stack.pop());
    }

    @Test
    public void pushAndReplaceAllFifo()
    {
        stack.push(new IntNode(1));
        stack.push(new IntNode(2));
        stack.push(new IntNode(3));

        assertEquals(stack.size(), 3);
        Iterable<IntNode> all = stack.replaceAllFifo(new IntNode(4));
        assertEquals(stack.size(), 1);
        Iterator<IntNode> iterator = all.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(1));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(2));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(3));
        assertFalse(iterator.hasNext());

        // Iterable must be reentrant.
        iterator = all.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(1));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(2));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(3));
        assertFalse(iterator.hasNext());

        try
        {
            iterator.next();
            fail("Expected NoSuchElementException");
        }
        catch (NoSuchElementException ignore)
        {
            // Good.
        }

        assertEquals(stack.pop(), new IntNode(4));
        assertNull(stack.pop());
    }

    @Test
    public void recyclingNodesOnPushMustThrow()
    {
        assertEquals(stack.size(), 0);
        stack.push(new IntNode(1));
        assertEquals(stack.size(), 1);
        IntNode node = stack.pop();
        assertEquals(stack.size(), 0);
        boolean gotError = false;
        try
        {
            stack.push(node);
        }
        catch (AssertionError ignore)
        {
            // Good.
            gotError = true;
        }
        assertTrue(gotError);
    }

    @Test
    public void recyclingNodesOnReplaceAllMustThrow()
    {
        assertEquals(stack.size(), 0);
        stack.push(new IntNode(1));
        assertEquals(stack.size(), 1);
        IntNode node = stack.pop();
        assertEquals(stack.size(), 0);
        boolean gotError = false;
        try
        {
            stack.replaceAll(node);
        }
        catch (AssertionError ignore)
        {
            // Good.
            gotError = true;
        }
        assertTrue(gotError);
    }

    @Test
    public void recyclingNodesOnReplaceAllFifoMustThrow()
    {
        assertEquals(stack.size(), 0);
        stack.push(new IntNode(1));
        assertEquals(stack.size(), 1);
        IntNode node = stack.pop();
        assertEquals(stack.size(), 0);
        boolean gotError = false;
        try
        {
            stack.replaceAllFifo(node);
        }
        catch (AssertionError ignore)
        {
            // Good.
            gotError = true;
        }
        assertTrue(gotError);
    }

    @Test
    public void peek() {
        assertNull(stack.peek());
        stack.push(new IntNode(1));
        assertEquals(stack.peek(), new IntNode(1));
        assertEquals(stack.peek(), new IntNode(1));
        stack.push(new IntNode(2));
        assertEquals(stack.peek(), new IntNode(2));
        assertEquals(stack.peek(), new IntNode(2));
        stack.replaceAll(new IntNode(3));
        assertEquals(stack.peek(), new IntNode(3));
        assertEquals(stack.peek(), new IntNode(3));
        stack.replaceAllFifo(new IntNode(4));
        assertEquals(stack.peek(), new IntNode(4));
        assertEquals(stack.peek(), new IntNode(4));
        stack.popAll();
        assertNull(stack.peek());
    }

    @Test
    public void iteration()
    {
        Iterator<IntNode> iterator = stack.iterator();
        assertEmpty(iterator);

        stack.push(new IntNode(1));
        iterator = stack.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(1));
        assertEmpty(iterator);

        iterator = stack.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(1));
        assertEmpty(iterator);

        stack.push(new IntNode(2));

        iterator = stack.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(2));
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), new IntNode(1));
        assertEmpty(iterator);
    }

    private void assertEmpty(Iterator<IntNode> iterator)
    {
        assertFalse(iterator.hasNext());
        try
        {
            iterator.next();
            fail("iterator.next() should throw when empty.");
        }
        catch (NoSuchElementException ignore)
        {
            // Good.
        }
    }

    @Test(timeout = TEST_TIMEOUT)
    public void sanityCheckMultiPushMultiPop() throws Exception
    {
        int pusherCount = Math.max(1, (Runtime.getRuntime().availableProcessors() - 1) / 3);
        int popperCount = Math.max(1, (Runtime.getRuntime().availableProcessors() - 1) - pusherCount);
        AtomicInteger doneCounter = new AtomicInteger();

        class Pusher implements Runnable
        {
            final int from;
            final int to;

            Pusher(int from, int to)
            {
                this.from = from;
                this.to = to;
            }

            @Override
            public void run()
            {
                for (int i = from; i < to; i++)
                {
                    stack.push(new IntNode(i));
                }
                doneCounter.getAndIncrement();
            }
        }

        class Popper implements Runnable
        {
            final Set<Integer> ints;

            Popper()
            {
                ints = new HashSet<>();
            }

            @Override
            public void run()
            {
                while (doneCounter.get() < pusherCount)
                {
                    IntNode in;
                    while ((in = stack.pop()) != null)
                    {
                        ints.add(in.value);
                    }
                }
            }
        }

        int max = 0;
        int valuesPerThread = 1000000;
        List<Popper> poppers = new ArrayList<>();
        List<Thread> ts = new ArrayList<>();
        for (int i = 0; i < pusherCount; i++)
        {
            int from = i * valuesPerThread;
            int to = from + valuesPerThread;
            max = Math.max(max, to - 1); // Account for truncation in integer division. And 'to' is not inclusive.
            ts.add(new Thread(new Pusher(from, to), "Pusher-" + i));
        }
        for (int i = 0; i < popperCount; i++)
        {
            Popper popper = new Popper();
            poppers.add(popper);
            ts.add(new Thread(popper, "Popper-" + i));
        }
        for (Thread t : ts)
        {
            t.start();
        }
        for (Thread t : ts)
        {
            t.join();
        }

        // Make sure stack really is empty.
        IntNode in;
        while ((in = stack.pop()) != null)
        {
            poppers.get(0).ints.add(in.value);
        }

        // Collect all values.
        int valueCount = 0;
        for (Popper popper : poppers)
        {
            valueCount += popper.ints.size();
        }
        int[] values = new int[valueCount];
        int index = 0;
        for (Popper popper : poppers)
        {
            for (Integer x : popper.ints)
            {
                values[index] = x;
                index++;
            }
        }

        Arrays.sort(values);
        assertEquals(max, values[values.length - 1]);
        assertEquals(values.length, valuesPerThread * pusherCount);
        for (int i = 1; i < values.length; i++)
        {
            assertEquals(values[i - 1] + 1, values[i]);
        }
    }
}
