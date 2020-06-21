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

import java.util.Iterator;

/**
 * A concurrent, unbounded, and non-blocking stack based on linked nodes.
 * Concurrent push and pop executes safely across multiple threads.
 * <p>
 * Unlike {@code java.util.concurrent.ConcurrentLinkedDeque}, this class only supports
 * the stack APIs, and does not support internal-remove.
 * On the other hand, this class has a constant time {@link #size()} method,
 * and a wait-free {@link #push(Object)} method.
 * <p>
 * The {@link #iterator()} operates on a safely captured snapshot of the stack,
 * and is <em>weakly consistent</em>. This follows the precedent of the
 * {@code java.util.concurrent} package, as defined in its package summary.
 * <p>
 * This implementation is based on the {@link WFIStack}.
 */
public class ConcurrentLinkedStack<T> implements Iterable<T>
{
    private final WFIStack<LinkedNode<T>> stack;

    /**
     * Create a new empty stack.
     */
    public ConcurrentLinkedStack()
    {
        stack = new WFIStack<LinkedNode<T>>();
    }

    /**
     * Push the given object onto the stack.
     *
     * @param value The object to push onto the stack.
     */
    public void push(T value)
    {
        stack.push(new LinkedNode<T>(value));
    }

    /**
     * Pop the top-most object from the stack.
     *
     * @return The object that was the head of the stack, or {@code null} if the stack was empty.
     */
    public T pop()
    {
        LinkedNode<T> node = stack.pop();
        return node == null ? null : node.value;
    }

    /**
     * Peek at the top of the stack, without removing said object.
     * <p>
     * Note that the result may be outdated as soon as this method returns.
     *
     * @return The object that is the top of the stack, or {@code null} if the stack is empty.
     */
    public T peek()
    {
        LinkedNode<T> node = stack.peek();
        return node == null ? null : node.value;
    }

    /**
     * Get the size of the stack.
     *
     * @return The number of objects on this stack.
     */
    public int size()
    {
        return stack.size();
    }

    /**
     * Tell whether the stack is empty.
     *
     * @return {@code true} if the stack is empty.
     */
    public boolean isEmpty()
    {
        return stack.isEmpty();
    }

    @Override
    public Iterator<T> iterator()
    {
        return new Iterator<T>()
        {
            private final Iterator<LinkedNode<T>> inner = stack.iterator();

            @Override
            public boolean hasNext()
            {
                return inner.hasNext();
            }

            @Override
            public T next()
            {
                LinkedNode<T> next = inner.next();
                return next.value;
            }
        };
    }

    private static class LinkedNode<T> extends WFIStack.Node
    {
        private final T value;

        private LinkedNode(T value)
        {
            this.value = value;
        }
    }
}
