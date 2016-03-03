/*
 * Written by Cliff Click and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 * Big Chunks of code shamelessly copied from Doug Lea's test harness which is also public domain.
 */


import java.io.*;
import org.cliffc.high_scale_lib.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.lang.reflect.*;

public class perf_hash_test extends Thread {
  static int _read_ratio, _gr, _pr;
  static int _thread_min, _thread_max, _thread_incr;
  static int _table_size;
  static int _map_impl;

  static ConcurrentMap<String,String> make_map( int impl ) {
    switch( impl ) {
    case 1: return null; //new Hashtable<String,String>(0);
    case 2: return null; // new CliffWrapHerlihy(); // was a non-blocking HashSet implementation from Maurice Herlihy
    case 3: return new ConcurrentHashMap<String,String>(16,0.75f,  16); // force to   16 striping
    case 4: return new ConcurrentHashMap<String,String>(16,0.75f, 256); // force to  256 striping
    case 5: return new ConcurrentHashMap<String,String>(16,0.75f,4096); // force to 4096 striping
    case 6: return new NonBlockingHashMap<String,String>();
    default: throw new Error("Bad imple");
    }
  }
  static String names[] = {
    "ALL",
    "HashTable",
    "HerlihyHashSet",
    "CHM_16",
    "CHM_256",
    "CHM_4096",
    "NBHashMap",
  };


  static String KEYS[];
  static volatile boolean _start;
  static volatile boolean _stop;
  static final int NUM_CPUS = Runtime.getRuntime().availableProcessors();

  static int check( String arg, String msg, int lower, int upper ) throws Exception {
    return check( Integer.parseInt(arg), msg, lower, upper );
  }
  static int check( int x, String msg, int lower, int upper ) throws Exception {
    if( x < lower || x > upper )
      throw new Error(msg+" must be from "+lower+" to "+upper);
    return x;
  }

  public static void main( String args[] ) throws Exception {
    // Parse args
    try { 
      _read_ratio   = check( args[0], "read%", 0, 100 );
      _thread_min   = check( args[1], "thread_min", 1, 100000 );
      _thread_max   = check( args[2], "thread_max", 1, 100000 );
      _thread_incr  = check( args[3], "thread_incr", 1, 100000 );
      _table_size   = check( args[4], "table_size", 1, 100000000 );
      _map_impl     = check( args[5], "implementation", -1, names.length );

      _gr = (_read_ratio<<20)/100;
      _pr = (((1<<20) - _gr)>>1) + _gr;

      int trips = (_thread_max - _thread_min)/_thread_incr;
      _thread_max = trips*_thread_incr + _thread_min;

    } catch( Exception e ) {
      System.out.println("Usage: perf_hash_test read%[0=churn test] thread-min thread-max thread-increment hash_table_size impl[All=0,Hashtable=1,HerlihyHashSet=2,CHM_16=3,CHM_256=4,CHM_4096=5,NonBlockingHashMap=6]");
      throw e;
    }
    
    System.out.print(  _read_ratio+"% gets, "+ 
                       ((100-_read_ratio)>>1)+"% inserts, "+ 
                       ((100-_read_ratio)>>1)+"% removes, " +
                       "table_size="+_table_size);
    if( _read_ratio==0 )
      System.out.print(" -- churn");
    String name = _map_impl == -1 ? "Best" : names[_map_impl];
    System.out.println(" "+name);
    System.out.println("Threads from "+_thread_min+" to "+_thread_max+" by "+_thread_incr);

    // Do some warmup
    int keymax = 1;
    while( keymax < _table_size ) keymax<<=1;
    if( _read_ratio == 0 ) keymax = 1024*1024; // The churn test uses a large key set
    KEYS = new String[keymax];
    int [] histo = new int[64];
    for( int i=0; i<KEYS.length; i++ ) {
      KEYS[i] = String.valueOf(i) + "abc" + String.valueOf(i*17+123);
      histo[KEYS[i].hashCode() >>>(32-6)]++;
    }
    // verify good key spread to help ConcurrentHashMap
    //for( int i=0; i<histo.length; i++ ) 
    //  System.out.print(" "+histo[i]);

    System.out.println("Warmup -variance: ");
    run_till_stable(Math.min(_thread_min,2),/*extra warmup round for churn*/_read_ratio==0 ? 2 : 1);

    // Now do the real thing
    System.out.print("==== Counter  Threads   Trial: ");
    int num_trials = 7;         // Number of Trials
    for( int i=0; i<num_trials; i++ )
      System.out.printf(" %3d       ",i);
    System.out.println("   Avg      Stddev");
    for( int i=_thread_min; i<=_thread_max; i += _thread_incr ) 
      run_till_stable( i, num_trials );
  }

  static void run_till_stable( int num_threads, int num_trials ) throws Exception {
    if( _map_impl > 0 ) {
      run_till_stable(num_threads,num_trials,_map_impl);
    } else if( _map_impl == 0 ) {
      for( int i=1; i<names.length; i++ )
        run_till_stable(num_threads,num_trials,i);
    } else {
      run_till_stable(num_threads,num_trials,4);
      run_till_stable(num_threads,num_trials,6);
    }
  }

  static void run_till_stable( int num_threads, int num_trials, int impl ) throws Exception {
    ConcurrentMap<String,String> HM = make_map(impl);
    if( HM == null ) return;
    String name = names[impl];
    System.out.printf("=== %10.10s  %3d",name,num_threads);

    // Quicky sanity check
    for( int i=0; i<100; i++ ) {
      HM.put(KEYS[i],KEYS[i]);
      for( int j=0; j<i; j++ ) {
        if( HM.get(KEYS[j]) != KEYS[j] ) {
          throw new Error("Broken table, put "+i+" but cannot find #"+j);
        }
      }
    }

    long[] trials = new long[num_trials]; // Number of trials
    long total = 0;

    for( int j=0; j<trials.length; j++ ) {
      long[] ops = new long[num_threads];
      long[] nanos = new long[num_threads];
      long millis = run_once(num_threads,HM,ops,nanos);
      long sum_ops = 0;
      long sum_nanos = 0;
      for( int i=0; i<num_threads; i++ ) {
        sum_ops += ops[i];
        sum_nanos += nanos[i];
      }
      long ops_per_sec = (sum_ops*1000L)/millis;
      trials[j] = ops_per_sec;
      total += ops_per_sec;
      if( j == 0 ) 
        System.out.printf("  cnts/sec=");
      System.out.printf(" %10d",ops_per_sec);

      // Note: sum of nanos does not mean much if there are more threads than cpus
      //System.out.printf("+-%f%%",(ops_per_sec - ops_per_sec_n)*100.0/ops_per_sec);
      if( HM instanceof NonBlockingHashMap ) {
        long reprobes = ((NonBlockingHashMap)HM).reprobes();
        if( reprobes > 0 ) 
          System.out.printf("(%5.2f)",(double)reprobes/(double)sum_ops);
      }

    }

    if( trials.length > 2 ) {
      // Toss out low & high
      int lo=0;
      int hi=0;
      for( int j=1; j<trials.length; j++ ) {
        if( trials[lo] < trials[j] ) lo=j;
        if( trials[hi] > trials[j] ) hi=j;
      }
      total -= (trials[lo]+trials[hi]);
      trials[lo] = trials[trials.length-1];
      trials[hi] = trials[trials.length-2];
      // Print avg,stddev
      long avg = total/(trials.length-2);
      long stddev = compute_stddev(trials,trials.length-2);
      long p = stddev*100/avg;  // std-dev as a percent

      if( trials.length-2 > 2 ) {
        // Toss out low & high
        lo=0;
        hi=0;
        for( int j=1; j<trials.length-2; j++ ) {
          if( trials[lo] < trials[j] ) lo=j;
          if( trials[hi] > trials[j] ) hi=j;
        }
        total -= (trials[lo]+trials[hi]);
        trials[lo] = trials[trials.length-2-1];
        trials[hi] = trials[trials.length-2-2];
        // Print avg,stddev
        avg = total/(trials.length-2-2);
        stddev = compute_stddev(trials,trials.length-2-2);
        p = stddev*100/avg;  // std-dev as a percent
      } 
      System.out.printf(" %10d",avg);
      System.out.printf(" (+/-%2d%%)  %d",p,HM.size());
    }
    System.out.println();
  }

  static long compute_stddev(long[] trials, int len) {
    double sum = 0;
    double squ = 0.0;
    for( int i=0; i<len; i++ ) {
      double d = (double)trials[i];
      sum += d;
      squ += d*d;
    }
    double x = squ - sum*sum/len;
    double stddev = Math.sqrt(x/(len-1));
    return (long)stddev;
  }

  // Worker thread fields
  final int _tnum;
  final ConcurrentMap<String,String> _hash; // Shared hashtable
  final long[] _ops;
  final long[] _nanos;
  perf_hash_test( int tnum, ConcurrentMap<String,String> HM, long[] ops, long[] nanos ) { _tnum = tnum; _hash = HM; _ops = ops; _nanos = nanos; }

  static long run_once( int num_threads, ConcurrentMap<String,String> HM, long[] ops, long[] nanos ) throws Exception {
    Random R = new Random();
    _start = false;
    _stop = false;
    
    HM.put("Cliff","Cliff");
    HM.remove("Cliff");

    int sz = HM.size();
    int xsz=0;
    while( sz+1024 < _table_size ) {
      int idx = R.nextInt();
      for( int i=0; i<1024; i++ ) {
        String key = KEYS[idx&(KEYS.length-1)];
        HM.put(key,key);
        idx++;
      }
      sz = HM.size();
    }

    while( sz < ((_table_size>>1)+(_table_size>>3)) ) {
      int trip = 0;
      int idx = R.nextInt();
      while( true ) {
        String key = KEYS[idx&(KEYS.length-1)];
        if( sz < _table_size ) {
          if( HM.put(key,key) == null ) { sz++; break; }
        } else {
          if( HM.remove(key ) != null ) { sz--; break; }
        }
        idx++;
        if( (trip & 15)==15 ) idx = R.nextInt();
        if( trip++ > 1024*1024 ) {
          if( trip > 1024*1024+100 ) 
            throw new Exception("barf trip "+sz+" "+HM.size()+" numkeys="+KEYS.length);
          System.out.println(key);
        }
      }
    }

    if( sz != HM.size() ) {
      throw new Error("size does not match table contents sz="+sz+" size()="+HM.size());
    }

    // Launch threads
    perf_hash_test thrs[] = new perf_hash_test[num_threads];
    for( int i=0; i<num_threads; i++ )
      thrs[i] = new perf_hash_test(i, HM, ops, nanos);
    for( int i=0; i<num_threads; i++ )
      thrs[i].start();
    // Run threads
    long start = System.currentTimeMillis();
    _start = true;
    try { Thread.sleep(2000); } catch( InterruptedException e ){}
    _stop = true;
    long stop = System.currentTimeMillis();
    long millis = stop-start;

    for( int i=0; i<num_threads; i++ )
      thrs[i].join();
    return millis;
  }

  // What a worker thread does
  public void run() {
    while( !_start )            // Spin till Time To Go
      try { Thread.sleep(1); } catch( Exception e ){}

    long nano1 = System.nanoTime();

    int total = 0;
    if( _read_ratio == 0 ) {
      total = run_churn();
    } else {
      if( _hash instanceof NonBlockingHashMap ) {
        total = run_normal( (NonBlockingHashMap) _hash);
      } else if( _hash instanceof ConcurrentHashMap ) total = run_normal( (ConcurrentHashMap) _hash);
      else total = run_normal(_hash);
    }

    _ops[_tnum] = total;
    long nano2 = System.nanoTime();
    _nanos[_tnum] = (nano2-nano1);
  }

  // Force a large turnover of live keys, while keeping the total live-set
  // low.  10 keys kept alive per thread, out of a set of a million or so.
  // constantly churned, so we constantly need to 'cleanse' the table to flush
  // old entries.
  public int run_churn() {
    int reprobe = System.identityHashCode(Thread.currentThread());
    int idx = reprobe;

    int get_ops = 0;
    int put_ops = 0;
    int del_ops = 0;
    while( !_stop ) {
      // Insert a key 10 probes in the future,
      // remove a key  0 probes in the future,
      // Net result is the thread keeps 10 random keys in table
      String key1 = KEYS[(idx+reprobe*10) & (KEYS.length-1)];
      _hash.put(key1,key1);
      put_ops++;

      // Remove a key  0 probes in the future
      String key2 = KEYS[(idx+reprobe* 0) & (KEYS.length-1)];
      _hash.remove(key2);
      del_ops++;

      idx += reprobe;
    }

    // We stopped; report results into shared result structure
    return get_ops+put_ops+del_ops;
  }

  public int run_normal( NonBlockingHashMap<String,String> hm ) {
    SimpleRandom R = new SimpleRandom();

    int get_ops = 0;
    int put_ops = 0;
    int del_ops = 0;
    while( !_stop ) {
      int x = R.nextInt()&((1<<20)-1);
      String key = KEYS[R.nextInt()&(KEYS.length-1)];
      if( x < _gr ) {
        get_ops++;
        String val = hm.get(key);
        if( val != null && !val.equals(key) ) throw new IllegalArgumentException("Mismatched key="+key+" and val="+val);
      } else if( x < _pr ) {
        put_ops++;
	hm.putIfAbsent( key, key );
      } else {
        del_ops++;
        hm.remove( key );
      }
    }
    // We stopped; report results into shared result structure
    return get_ops+put_ops+del_ops;
  }

  public int run_normal( ConcurrentHashMap<String,String> hm ) {
    SimpleRandom R = new SimpleRandom();

    int get_ops = 0;
    int put_ops = 0;
    int del_ops = 0;
    while( !_stop ) {
      int x = R.nextInt()&((1<<20)-1);
      String key = KEYS[R.nextInt()&(KEYS.length-1)];
      if( x < _gr ) {
        get_ops++;
        String val = hm.get(key);
        if( val != null && !val.equals(key) ) throw new IllegalArgumentException("Mismatched key="+key+" and val="+val);
      } else if( x < _pr ) {
        put_ops++;
	hm.putIfAbsent( key, key );
      } else {
        del_ops++;
        hm.remove( key );
      }
    }
    // We stopped; report results into shared result structure
    return get_ops+put_ops+del_ops;
  }

  public int run_normal( ConcurrentMap<String,String> hm ) {
    SimpleRandom R = new SimpleRandom();

    int get_ops = 0;
    int put_ops = 0;
    int del_ops = 0;
    while( !_stop ) {
      int x = R.nextInt()&((1<<20)-1);
      String key = KEYS[R.nextInt()&(KEYS.length-1)];
      if( x < _gr ) {
        get_ops++;
        String val = hm.get(key);
        if( val != null && !val.equals(key) ) throw new IllegalArgumentException("Mismatched key="+key+" and val="+val);
      } else if( x < _pr ) {
        put_ops++;
	hm.putIfAbsent( key, key );
      } else {
        del_ops++;
        hm.remove( key );
      }
    }
    // We stopped; report results into shared result structure
    return get_ops+put_ops+del_ops;
  }

  // Fairly fast random numbers
  public static final class SimpleRandom {
    private final static long multiplier = 0x5DEECE66DL;
    private final static long addend = 0xBL;
    private final static long mask = (1L << 48) - 1;
    static final AtomicLong seq = new AtomicLong( -715159705);
    private long seed;
    SimpleRandom(long s) { seed = s; }
    SimpleRandom() { seed = System.nanoTime() + seq.getAndAdd(129); }
    public void setSeed(long s) { seed = s; }
    public int nextInt() { return next(); }
    public int next() {
      long nextseed = (seed * multiplier + addend) & mask;
      seed = nextseed;
      return ((int)(nextseed >>> 17)) & 0x7FFFFFFF;
    }
  }

}
