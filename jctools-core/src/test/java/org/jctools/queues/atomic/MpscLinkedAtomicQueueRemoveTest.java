package org.jctools.queues.atomic;

import java.util.Queue;

import org.jctools.queues.ScQueueRemoveTest;

public class MpscLinkedAtomicQueueRemoveTest extends ScQueueRemoveTest {
    @Override
    protected Queue<Integer> newQueue() {
        return new MpscLinkedAtomicQueue<>();
    }
}
