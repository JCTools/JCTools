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

import java.util.Iterator;
import java.util.NoSuchElementException;

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
        assertNull(stack.pop());
        stack.push(new IntNode(1));
        stack.push(new IntNode(2));
        assertEquals(stack.pop(), new IntNode(2));
        assertEquals(stack.pop(), new IntNode(1));
        assertNull(stack.pop());
        stack.push(new IntNode(1));
        assertEquals(stack.pop(), new IntNode(1));
        assertNull(stack.pop());
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
}
