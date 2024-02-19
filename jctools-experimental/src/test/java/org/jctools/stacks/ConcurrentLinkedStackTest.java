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

import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.*;

/**
 * @see WFIStackTest most of the correctness of this implementation is derived from the WFIStack.
 */
public class ConcurrentLinkedStackTest
{
    @Test
    public void sanityCheckMethods()
    {
        ConcurrentLinkedStack<Object> stack = new ConcurrentLinkedStack<Object>();
        assertTrue(stack.isEmpty());
        assertEquals(0, stack.size());
        assertFalse(stack.iterator().hasNext());
        assertNull(stack.pop());
        assertNull(stack.peek());

        stack.push(1);

        assertEquals(1, stack.peek());
        assertFalse(stack.isEmpty());
        assertEquals(1, stack.size());
        Iterator<Object> iterator = stack.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(1, iterator.next());
        assertFalse(iterator.hasNext());

        assertEquals(1, stack.pop());

        assertTrue(stack.isEmpty());
        assertEquals(0, stack.size());
        assertFalse(stack.iterator().hasNext());
        assertNull(stack.pop());
        assertNull(stack.peek());

        stack.push(2);
        stack.push(3);

        assertFalse(stack.isEmpty());
        assertEquals(2, stack.size());
        iterator = stack.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(3, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(2, iterator.next());
        assertFalse(iterator.hasNext());
        assertEquals(3, stack.peek());

        assertEquals(3, stack.pop());

        assertEquals(2, stack.peek());

        assertEquals(2, stack.pop());

        assertTrue(stack.isEmpty());
        assertEquals(0, stack.size());
        assertFalse(stack.iterator().hasNext());
        assertNull(stack.pop());
        assertNull(stack.peek());
    }
}