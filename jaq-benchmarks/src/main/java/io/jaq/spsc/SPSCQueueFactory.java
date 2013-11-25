package io.jaq.spsc;

import io.jaq.spsc.BQueue;
import io.jaq.spsc.FFBuffer;
import io.jaq.spsc.FFBufferWithOfferBatch;
import io.jaq.spsc.InlinedRingBufferQueue;

import java.util.Queue;

public class SPSCQueueFactory {
    public static Queue<Integer> createQueue() {
        int type = Integer.getInteger("q.type", 0);
        int capacity = 32 * 1024;
        switch (type) {
        case 0:
            return new InlinedRingBufferQueue<Integer>(capacity);
        case 1:
            return new BQueue<Integer>(capacity);
        case 2:
            return new FFBuffer<Integer>(capacity);
        case 3:
            return new FFBufferWithOfferBatch<Integer>(capacity);
        }
        throw new IllegalArgumentException("Type: " + type);
    }

}
