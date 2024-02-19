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

@SuppressWarnings({ "unused", "MultipleTopLevelClassesInFile", "ClassNameDiffersFromFileName" })
abstract class WFIStackL0Pad
{
    // Assuming 12-byte object header and 64-byte cache lines, we force the object header and the 'head' field onto
    // separate cache lines.
    byte b001, b002, b003, b004, b005, b006, b007, b008;
    byte b009, b010, b011, b012, b013, b014, b015, b016;
    byte b017, b018, b019, b020, b021, b022, b023, b024;
    byte b025, b026, b027, b028, b029, b030, b031, b032;
    byte b033, b034, b035, b036, b037, b038, b039, b040;
    byte b041, b042, b043, b044, b045, b046, b047, b048;
    byte b049, b050, b051, b052;
}

@SuppressWarnings({ "unused", "MultipleTopLevelClassesInFile", "ClassNameDiffersFromFileName" })
abstract class WFIStackHeadField extends WFIStackL0Pad
{
    static final long HEAD_OFFSET = UnsafeAccess.fieldOffset(WFIStackHeadField.class, "head");

    @SuppressWarnings( {"FieldCanBeLocal", "FieldMayBeFinal"})
    volatile Node head; // Accessed via UNSAFE.

    Node xchgHead(Node node)
    {
        return (Node) UnsafeAccess.UNSAFE.getAndSetObject(this, HEAD_OFFSET, node);
    }

    boolean casHead(Node expected, Node update)
    {
        return UnsafeAccess.UNSAFE.compareAndSwapObject(this, HEAD_OFFSET, expected, update);
    }
}

@SuppressWarnings({ "unused", "MultipleTopLevelClassesInFile", "ClassNameDiffersFromFileName" })
abstract class WFIStackL1Pad extends WFIStackHeadField
{
    // Isolate the 'head' field on its own cache line.
    // This brings the object instance size to 128 bytes, assuming 12-byte header
    // and 4-byte object reference size.
    byte b053, b054, b055, b056, b057, b058, b059, b060;
    byte b061, b062, b063, b064, b065, b066, b067, b068;
    byte b069, b070, b071, b072, b073, b074, b075, b076;
    byte b077, b078, b079, b080, b081, b082, b083, b084;
    byte b085, b086, b087, b088, b089, b090, b091, b092;
    byte b093, b094, b095, b096, b097, b098, b099, b100;
    byte b101, b102, b103, b104, b105, b106, b107, b108;
}

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
@SuppressWarnings({ "unchecked", "AssignmentToSuperclassField", "MultipleTopLevelClassesInFile" })
public class WFIStack<T extends Node> extends WFIStackL1Pad implements Iterable<T>
{
    /**
     * The super-class of all nodes, or entries, for {@link WFIStack wait-free intrusive stacks}.
     */
    public abstract static class Node
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

        private NodeIterator(Node start)
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
     * Returns {@code true} if the stack contains no elements.
     *
     * @return {@code true} if the stack contains no elements.
     */
    public boolean isEmpty()
    {
        return head == END;
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
        T n = (T) xchgHead(node);
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
        T n = (T) xchgHead(node);
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
     * This operation is lock-free, but not wait-free.
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
}