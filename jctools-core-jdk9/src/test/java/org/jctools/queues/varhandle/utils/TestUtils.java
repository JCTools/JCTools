package org.jctools.queues.varhandle.utils;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.spec.Preference;
import org.jctools.queues.varhandle.MpscArrayQueueVarHandle;
import org.jctools.queues.varhandle.SpmcArrayQueueVarHandle;
import org.jctools.queues.varhandle.SpscArrayQueueVarHandle;

public final class TestUtils {
  private TestUtils() {}

  public static Object[] makeMpq(int producers, int consumers, int capacity, Ordering ordering) {
    ConcurrentQueueSpec spec =
        new ConcurrentQueueSpec(producers, consumers, capacity, ordering, Preference.NONE);
    return new Object[] {spec, newQueue(spec)};
  }

  public static <E> Queue<E> newQueue(ConcurrentQueueSpec qs) {
    if (qs.isBounded()) {
      // SPSC
      if (qs.isSpsc()) {
        return new SpscArrayQueueVarHandle<>(qs.capacity);
      }
      // MPSC
      else if (qs.isMpsc()) {
        if (qs.ordering != Ordering.NONE) {
          return new MpscArrayQueueVarHandle<>(qs.capacity);
        } else {
          throw new UnsupportedOperationException("Not yet implemented");
        }
      }
      // SPMC
      else if (qs.isSpmc()) {
        return new SpmcArrayQueueVarHandle<>(qs.capacity);
      }
      // MPMC
      else {
        throw new UnsupportedOperationException("Not yet implemented");
      }
    } else {
      // SPSC
      if (qs.isSpsc()) {
        throw new UnsupportedOperationException("Not yet implemented");
      }
      // MPSC
      else if (qs.isMpsc()) {
        throw new UnsupportedOperationException("Not yet implemented");
      }
    }
    return new ConcurrentLinkedQueue<E>();
  }
}
