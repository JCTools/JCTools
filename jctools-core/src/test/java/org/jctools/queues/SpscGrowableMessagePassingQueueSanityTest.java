package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SpscGrowableMessagePassingQueueSanityTest extends MessagePassingQueueSanityTest {

   @Parameterized.Parameters
   public static Collection<Object[]> parameters() {
      ArrayList<Object[]> list = new ArrayList<Object[]>();
      list.add(makeMpq(0, 1, 16, Ordering.FIFO, new SpscGrowableArrayQueue<>(8, 16)));
      list.add(makeMpq(0, 1, SIZE, Ordering.FIFO, new SpscGrowableArrayQueue<>(8, SIZE)));
      return list;
   }

   public SpscGrowableMessagePassingQueueSanityTest(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue) {
      super(spec, queue);
   }

}
