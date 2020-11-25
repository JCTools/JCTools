package org.jctools.maps;

import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;

public class NBHMIdentityKeyAtomicityTest
{
    static final int THREAD_SEGMENT = 1000000;

    @Test
    public void putReturnValuesAreDistinct() throws Exception
    {
        Map<String, Long> map = new NonBlockingIdentityHashMap<>();
        map.put("K", -1l);
        int processors = Runtime.getRuntime().availableProcessors();
        CountDownLatch ready = new CountDownLatch(processors);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(processors);
        AtomicBoolean keepRunning = new AtomicBoolean(true);
        PutKey[] putKeys = new PutKey[processors];
        for (int i = 0; i < processors; i++)
        {
            putKeys[i] = new PutKey(map, "K", keepRunning, ready, start, done, i * THREAD_SEGMENT);
            Thread t = new Thread(putKeys[i]);
            t.setName("Putty McPutkey-"+i);
            t.start();
        }
        ready.await();
        start.countDown();
        Thread.sleep(1000);
        keepRunning.set(false);
        done.await();
        Set<Long> values = new HashSet((int)(processors*THREAD_SEGMENT));
        long totalKeys = 0;
        for (PutKey putKey : putKeys)
        {
            values.addAll(putKey.values);
            totalKeys += putKey.endIndex - putKey.startIndex;
        }
        assertEquals(totalKeys, values.size());
    }

    static class PutKey implements Runnable
    {
        final Map<String, Long> map;
        final String key;
        final AtomicBoolean keepRunning;
        final CountDownLatch ready;
        final CountDownLatch start;
        final CountDownLatch done;
        final int startIndex;
        int endIndex;

        List<Long> values = new ArrayList<>(THREAD_SEGMENT);

        PutKey(
            Map<String, Long> map,
            String key,
            AtomicBoolean keepRunning, CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch done,
            int startIndex)
        {
            this.map = map;
            this.key = key;
            this.keepRunning = keepRunning;
            this.ready = ready;
            this.start = start;
            this.done = done;
            this.startIndex = startIndex;
            assert startIndex >= 0 && startIndex + THREAD_SEGMENT > 0;
        }

        @Override
        public void run()
        {
            ready.countDown();
            try
            {
                start.await();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
                return;
            }
            long limit = startIndex + THREAD_SEGMENT;
            long v = startIndex;
            String k = key;
            for (; v < limit && keepRunning.get(); v++)
            {
                values.add(map.put(k, v));
            }
            endIndex = (int) v;
            done.countDown();
        }
    }
}
