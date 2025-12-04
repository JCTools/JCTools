package org.jctools.queues.varhandle;

import static org.hamcrest.Matchers.is;
import static org.jctools.util.TestUtil.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.QueueSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.spec.Preference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscArrayVarHandleTest extends QueueSanityTest {
  public QueueSanityTestMpscArrayVarHandleTest(ConcurrentQueueSpec spec, Queue<Integer> queue) {
    super(spec, queue);
  }

  private static Object[] make(int producers, int consumers, int capacity) {
    ConcurrentQueueSpec spec =
        new ConcurrentQueueSpec(producers, consumers, capacity, Ordering.FIFO, Preference.NONE);
    return new Object[] {spec, new MpscArrayQueue<>(spec.capacity)};
  }

  @Parameterized.Parameters
  public static Collection<Object[]> parameters() {
    ArrayList<Object[]> list = new ArrayList<>();
    list.add(makeMpq(0, 0, 2, Ordering.FIFO));
    list.add(makeMpq(0, 0, SIZE, Ordering.FIFO));
    return list;
  }

  @Test
  public void testOfferPollSemantics() throws Exception {
    assumeThat(spec.capacity, is(2));
    final AtomicBoolean stop = new AtomicBoolean();
    final AtomicBoolean consumerLock = new AtomicBoolean(true);
    final Queue<Integer> q = this.queue;
    // fill up the queue
    while (q.offer(1)) {
      // fill it up
    }
    // queue has 2 empty slots
    q.poll();
    q.poll();

    final Val fail = new Val();
    final Runnable runnable =
        new Runnable() {
          @Override
          public void run() {
            while (!stop.get()) {
              if (!q.offer(1)) {
                fail.value++;
              }

              while (!consumerLock.compareAndSet(true, false)) {
                ;
              }
              if (q.poll() == null) {
                fail.value++;
              }
              consumerLock.lazySet(true);
            }
          }
        };
    Thread t1 = new Thread(runnable);
    Thread t2 = new Thread(runnable);

    t1.start();
    t2.start();
    Thread.sleep(1000);
    stop.set(true);
    t1.join();
    t2.join();
    assertEquals("Unexpected offer/poll observed", 0, fail.value);
  }
}
