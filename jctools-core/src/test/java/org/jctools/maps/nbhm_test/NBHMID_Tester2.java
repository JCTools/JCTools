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
package org.jctools.maps.nbhm_test;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.jctools.maps.NonBlockingIdentityHashMap;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

// Test NonBlockingHashMap via JUnit
public class NBHMID_Tester2
{
    static private NonBlockingIdentityHashMap<String, String> _nbhm;

    @BeforeClass
    public static void setUp()
    {
        _nbhm = new NonBlockingIdentityHashMap<>();
    }

    @AfterClass
    public static void tearDown()
    {
        _nbhm = null;
    }

    // Test some basic stuff; add a few keys, remove a few keys
    @Test
    public void testBasic()
    {
        assertTrue(_nbhm.isEmpty());
        assertThat(_nbhm.putIfAbsent("k1", "v1"), nullValue());
        checkSizes(1);
        assertThat(_nbhm.putIfAbsent("k2", "v2"), nullValue());
        checkSizes(2);
        assertTrue(_nbhm.containsKey("k2"));
        assertThat(_nbhm.put("k1", "v1a"), is("v1"));
        assertThat(_nbhm.put("k2", "v2a"), is("v2"));
        checkSizes(2);
        assertThat(_nbhm.putIfAbsent("k2", "v2b"), is("v2a"));
        assertThat(_nbhm.remove("k1"), is("v1a"));
        assertFalse(_nbhm.containsKey("k1"));
        checkSizes(1);
        assertThat(_nbhm.remove("k1"), nullValue());
        assertThat(_nbhm.remove("k2"), is("v2a"));
        checkSizes(0);
        assertThat(_nbhm.remove("k2"), nullValue());
        assertThat(_nbhm.remove("k3"), nullValue());
        assertTrue(_nbhm.isEmpty());

        assertThat(_nbhm.put("k0", "v0"), nullValue());
        assertTrue(_nbhm.containsKey("k0"));
        checkSizes(1);
        assertThat(_nbhm.remove("k0"), is("v0"));
        assertFalse(_nbhm.containsKey("k0"));
        checkSizes(0);

        assertThat(_nbhm.replace("k0", "v0"), nullValue());
        assertFalse(_nbhm.containsKey("k0"));
        assertThat(_nbhm.put("k0", "v0"), nullValue());
        assertEquals(_nbhm.replace("k0", "v0a"), "v0");
        assertEquals(_nbhm.get("k0"), "v0a");
        assertThat(_nbhm.remove("k0"), is("v0a"));
        assertFalse(_nbhm.containsKey("k0"));
        checkSizes(0);

        assertThat(_nbhm.replace("k1", "v1"), nullValue());
        assertFalse(_nbhm.containsKey("k1"));
        assertThat(_nbhm.put("k1", "v1"), nullValue());
        assertEquals(_nbhm.replace("k1", "v1a"), "v1");
        assertEquals(_nbhm.get("k1"), "v1a");
        assertThat(_nbhm.remove("k1"), is("v1a"));
        assertFalse(_nbhm.containsKey("k1"));
        checkSizes(0);

        // Insert & Remove KeyBonks until the table resizes and we start
        // finding Tombstone keys- and KeyBonk's equals-call with throw a
        // ClassCastException if it sees a non-KeyBonk.
        NonBlockingIdentityHashMap<KeyBonk, String> dumb = new NonBlockingIdentityHashMap<>();
        for (int i = 0; i < 10000; i++)
        {
            final KeyBonk happy1 = new KeyBonk(i);
            assertThat(dumb.put(happy1, "and"), nullValue());
            if ((i & 1) == 0)
            {
                dumb.remove(happy1);
            }
            final KeyBonk happy2 = new KeyBonk(i); // 'equals' but not '=='
            dumb.get(happy2);
        }
    }

    // Check all iterators for correct size counts
    private void checkSizes(int expectedSize)
    {
        assertEquals("size()", _nbhm.size(), expectedSize);
        Collection<String> vals = _nbhm.values();
        checkSizes("values()", vals.size(), vals.iterator(), expectedSize);
        Set<String> keys = _nbhm.keySet();
        checkSizes("keySet()", keys.size(), keys.iterator(), expectedSize);
        Set<Map.Entry<String, String>> ents = _nbhm.entrySet();
        checkSizes("entrySet()", ents.size(), ents.iterator(), expectedSize);
    }

    // Check that the iterator iterates the correct number of times
    private void checkSizes(String msg, int sz, Iterator it, int expectedSize)
    {
        assertEquals(msg, expectedSize, sz);
        int result = 0;
        while (it.hasNext())
        {
            result++;
            it.next();
        }
        assertEquals(msg, expectedSize, result);
    }

    @Test
    public void testIteration()
    {
        assertTrue(_nbhm.isEmpty());
        assertThat(_nbhm.put("k1", "v1"), nullValue());
        assertThat(_nbhm.put("k2", "v2"), nullValue());

        String str1 = "";
        for (Map.Entry<String, String> e : _nbhm.entrySet())
        {
            str1 += e.getKey();
        }
        assertThat("found all entries", str1, anyOf(is("k1k2"), is("k2k1")));

        String str2 = "";
        for (String key : _nbhm.keySet())
        {
            str2 += key;
        }
        assertThat("found all keys", str2, anyOf(is("k1k2"), is("k2k1")));

        String str3 = "";
        for (String val : _nbhm.values())
        {
            str3 += val;
        }
        assertThat("found all vals", str3, anyOf(is("v1v2"), is("v2v1")));

        assertThat("toString works", _nbhm.toString(), anyOf(is("{k1=v1, k2=v2}"), is("{k2=v2, k1=v1}")));
        _nbhm.clear();
    }

    @Test
    public void testSerial()
    {
        assertTrue(_nbhm.isEmpty());
        assertThat(_nbhm.put("k1", "v1"), nullValue());
        assertThat(_nbhm.put("k2", "v2"), nullValue());

        // Serialize it out
        try
        {
            FileOutputStream fos = new FileOutputStream("NBHM_test.txt");
            ObjectOutputStream out = new ObjectOutputStream(fos);
            out.writeObject(_nbhm);
            out.close();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }

        // Read it back
        try
        {
            File f = new File("NBHM_test.txt");
            FileInputStream fis = new FileInputStream(f);
            ObjectInputStream in = new ObjectInputStream(fis);
            NonBlockingIdentityHashMap nbhm = (NonBlockingIdentityHashMap) in.readObject();
            in.close();

            assertEquals(_nbhm.size(), nbhm.size());
            Object[] keys = nbhm.keySet().toArray();
            if (keys[0].equals("k1"))
            {
                assertEquals(nbhm.get(keys[0]), "v1");
                assertEquals(nbhm.get(keys[1]), "v2");
            }
            else
            {
                assertEquals(nbhm.get(keys[1]), "v1");
                assertEquals(nbhm.get(keys[0]), "v2");
            }

            if (!f.delete())
            {
                throw new IOException("delete failed");
            }
        }
        catch (IOException | ClassNotFoundException ex)
        {
            ex.printStackTrace();
        }
        _nbhm.clear();
    }

    @Test
    public void testIterationBig2()
    {
        final int CNT = 10000;
        NonBlockingIdentityHashMap<Integer, String> nbhm = new NonBlockingIdentityHashMap<>();
        final String v = "v";
        for (int i = 0; i < CNT; i++)
        {
            final Integer z = i;
            String s0 = nbhm.get(z);
            assertThat(s0, nullValue());
            nbhm.put(z, v);
            String s1 = nbhm.get(z);
            assertThat(s1, is(v));
        }
        assertThat(nbhm.size(), is(CNT));
        _nbhm.clear();
    }

    @Test
    public void testIterationBig()
    {
        final int CNT = 10000;
        String[] keys = new String[CNT];
        String[] vals = new String[CNT];
        assertThat(_nbhm.size(), is(0));
        for (int i = 0; i < CNT; i++)
        {
            _nbhm.put(keys[i] = ("k" + i), vals[i] = ("v" + i));
        }
        assertThat(_nbhm.size(), is(CNT));

        int sz = 0;
        int sum = 0;
        for (String s : _nbhm.keySet())
        {
            sz++;
            assertThat("", s.charAt(0), is('k'));
            int x = Integer.parseInt(s.substring(1));
            sum += x;
            assertTrue(x >= 0 && x <= (CNT - 1));
        }
        assertThat("Found 10000 ints", sz, is(CNT));
        assertThat("Found all integers in list", sum, is(CNT * (CNT - 1) / 2));

        assertThat("can remove 3", _nbhm.remove(keys[3]), is(vals[3]));
        assertThat("can remove 4", _nbhm.remove(keys[4]), is(vals[4]));
        sz = 0;
        sum = 0;
        for (String s : _nbhm.keySet())
        {
            sz++;
            assertThat("", s.charAt(0), is('k'));
            int x = Integer.parseInt(s.substring(1));
            sum += x;
            assertTrue(x >= 0 && x <= (CNT - 1));
            String v = _nbhm.get(s);
            assertThat("", v.charAt(0), is('v'));
            assertThat("", s.substring(1), is(v.substring(1)));
        }
        assertThat("Found " + (CNT - 2) + " ints", sz, is(CNT - 2));
        assertThat("Found all integers in list", sum, is(CNT * (CNT - 1) / 2 - (3 + 4)));
        _nbhm.clear();
    }

    // Do some simple concurrent testing
    @Test
    public void testConcurrentSimple() throws InterruptedException
    {
        final NonBlockingIdentityHashMap<String, String> nbhm = new NonBlockingIdentityHashMap<>();
        final String[] keys = new String[20000];
        for (int i = 0; i < 20000; i++)
        {
            keys[i] = "k" + i;
        }

        // In 2 threads, add & remove even & odd elements concurrently
        Thread t1 = new Thread()
        {
            public void run()
            {
                work_helper(nbhm, "T1", 1, keys);
            }
        };
        t1.start();
        work_helper(nbhm, "T0", 0, keys);
        t1.join();

        // In the end, all members should be removed
        StringBuilder buf = new StringBuilder();
        buf.append("Should be emptyset but has these elements: {");
        boolean found = false;
        for (String x : nbhm.keySet())
        {
            buf.append(" ").append(x);
            found = true;
        }
        if (found)
        {
            System.out.println(buf + " }");
        }
        assertThat("concurrent size=0", nbhm.size(), is(0));
        assertThat("keySet size=0", nbhm.keySet().size(), is(0));
    }

    void work_helper(NonBlockingIdentityHashMap<String, String> nbhm, String thrd, int d, String[] keys)
    {
        final int ITERS = 20000;
        for (int j = 0; j < 10; j++)
        {
            //long start = System.nanoTime();
            for (int i = d; i < ITERS; i += 2)
            {
                assertThat("this key not in there, so putIfAbsent must work",
                    nbhm.putIfAbsent(keys[i], thrd), is((String) null));
            }
            for (int i = d; i < ITERS; i += 2)
            {
                assertTrue(nbhm.remove(keys[i], thrd));
            }
            //double delta_nanos = System.nanoTime()-start;
            //double delta_secs = delta_nanos/1000000000.0;
            //double ops = ITERS*2;
            //System.out.println("Thrd"+thrd+" "+(ops/delta_secs)+" ops/sec size="+nbhm.size());
        }
    }

    @Test
    public final void testNonBlockingIdentityHashMapSize()
    {
        NonBlockingIdentityHashMap<Long, String> items = new NonBlockingIdentityHashMap<>();
        items.put(100L, "100");
        items.put(101L, "101");

        assertEquals("keySet().size()", 2, items.keySet().size());
        assertTrue("keySet().contains(100)", items.keySet().contains(100L));
        assertTrue("keySet().contains(101)", items.keySet().contains(101L));

        assertEquals("values().size()", 2, items.values().size());
        assertTrue("values().contains(\"100\")", items.values().contains("100"));
        assertTrue("values().contains(\"101\")", items.values().contains("101"));

        assertEquals("entrySet().size()", 2, items.entrySet().size());
        boolean found100 = false;
        boolean found101 = false;
        for (Map.Entry<Long, String> entry : items.entrySet())
        {
            if (entry.getKey().equals(100L))
            {
                assertEquals("entry[100].getValue()==\"100\"", "100", entry.getValue());
                found100 = true;
            }
            else if (entry.getKey().equals(101L))
            {
                assertEquals("entry[101].getValue()==\"101\"", "101", entry.getValue());
                found101 = true;
            }
        }
        assertTrue("entrySet().contains([100])", found100);
        assertTrue("entrySet().contains([101])", found101);
    }

    // Concurrent insertion & then iterator test.
    @Test
    public void testNonBlockingIdentityHashMapIterator() throws InterruptedException
    {
        final int ITEM_COUNT1 = 1000;
        final int THREAD_COUNT = 5;
        final int PER_CNT = ITEM_COUNT1 / THREAD_COUNT;
        final int ITEM_COUNT = PER_CNT * THREAD_COUNT; // fix roundoff for odd thread counts

        NonBlockingIdentityHashMap<Long, TestKey> nbhml = new NonBlockingIdentityHashMap<>();
        // use a barrier to open the gate for all threads at once to avoid rolling
        // start and no actual concurrency
        final CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
        final ExecutorService ex = Executors.newFixedThreadPool(THREAD_COUNT);
        final CompletionService<Object> co = new ExecutorCompletionService<>(ex);
        for (int i = 0; i < THREAD_COUNT; i++)
        {
            co.submit(new NBHMLFeeder(nbhml, PER_CNT, barrier, i * PER_CNT));
        }
        for (int retCount = 0; retCount < THREAD_COUNT; retCount++)
        {
            co.take();
        }
        ex.shutdown();

        assertEquals("values().size()", ITEM_COUNT, nbhml.values().size());
        assertEquals("entrySet().size()", ITEM_COUNT, nbhml.entrySet().size());
        int itemCount = 0;
        for (TestKey K : nbhml.values())
        {
            itemCount++;
        }
        assertEquals("values().iterator() count", ITEM_COUNT, itemCount);
    }

    // --- Customer Test Case 3 ------------------------------------------------
    private TestKeyFeeder getTestKeyFeeder()
    {
        final TestKeyFeeder feeder = new TestKeyFeeder();
        feeder.checkedPut(10401000001844L, 657829272, 680293140); // section 12
        feeder.checkedPut(10401000000614L, 657829272, 401326994); // section 12
        feeder.checkedPut(10400345749304L, 2095121916, -9852212); // section 12
        feeder.checkedPut(10401000002204L, 657829272, 14438460); // section 12
        feeder.checkedPut(10400345749234L, 1186831289, -894006017); // section 12
        feeder.checkedPut(10401000500234L, 969314784, -2112018706); // section 12
        feeder.checkedPut(10401000000284L, 657829272, 521425852); // section 12
        feeder.checkedPut(10401000002134L, 657829272, 208406306); // section 12
        feeder.checkedPut(10400345749254L, 2095121916, -341939818); // section 12
        feeder.checkedPut(10401000500384L, 969314784, -2136811544); // section 12
        feeder.checkedPut(10401000001944L, 657829272, 935194952); // section 12
        feeder.checkedPut(10400345749224L, 1186831289, -828214183); // section 12
        feeder.checkedPut(10400345749244L, 2095121916, -351234120); // section 12
        feeder.checkedPut(10400333128994L, 2095121916, -496909430); // section 12
        feeder.checkedPut(10400333197934L, 2095121916, 2147144926); // section 12
        feeder.checkedPut(10400333197944L, 2095121916, -2082366964); // section 12
        feeder.checkedPut(10400336947684L, 2095121916, -1404212288); // section 12
        feeder.checkedPut(10401000000594L, 657829272, 124369790); // section 12
        feeder.checkedPut(10400331896264L, 2095121916, -1028383492); // section 12
        feeder.checkedPut(10400332415044L, 2095121916, 1629436704); // section 12
        feeder.checkedPut(10400345749614L, 1186831289, 1027996827); // section 12
        feeder.checkedPut(10401000500424L, 969314784, -1871616544); // section 12
        feeder.checkedPut(10400336947694L, 2095121916, -1468802722); // section 12
        feeder.checkedPut(10410002672481L, 2154973, 1515288586); // section 12
        feeder.checkedPut(10410345749171L, 2154973, 2084791828); // section 12
        feeder.checkedPut(10400004960671L, 2154973, 1554754674); // section 12
        feeder.checkedPut(10410009983601L, 2154973, -2049707334); // section 12
        feeder.checkedPut(10410335811601L, 2154973, 1547385114); // section 12
        feeder.checkedPut(10410000005951L, 2154973, -1136117016); // section 12
        feeder.checkedPut(10400004938331L, 2154973, -1361373018); // section 12
        feeder.checkedPut(10410001490421L, 2154973, -818792874); // section 12
        feeder.checkedPut(10400001187131L, 2154973, 649763142); // section 12
        feeder.checkedPut(10410000409071L, 2154973, -614460616); // section 12
        feeder.checkedPut(10410333717391L, 2154973, 1343531416); // section 12
        feeder.checkedPut(10410336680071L, 2154973, -914544144); // section 12
        feeder.checkedPut(10410002068511L, 2154973, -746995576); // section 12
        feeder.checkedPut(10410336207851L, 2154973, 863146156); // section 12
        feeder.checkedPut(10410002365251L, 2154973, 542724164); // section 12
        feeder.checkedPut(10400335812581L, 2154973, 2146284796); // section 12
        feeder.checkedPut(10410337345361L, 2154973, -384625318); // section 12
        feeder.checkedPut(10410000409091L, 2154973, -528258556); // section 12
        return feeder;
    }

    // ---
    @Test
    public void testNonBlockingIdentityHashMapIteratorMultithreaded() throws InterruptedException, ExecutionException
    {
        TestKeyFeeder feeder = getTestKeyFeeder();
        final int itemCount = feeder.size();

        // validate results
        final NonBlockingIdentityHashMap<Long, TestKey> items = feeder.getMapMultithreaded();
        assertEquals("size()", itemCount, items.size());

        assertEquals("values().size()", itemCount, items.values().size());

        assertEquals("entrySet().size()", itemCount, items.entrySet().size());

        int iteratorCount = 0;
        for (TestKey m : items.values())
        {
            iteratorCount++;
        }
        // sometimes a different result comes back the second time
        int iteratorCount2 = 0;
        for (TestKey m : items.values())
        {
            iteratorCount2++;
        }
        assertEquals("iterator counts differ", iteratorCount, iteratorCount2);
        assertEquals("values().iterator() count", itemCount, iteratorCount);
    }

    // Throw a ClassCastException if I see a tombstone during key-compares
    private static class KeyBonk
    {
        final int _x;

        KeyBonk(int i)
        {
            _x = i;
        }

        public int hashCode()
        {
            return (_x >> 2);
        }

        public boolean equals(Object o)
        {
            return o != null && ((KeyBonk) o)._x    // Throw CCE here
                == this._x;
        }


        public String toString()
        {
            return "Bonk_" + Integer.toString(_x);
        }
    }

    // --- NBHMLFeeder ---
    // Class to be called from another thread, to get concurrent installs into
    // the table.
    static private class NBHMLFeeder implements Callable<Object>
    {
        static private final Random _rand = new Random(System.currentTimeMillis());
        private final NonBlockingIdentityHashMap<Long, TestKey> _map;
        private final int _count;
        private final CyclicBarrier _barrier;
        private final long _offset;

        public NBHMLFeeder(
            final NonBlockingIdentityHashMap<Long, TestKey> map,
            final int count,
            final CyclicBarrier barrier,
            final long offset)
        {
            _map = map;
            _count = count;
            _barrier = barrier;
            _offset = offset;
        }

        public Object call() throws Exception
        {
            _barrier.await();         // barrier, to force racing start
            for (long j = 0; j < _count; j++)
            {
                _map.put(
                    j + _offset,
                    new TestKey(_rand.nextLong(), _rand.nextInt(), (short) _rand.nextInt(Short.MAX_VALUE)));
            }
            return null;
        }
    }

    // --- TestKey ---
    // Funny key tests all sorts of things, has a pre-wired hashCode & equals.
    static private final class TestKey
    {
        public final int _type;
        public final long _id;
        public final int _hash;

        public TestKey(final long id, final int type, int hash)
        {
            _id = id;
            _type = type;
            _hash = hash;
        }

        public int hashCode()
        {
            return _hash;
        }

        public boolean equals(Object object)
        {
            if (null == object)
            {
                return false;
            }
            if (object == this)
            {
                return true;
            }
            if (object.getClass() != this.getClass())
            {
                return false;
            }
            final TestKey other = (TestKey) object;
            return (this._type == other._type && this._id == other._id);
        }

        public String toString()
        {
            return String.format("%s:%d,%d,%d", getClass().getSimpleName(), _id, _type, _hash);
        }
    }

    // ---
    static private class TestKeyFeeder
    {
        private final Hashtable<Integer, List<TestKey>> _items = new Hashtable<>();
        private int _size = 0;

        public int size()
        {
            return _size;
        }

        // Put items into the hashtable, sorted by 'type' into LinkedLists.
        public void checkedPut(final long id, final int type, final int hash)
        {
            _size++;
            final TestKey item = new TestKey(id, type, hash);
            if (!_items.containsKey(type))
            {
                _items.put(type, new LinkedList<>());
            }
            _items.get(type).add(item);
        }

        public NonBlockingIdentityHashMap<Long, TestKey> getMapMultithreaded()
            throws InterruptedException, ExecutionException
        {
            final int threadCount = _items.keySet().size();
            final NonBlockingIdentityHashMap<Long, TestKey> map = new NonBlockingIdentityHashMap<>();

            // use a barrier to open the gate for all threads at once to avoid rolling start and no actual concurrency
            final CyclicBarrier barrier = new CyclicBarrier(threadCount);
            final ExecutorService ex = Executors.newFixedThreadPool(threadCount);
            final CompletionService<Integer> co = new ExecutorCompletionService<>(ex);
            for (Integer type : _items.keySet())
            {
                // A linked-list of things to insert
                List<TestKey> items = _items.get(type);
                TestKeyFeederThread feeder = new TestKeyFeederThread(items, map, barrier);
                co.submit(feeder);
            }

            // wait for all threads to return
            int itemCount = 0;
            for (int retCount = 0; retCount < threadCount; retCount++)
            {
                final Future<Integer> result = co.take();
                itemCount += result.get();
            }
            ex.shutdown();
            return map;
        }
    }

    // --- TestKeyFeederThread
    static private class TestKeyFeederThread implements Callable<Integer>
    {
        private final NonBlockingIdentityHashMap<Long, TestKey> _map;
        private final List<TestKey> _items;
        private final CyclicBarrier _barrier;

        public TestKeyFeederThread(
            final List<TestKey> items,
            final NonBlockingIdentityHashMap<Long, TestKey> map,
            final CyclicBarrier barrier)
        {
            _map = map;
            _items = items;
            _barrier = barrier;
        }

        public Integer call() throws Exception
        {
            _barrier.await();
            int count = 0;
            for (TestKey item : _items)
            {
                if (_map.contains(item._id))
                {
                    System.err.printf("COLLISION DETECTED: %s exists\n", item.toString());
                }
                final TestKey exists = _map.putIfAbsent(item._id, item);
                if (exists == null)
                {
                    count++;
                }
                else
                {
                    System.err.printf("COLLISION DETECTED: %s exists as %s\n", item.toString(), exists.toString());
                }
            }
            return count;
        }
    }

    // This test is a copy of the JCK test Hashtable2027, which is incorrect.
    // The test requires a particular order of values to appear in the esa
    // array - but this is not part of the spec.  A different implementation
    // might put the same values into the array but in a different order.
    //public void testToArray() {
    //  NonBlockingIdentityHashMap ht = new NonBlockingIdentityHashMap();
    //
    //  ht.put("Nine", new Integer(9));
    //  ht.put("Ten", new Integer(10));
    //  ht.put("Ten1", new Integer(100));
    //
    //  Collection es = ht.values();
    //
    //  Object [] esa = es.toArray();
    //
    //  ht.remove("Ten1");
    //
    //  assertEquals( "size check", es.size(), 2 );
    //  assertEquals( "iterator_order[0]", new Integer( 9), esa[0] );
    //  assertEquals( "iterator_order[1]", new Integer(10), esa[1] );
    //}
}
