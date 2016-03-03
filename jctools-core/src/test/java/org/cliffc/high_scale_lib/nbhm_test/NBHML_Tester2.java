/*
 * Written by Cliff Click and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 * Additional test cases provided by Andy Martin of TeleAtlas.
 */

package Testing.NBHM_Tester;
import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import junit.framework.TestCase;
import org.cliffc.high_scale_lib.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

// Test NonBlockingHashMapLong via JUnit
public class NBHML_Tester2 extends TestCase {
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("Testing.NBHM_Tester.NBHML_Tester2");
  }

  private NonBlockingHashMapLong<String> _nbhml;
  protected void setUp   () { _nbhml = new NonBlockingHashMapLong<String>(); }
  protected void tearDown() { _nbhml = null; }

  // Test some basic stuff; add a few keys, remove a few keys
  public void testBasic() {
    assertTrue ( _nbhml.isEmpty() );
    assertThat ( _nbhml.put(1,"v1"), nullValue() );
    checkSizes (1);
    assertThat ( _nbhml.putIfAbsent(2,"v2"), nullValue() );
    checkSizes (2);
    assertTrue ( _nbhml.containsKey(2) );
    assertThat ( _nbhml.put(1,"v1a"), is("v1") );
    assertThat ( _nbhml.put(2,"v2a"), is("v2") );
    checkSizes (2);
    assertThat ( _nbhml.putIfAbsent(2,"v2b"), is("v2a") );
    assertThat ( _nbhml.remove(1), is("v1a") );
    assertFalse( _nbhml.containsKey(1) );
    checkSizes (1);
    assertThat ( _nbhml.remove(1), nullValue() );
    assertThat ( _nbhml.remove(2), is("v2a") );
    checkSizes (0);
    assertThat ( _nbhml.remove(2), nullValue() );
    assertThat ( _nbhml.remove("k3"), nullValue() );
    assertTrue ( _nbhml.isEmpty() );

    assertThat ( _nbhml.put(0,"v0"), nullValue() );
    assertTrue ( _nbhml.containsKey(0) );
    checkSizes (1);
    assertThat ( _nbhml.remove(0), is("v0") );
    assertFalse( _nbhml.containsKey(0) );
    checkSizes (0);

    assertThat ( _nbhml.replace(0,"v0"), nullValue() );
    assertFalse( _nbhml.containsKey(0) );
    assertThat ( _nbhml.put(0,"v0"), nullValue() );
    assertEquals(_nbhml.replace(0,"v0a"), "v0" );
    assertEquals(_nbhml.get(0), "v0a" );
    assertThat ( _nbhml.remove(0), is("v0a") );
    assertFalse( _nbhml.containsKey(0) );
    checkSizes (0);

    assertThat ( _nbhml.replace(1,"v1"), nullValue() );
    assertFalse( _nbhml.containsKey(1) );
    assertThat ( _nbhml.put(1,"v1"), nullValue() );
    assertEquals(_nbhml.replace(1,"v1a"), "v1" );
    assertEquals(_nbhml.get(1), "v1a" );
    assertThat ( _nbhml.remove(1), is("v1a") );
    assertFalse( _nbhml.containsKey(1) );
    checkSizes (0);

    // Simple insert of simple keys, with no reprobing on insert until the
    // table gets full exactly.  Then do a 'get' on the totally full table.
    NonBlockingHashMapLong<Object> map = new NonBlockingHashMapLong<Object>(32);
    for( int i = 1; i < 32; i++ )
      map.put(i, new Object());
    map.get(33);  // this causes a NPE
  }

  // Check all iterators for correct size counts
  private void checkSizes(int expectedSize) {
    assertEquals( "size()", _nbhml.size(), expectedSize );
    Collection<String> vals = _nbhml.values();
    checkSizes("values()",vals.size(),vals.iterator(),expectedSize);
    Set<Long> keys = _nbhml.keySet();
    checkSizes("keySet()",keys.size(),keys.iterator(),expectedSize);
    Set<Entry<Long,String>> ents = _nbhml.entrySet();
    checkSizes("entrySet()",ents.size(),ents.iterator(),expectedSize);
  }

  // Check that the iterator iterates the correct number of times
  private void checkSizes(String msg, int sz, Iterator it, int expectedSize) {
    assertEquals( msg, expectedSize, sz );
    int result = 0;
    while (it.hasNext()) {
      result++;
      it.next();
    }
    assertEquals( msg, expectedSize, result );
  }


  public void testIterationBig2() {
    final int CNT = 10000;
    assertThat( _nbhml.size(), is(0) );
    final String v = "v";
    for( int i=0; i<CNT; i++ ) {
      _nbhml.put(i,v);
      String s = _nbhml.get(i);
      assertThat( s, is(v) );
    }
    assertThat( _nbhml.size(), is(CNT) );
  }


  public void testIteration() {
    assertTrue ( _nbhml.isEmpty() );
    assertThat ( _nbhml.put(1,"v1"), nullValue() );
    assertThat ( _nbhml.put(2,"v2"), nullValue() );

    String str1 = "";
    for( Iterator<Map.Entry<Long,String>> i = _nbhml.entrySet().iterator(); i.hasNext(); ) {
      Map.Entry<Long,String> e = i.next();
      str1 += e.getKey();
    }
    assertThat("found all entries",str1,anyOf(is("12"),is("21")));

    String str2 = "";
    for( Iterator<Long> i = _nbhml.keySet().iterator(); i.hasNext(); ) {
      Long key = i.next();
      str2 += key;
    }
    assertThat("found all keys",str2,anyOf(is("12"),is("21")));

    String str3 = "";
    for( Iterator<String> i = _nbhml.values().iterator(); i.hasNext(); ) {
      String val = i.next();
      str3 += val;
    }
    assertThat("found all vals",str3,anyOf(is("v1v2"),is("v2v1")));

    assertThat("toString works",_nbhml.toString(), anyOf(is("{1=v1, 2=v2}"),is("{2=v2, 1=v1}")));
  }

  public void testSerial() {
    assertTrue ( _nbhml.isEmpty() );
    assertThat ( _nbhml.put(0x12345678L,"v1"), nullValue() );
    assertThat ( _nbhml.put(0x87654321L,"v2"), nullValue() );

    // Serialize it out
    try {
      FileOutputStream fos = new FileOutputStream("NBHML_test.txt");
      ObjectOutputStream out = new ObjectOutputStream(fos);
      out.writeObject(_nbhml);
      out.close();
    } catch(IOException ex) {
      ex.printStackTrace();
    }

    // Read it back
    try {
      File f = new File("NBHML_test.txt");
      FileInputStream fis = new FileInputStream(f);
      ObjectInputStream in = new ObjectInputStream(fis);
      NonBlockingHashMapLong nbhml = (NonBlockingHashMapLong)in.readObject();
      in.close();
      assertEquals(_nbhml.toString(),nbhml.toString());
      if( !f.delete() ) throw new IOException("delete failed");
    } catch(IOException ex) {
      ex.printStackTrace();
    } catch(ClassNotFoundException ex) {
      ex.printStackTrace();
    }

  }

  public void testIterationBig() {
    final int CNT = 10000;
    assertThat( _nbhml.size(), is(0) );
    for( int i=0; i<CNT; i++ )
      _nbhml.put(i,"v"+i);
    assertThat( _nbhml.size(), is(CNT) );

    int sz =0;
    int sum = 0;
    for( long x : _nbhml.keySet() ) {
      sz++;
      sum += x;
      assertTrue(x>=0 && x<=(CNT-1));
    }
    assertThat("Found 10000 ints",sz,is(CNT));
    assertThat("Found all integers in list",sum,is(CNT*(CNT-1)/2));

    assertThat( "can remove 3", _nbhml.remove(3), is("v3") );
    assertThat( "can remove 4", _nbhml.remove(4), is("v4") );
    sz =0;
    sum = 0;
    for( long x : _nbhml.keySet() ) {
      sz++;
      sum += x;
      assertTrue(x>=0 && x<=(CNT-1));
      String v = _nbhml.get(x);
      assertThat("",v.charAt(0),is('v'));
      assertThat("",x,is(Long.parseLong(v.substring(1))));
    }
    assertThat("Found "+(CNT-2)+" ints",sz,is(CNT-2));
    assertThat("Found all integers in list",sum,is(CNT*(CNT-1)/2 - (3+4)));
  }

  // Do some simple concurrent testing
  public void testConcurrentSimple() throws InterruptedException {
    final NonBlockingHashMapLong<String> nbhml = new NonBlockingHashMapLong<String>();

    // In 2 threads, add & remove even & odd elements concurrently
    final int num_thrds = 2;
    Thread ts[] = new Thread[num_thrds];
    for( int i=1; i<num_thrds; i++ ) {
      final int x = i;
      ts[i] = new Thread() { public void run() { work_helper(nbhml,x,num_thrds); } };
    }
    for( int i=1; i<num_thrds; i++ )
      ts[i].start();
    work_helper(nbhml,0,num_thrds);
    for( int i=1; i<num_thrds; i++ )
      ts[i].join();

    // In the end, all members should be removed
    StringBuffer buf = new StringBuffer();
    buf.append("Should be emptyset but has these elements: {");
    boolean found = false;
    for( long x : nbhml.keySet() ) {
      buf.append(" ").append(x);
      found = true;
    }
    if( found ) System.out.println(buf+" }");
    assertThat( "concurrent size=0", nbhml.size(), is(0) );
    for( long x : nbhml.keySet() ) {
      assertTrue("No elements so never get here",false);
    }
  }

  void work_helper(NonBlockingHashMapLong<String> nbhml, int d, int num_thrds) {
    String thrd = "T"+d;
    final int ITERS = 20000;
    for( int j=0; j<10; j++ ) {
      //long start = System.nanoTime();
      for( int i=d; i<ITERS; i+=num_thrds )
        assertThat( "key "+i+" not in there, so putIfAbsent must work",
                    nbhml.putIfAbsent((long)i,thrd), is((String)null) );
      for( int i=d; i<ITERS; i+=num_thrds )
        assertTrue( nbhml.remove((long)i,thrd) );
      //double delta_nanos = System.nanoTime()-start;
      //double delta_secs = delta_nanos/1000000000.0;
      //double ops = ITERS*2;
      //System.out.println("Thrd"+thrd+" "+(ops/delta_secs)+" ops/sec size="+nbhml.size());
    }
  }


  // --- Customer Test Case 1 ------------------------------------------------
  public final void testNonBlockingHashMapSize() {
    NonBlockingHashMapLong<String> items = new NonBlockingHashMapLong<String>();
    items.put(Long.valueOf(100), "100");
    items.put(Long.valueOf(101), "101");

    assertEquals("keySet().size()", 2, items.keySet().size());
    assertTrue("keySet().contains(100)", items.keySet().contains(Long.valueOf(100)));
    assertTrue("keySet().contains(101)", items.keySet().contains(Long.valueOf(101)));

    assertEquals("values().size()", 2, items.values().size());
    assertTrue("values().contains(\"100\")", items.values().contains("100"));
    assertTrue("values().contains(\"101\")", items.values().contains("101"));

    assertEquals("entrySet().size()", 2, items.entrySet().size());
    boolean found100 = false;
    boolean found101 = false;
    for (Entry<Long, String> entry : items.entrySet()) {
      if (entry.getKey().equals(Long.valueOf(100))) {
        assertEquals("entry[100].getValue()==\"100\"", "100", entry.getValue());
        found100 = true;
      } else if (entry.getKey().equals(Long.valueOf(101))) {
        assertEquals("entry[101].getValue()==\"101\"", "101", entry.getValue());
        found101 = true;
      }
    }
    assertTrue("entrySet().contains([100])", found100);
    assertTrue("entrySet().contains([101])", found101);
  }

  // --- Customer Test Case 2 ------------------------------------------------
  // Concurrent insertion & then iterator test.
  static public void testNonBlockingHashMapIterator() throws InterruptedException {
    final int ITEM_COUNT1 = 1000;
    final int THREAD_COUNT = 5;
    final int PER_CNT = ITEM_COUNT1/THREAD_COUNT;
    final int ITEM_COUNT = PER_CNT*THREAD_COUNT; // fix roundoff for odd thread counts

    NonBlockingHashMapLong<TestKey> nbhml = new NonBlockingHashMapLong<TestKey>();
    // use a barrier to open the gate for all threads at once to avoid rolling
    // start and no actual concurrency
    final CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
    final ExecutorService ex = Executors.newFixedThreadPool(THREAD_COUNT);
    final CompletionService<Object> co = new ExecutorCompletionService<Object>(ex);
    for( int i=0; i<THREAD_COUNT; i++ ) {
      co.submit(new NBHMLFeeder(nbhml, PER_CNT, barrier, i*PER_CNT));
    }
    for( int retCount = 0; retCount < THREAD_COUNT; retCount++ ) {
      co.take();
    }
    ex.shutdown();

    assertEquals("values().size()", ITEM_COUNT, nbhml.values().size());
    assertEquals("entrySet().size()", ITEM_COUNT, nbhml.entrySet().size());
    int itemCount = 0;
    for( TestKey K : nbhml.values() )
      itemCount++;
    assertEquals("values().iterator() count", ITEM_COUNT, itemCount);
  }

  // --- NBHMLFeeder ---
  // Class to be called from another thread, to get concurrent installs into
  // the table.
  static private class NBHMLFeeder implements Callable<Object> {
    static private final Random _rand = new Random(System.currentTimeMillis());
    private final NonBlockingHashMapLong<TestKey> _map;
    private final int _count;
    private final CyclicBarrier _barrier;
    private final long _offset;
    public NBHMLFeeder(final NonBlockingHashMapLong<TestKey> map, final int count, final CyclicBarrier barrier, final long offset) {
      _map = map;
      _count = count;
      _barrier = barrier;
      _offset = offset;
    }
    public Object call() throws Exception {
      _barrier.await();         // barrier, to force racing start
      for( long j=0; j<_count; j++ )
        _map.put(j+_offset, new TestKey(_rand.nextLong(),_rand.nextInt (), (short) _rand.nextInt(Short.MAX_VALUE)));
      return null;
    }
  }

  // --- TestKey ---
  // Funny key tests all sorts of things, has a pre-wired hashCode & equals.
  static private final class TestKey {
    public final int  _type;
    public final long _id;
    public final int  _hash;
    public TestKey(final long id, final int type, int hash) {
      _id = id;
      _type = type;
      _hash = hash;
    }
    public int hashCode() { return _hash;  }
    public boolean equals(Object object) {
      if (null == object) return false;
      if (object == this) return true;
      if (object.getClass() != this.getClass()) return false;
      final TestKey other = (TestKey) object;
      return (this._type == other._type && this._id == other._id);
    }
    public String toString() { return String.format("%s:%d,%d,%d", getClass().getSimpleName(), _id, _type, _hash);  }
  }

  // --- Customer Test Case 3 ------------------------------------------------
  private TestKeyFeeder getTestKeyFeeder() {
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
  static private class TestKeyFeeder {
    private final Hashtable<Integer, List<TestKey>> _items = new Hashtable<Integer, List<TestKey>>();
    private int _size = 0;
    public int size() { return _size;  }
    // Put items into the hashtable, sorted by 'type' into LinkedLists.
    public void checkedPut(final long id, final int type, final int hash) {
      _size++;
      final TestKey item = new TestKey(id, type, hash);
      if( !_items.containsKey(type) )
        _items.put(type, new LinkedList<TestKey>());
      _items.get(type).add(item);
    }

    public NonBlockingHashMapLong<TestKey> getMapMultithreaded() throws InterruptedException, ExecutionException {
      final int threadCount = _items.keySet().size();
      final NonBlockingHashMapLong<TestKey> map = new NonBlockingHashMapLong<TestKey>();

      // use a barrier to open the gate for all threads at once to avoid rolling start and no actual concurrency
      final CyclicBarrier barrier = new CyclicBarrier(threadCount);
      final ExecutorService ex = Executors.newFixedThreadPool(threadCount);
      final CompletionService<Integer> co = new ExecutorCompletionService<Integer>(ex);
      for( Integer type : _items.keySet() ) {
        // A linked-list of things to insert
        List<TestKey> items = _items.get(type);
        TestKeyFeederThread feeder = new TestKeyFeederThread(type, items, map, barrier);
        co.submit(feeder);
      }

      // wait for all threads to return
      int itemCount = 0;
      for( int retCount = 0; retCount < threadCount; retCount++ ) {
        final Future<Integer> result = co.take();
        itemCount += result.get();
      }
      ex.shutdown();
      return map;
    }
  }

  // --- TestKeyFeederThread
  static private class TestKeyFeederThread implements Callable<Integer> {
    private final int _type;
    private final NonBlockingHashMapLong<TestKey> _map;
    private final List<TestKey> _items;
    private final CyclicBarrier _barrier;
    public TestKeyFeederThread(final int type, final List<TestKey> items, final NonBlockingHashMapLong<TestKey> map, final CyclicBarrier barrier) {
      _type = type;
      _map = map;
      _items = items;
      _barrier = barrier;
    }

    public Integer call() throws Exception {
      _barrier.await();
      int count = 0;
      for( TestKey item : _items ) {
        if (_map.contains(item._id)) {
          System.err.printf("COLLISION DETECTED: %s exists\n", item.toString());
        }
        final TestKey exists = _map.putIfAbsent(item._id, item);
        if (exists == null) {
          count++;
        } else {
          System.err.printf("COLLISION DETECTED: %s exists as %s\n", item.toString(), exists.toString());
        }
      }
      return count;
    }
  }

  // ---
  public void testNonBlockingHashMapIteratorMultithreaded() throws InterruptedException, ExecutionException {
    TestKeyFeeder feeder = getTestKeyFeeder();
    final int itemCount = feeder.size();

    // validate results
    final NonBlockingHashMapLong<TestKey> items = feeder.getMapMultithreaded();
    assertEquals("size()", itemCount, items.size());

    assertEquals("values().size()", itemCount, items.values().size());

    assertEquals("entrySet().size()", itemCount, items.entrySet().size());

    int iteratorCount = 0;
    for( TestKey m : items.values() )
      iteratorCount++;
    // sometimes a different result comes back the second time
    int iteratorCount2 = 0;
    for( Iterator<TestKey> it = items.values().iterator(); it.hasNext(); ) {
      iteratorCount2++;
      it.next();
    }
    assertEquals("iterator counts differ", iteratorCount, iteratorCount2);
    assertEquals("values().iterator() count", itemCount, iteratorCount);
  }

}
