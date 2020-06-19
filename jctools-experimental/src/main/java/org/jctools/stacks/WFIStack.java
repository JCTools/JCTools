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

import org.jctools.stacks.WFIStack.Node;
import org.jctools.util.UnsafeAccess;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A Wait-Free Intrusive Stack (Last-In, First-Out).
 * <p>
 * This stack has wait-free push, and wait-free bulk pop and bulk replace operations.
 * The single-item pop operation is non-blocking but not wait-free.
 * <p>
 * The stack supports multiple concurrent consumers and multiple concurrent producers.
 * <p>
 * The stack is intrusive, and implementors must extend the {@link Node} class in order to use the stack.
 * These node objects cannot be reused, and must be allocated fresh for every push.
 * <p>
 * The implementation draws inspiration from Dmitry Vyukov's
 * <a href="http://www.1024cores.net/home/lock-free-algorithms/queues/intrusive-mpsc-node-based-queue">
 * Intrusive MPSC node-based queue</a>, but is oriented as a stack instead of a queue, and supports MPMC.
 *
 * @param <T> The type of node in this stack.
 */
@SuppressWarnings({"unchecked", "NullableProblems"})
public class WFIStack<T extends WFIStack.Node> extends PadAfter implements Iterable<T>
{
    /**
     * The super-class of all nodes, or entries, for {@link WFIStack wait-free intrusive stacks}.
     */
    public static abstract class Node
    {
        volatile Node next;
        int count;

        final Node next()
        {
            Node n = next;
            //noinspection IdempotentLoopBody
            while (n == null)
            {
                // TODO: Thread.onSpinWait
                n = next;
            }
            return n;
        }

        <X extends Node> X self()
        {
            return (X) this;
        }
    }

    private static class NodeIterable<E extends Node> implements Iterable<E>
    {
        private Node start;

        private NodeIterable(Node start)
        {
            this.start = start;
        }

        @Override
        public Iterator<E> iterator()
        {
            return new NodeIterator<E>(start);
        }

        public NodeIterable<E> reverse()
        {
            Node curr = END;
            Node next = start;
            while (next != END)
            {
                Node n = next;
                next = next.next();
                n.next = curr;
                curr = n;
            }
            start = curr;
            return this;
        }
    }

    private static class NodeIterator<E extends Node> implements Iterator<E>
    {
        Node next;

        public NodeIterator(Node start)
        {
            next = start.self();
        }

        @Override
        public boolean hasNext()
        {
            return next != null;
        }

        @Override
        public E next()
        {
            Node n = next;
            if (n == null)
            {
                throw new NoSuchElementException();
            }
            next = n.next().self();
            return (E) n;
        }
    }

    private static final Node END = new Node()
    {
        {
            next = this; // Next from END is END itself.
        }

        @Override
        <X extends Node> X self()
        {
            return null;
        }
    };

    /**
     * Create an empty wait-free intrusive stack.
     */
    public WFIStack()
    {
        if (!UnsafeAccess.SUPPORTS_GET_AND_SET_REF)
        {
            throw new IllegalStateException(
                "Unsafe::getAndSetObject support (JDK 8+) is required for this stack to work.");
        }
        head = END;
    }

    /**
     * Get the size of the stack.
     * This is the number of elements currently pushed onto the stack.
     * Note that this method is inherently racy, and the number can be outdated as soon as it is returned.
     *
     * @return The number of elements in the stack.
     */
    public int size()
    {
        Node h = head; // Take snapshot of stack.
        h.next(); // Ensure 'count' has been updated, by ensuring 'next' has been updated.
        return h.count;
    }

    /**
     * Peek at the head of the stack.
     * This returns the most recently added element, if any.
     * Note that this method is inherently racy, and the value can be outdated as soon as it is returned.
     *
     * @return The most recently added element, or {@code null}.
     */
    public T peek()
    {
        return head.self();
    }

    /**
     * Push the given node onto the head of the stack.
     * <p>
     * This operation is wait-free and has constant time-complexity.
     *
     * @param node The node to push onto the stack.
     */
    public void push(T node)
    {
        checkPush(node);
        T n = xchgHead(node);
        node.count = n.count + 1; // Update 'count' before volatile store to 'next'.
        node.next = n;
    }

    /**
     * Atomically peek the head of the stack, and push the given node onto the head of the stack.
     * The returned node is the head of the stack that was displaced by the given pushed node.
     * The returned node is not removed from the stack, so the size of the stack is increased by one.
     * <p>
     * This operation is wait-free and has constant time-complexity.
     *
     * @param node The node to push onto the stack.
     * @return The node that was previously the head of the stack, if any, or {@code null} if the stack was empty.
     */
    public T peekAndPush(T node)
    {
        checkPush(node);
        T n = xchgHead(node);
        node.count = n.count + 1; // Update 'count' before volatile store to 'next'.
        node.next = n;
        return n.self();
    }

    /**
     * Remove all nodes from the stack, and return an iterable of the removed nodes.
     * The returned iterable is a complete and atomic snapshot of the stack contents at the time of the method call.
     * Racing callers will not see any overlap between their returned iterables.
     * The iterable is re-entrant, so {@link Iterable#iterator()} can be called on it multiple times, and it will
     * always produce the same result.
     * <p>
     * This operation is wait-free and has constant time-complexity.
     *
     * @return An iterable of all the nodes in the stack.
     */
    public Iterable<T> popAll()
    {
        return new NodeIterable<T>(xchgHead(END));
    }

    /**
     * Remove all nodes from the stack, and return an iterable of the removed nodes.
     * The returned iterable is a complete and atomic snapshot of the stack contents at the time of the method call.
     * Racing callers will not see any overlap between their returned iterables.
     * The iterable is re-entrant, so {@link Iterable#iterator()} can be called on it multiple times, and it will
     * always produce the same result.
     * <p>
     * This operation is wait-free and has time-complexity linear to the number of elements in the returned iterable.
     *
     * @return An iterable of all the nodes in the stack.
     */
    public Iterable<T> popAllFifo()
    {
        return new NodeIterable<T>(xchgHead(END)).reverse();
    }

    /**
     * Replaces and returns all nodes in the stack with the given node, and return an iterable of the removed nodes.
     * The returned iterable is a complete and atomic snapshot of the stack contents at the time of the method call.
     * Racing callers will not see any overlap between their returned iterables.
     * The iterable is re-entrant, so {@link Iterable#iterator()} can be called on it multiple times, and it will
     * always produce the same result.
     * <p>
     * This operation is wait-free and has constant time-complexity.
     *
     * @return An iterable of all the nodes in the stack, in Last In, First Out order.
     */
    public Iterable<T> replaceAll(T node)
    {
        checkPush(node);
        node.count = 1;
        node.next = END;
        return new NodeIterable<T>(xchgHead(node));
    }


    /**
     * Replaces and returns all nodes in the stack with the given node, and return an iterable of the removed nodes.
     * The returned iterable is a complete and atomic snapshot of the stack contents at the time of the method call.
     * Racing callers will not see any overlap between their returned iterables.
     * The iterable is re-entrant, so {@link Iterable#iterator()} can be called on it multiple times, and it will
     * always produce the same result.
     * <p>
     * This operation is wait-free and has time-complexity linear to the number of elements in the returned iterable.
     *
     * @return An iterable of all the nodes in the stack, in First In, First Out order.
     */
    public Iterable<T> replaceAllFifo(T node)
    {
        checkPush(node);
        node.count = 1;
        node.next = END;
        return new NodeIterable<T>(xchgHead(node)).reverse();
    }

    /**
     * Pop, or remove, the most recently pushed node, or return {@code null} if the stack is empty.
     * <p>
     * This operation is non-blocking, but not wait-free. It has constant time-complexity.
     *
     * @return The most recently pushed node, if any, or {@code null}.
     */
    public T pop()
    {
        Node candidate, successor;
        do
        {
            candidate = head;
            if (candidate == END)
            {
                return null;
            }
            successor = candidate.next();
        }
        while (!casHead(candidate, successor));
        return candidate.self();
    }

    @Override
    public Iterator<T> iterator() {
        return new NodeIterator<T>(head);
    }

    private void checkPush(T node)
    {
        assert node.next == null : "WFIStack.Nodes cannot be reused.";
    }

    private T xchgHead(Node node)
    {
        return (T) UnsafeAccess.UNSAFE.getAndSetObject(this, HEAD_OFFSET, node);
    }

    private boolean casHead(Node expected, Node update)
    {
        return UnsafeAccess.UNSAFE.compareAndSwapObject(this, HEAD_OFFSET, expected, update);
    }
}

@SuppressWarnings("unused")
abstract class PadBefore
{
    // Assuming 12-byte object header and 64-byte cache lines, we force the object header and the 'head' field onto
    // separate cache lines.
    byte p01, p02, p03, p04, p05, p06, p07, p08;
    byte p09, p10, p11, p12, p13, p14, p15, p16;
    byte p17, p18, p19, p20, p21, p22, p23, p24;
    byte p25, p26, p27, p28, p29, p30, p31, p32;
    byte p33, p34, p35, p36, p37, p38, p39, p40;
    byte p41, p42, p43, p44, p45, p46, p47, p48;
    byte p49, p50, p51, p52;
}

abstract class HeadField extends PadBefore
{
    static final long HEAD_OFFSET = UnsafeAccess.fieldOffset(HeadField.class, "head");

    @SuppressWarnings( {"FieldCanBeLocal", "FieldMayBeFinal"})
    volatile Node head; // Accessed via UNSAFE.
}

@SuppressWarnings("unused")
abstract class PadAfter extends HeadField
{
    // Isolate the 'head' field on its own cache line.
    // This brings the object instance size to 128 bytes, assuming 12-byte header
    // and 4-byte object reference size.
    byte p53, p54, p55, p56, p57, p58, p59, p60;
    byte p61, p62, p63, p64, p65, p66, p67, p68;
    byte p69, p70, p71, p72, p73, p74, p75, p76;
    byte p77, p78, p79, p80, p81, p82, p83, p84;
    byte p85, p86, p87, p88, p89, p90, p91, p92;
    byte p93, p94, p95, p96, p97, p98, p99, p100;
    byte p101, p102, p103, p104, p105, p106, p107, p108;
}