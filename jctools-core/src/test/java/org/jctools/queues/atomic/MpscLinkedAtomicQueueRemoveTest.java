package org.jctools.queues.atomic;

import org.jctools.queues.ScQueueRemoveTest;

import java.util.Queue;

public class MpscLinkedAtomicQueueRemoveTest extends ScQueueRemoveTest {
    @Override
    protected Queue<Integer> newQueue() {
        return new MpscLinkedAtomicQueue<>();
    }
}
