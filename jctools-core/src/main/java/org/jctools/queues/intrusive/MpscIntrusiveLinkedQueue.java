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
package org.jctools.queues.intrusive;

import java.util.Iterator;

import static org.jctools.util.UnsafeAccess.UNSAFE;

/**
 * Intrusive MPSC queue implementation based on <a href="http://www.1024cores.net/home/lock-free-algorithms/queues/intrusive-mpsc-node-based-queue">Intrusive
 * MPSC node-based queue</a> as presented on <a href="http://www.1024cores.net">1024cores</a> by Dmitry Vyukov.
 *
 * @see Node
 */
public class MpscIntrusiveLinkedQueue {
    private volatile Node head;
    private Node tail;
    private final Node stub;

    public MpscIntrusiveLinkedQueue() {
        this.stub = head = tail = new NodeImpl();
    }

    public boolean offer(Node node) {
        if (node == null) {
            throw new NullPointerException();
        }
        node.setNext(null);
        Node prev = xchgHead(node);
        prev.setNext(node);
        return true;
    }

    public Node poll() {
        Node tail = this.tail;
        Node next = tail.getNext();

        if (tail == this.stub) {
            if (next == null) {
                return null;
            }
            this.tail = next;
            tail = next;
            next = next.getNext();
        }
        if (next != null) {
            this.tail = next;
            return tail;
        }

        Node head = this.head;
        if (tail != head) {
            return null;
        }

        offer(stub);
        next = tail.getNext();
        if (next != null) {
            this.tail = next;
            return tail;
        }

        return null;
    }

    public Node peek() {
        final Node tail = this.tail;

        if (tail == stub) {
            return tail.getNext();
        } else {
            return tail;
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    public void clear() {
        while (poll() != null || !isEmpty());
    }

    public int size() {
        Node tail = this.tail;
        if (tail == this.stub) {
            tail = tail.getNext();
        }

        int size = 0;
        while (tail != null) {
            tail = tail.getNext();
            size++;
        }
        return size;
    }

    public boolean isEmpty() {
        return peek() == null;
    }

    public Iterator<Node> iterator() {
        throw new UnsupportedOperationException();
    }

    private Node xchgHead(Node node) {
        // TODO: add support for JDK < 8 per org.jctools.queues.MpscLinkedQueue / MpscLinkedQueue8
        return (Node) UNSAFE.getAndSetObject(this, HEAD_OFFSET, node);
    }

    private final static long HEAD_OFFSET;

    static {
        try {
            HEAD_OFFSET = UNSAFE.objectFieldOffset(MpscIntrusiveLinkedQueue.class.getDeclaredField("head"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
