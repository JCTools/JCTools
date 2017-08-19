package org.jctools.queues.atomic;

import java.util.Queue;

public class MpscLinkedAtomicQueueTest extends ScLinkedAtomicQueueTest {
    @Override
    protected Queue<Integer> newQueue() {
        return new MpscLinkedAtomicQueue<>();
    }
}
