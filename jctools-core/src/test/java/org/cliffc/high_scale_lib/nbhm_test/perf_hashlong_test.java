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

public class perf_hashlong_test extends Thread {
  static int _read_ratio, _gr, _pr;
  static int _thread_min, _thread_max, _thread_incr;
  static int _table_size;

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
    String name = "NonBlockingHashMapLong";
    System.out.println(" "+name);
    System.out.println("Threads from "+_thread_min+" to "+_thread_max+" by "+_thread_incr);

    // Do some warmup
    int keymax = 1;
    while( keymax < _table_size ) keymax<<=1;
    if( _read_ratio == 0 ) keymax = 1024*1024; // The churn test uses a large key set
    KEYS = new String[keymax];
    KEYS[0] = "Cliff0";
    for( int i=1; i<KEYS.length; i++ ) {
      KEYS[i] = String.valueOf(i) + "abc" + String.valueOf(i*17+123);
    }

    System.out.println("Warmup -variance: ");
    run_till_stable(Math.min(_thread_min,2),1);

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
    NonBlockingHashMapLong<String> HM = new NonBlockingHashMapLong(true);
    String name = "NonBlockingHashMapLong";
    System.out.printf("=== %10.10s  %3d  cnts/sec=",name,num_threads);

    // Quicky sanity check
    for( int i=0; i<100; i++ ) {
      HM.put(i,KEYS[i]);
      for( int j=0; j<i; j++ ) {
        if( HM.get(j) != KEYS[j] ) {
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
      long sum = 0;
      for( int i=0; i<num_threads; i++ ) 
        sum += ops[i];
      long ops_per_sec = (sum*1000L)/millis;
      trials[j] = ops_per_sec;
      total += ops_per_sec;
      System.out.printf(" %10d",ops_per_sec);

      
      //for( int i=0; i<num_threads; i++ ) {
      //  if( nanos[i] < 1980000000 ||
      //      nanos[i] > 2010000000 ||
      //      ops[i] < 100000 )
      //    System.out.printf(" %d",ops[i]);
      //}

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
  final NonBlockingHashMapLong<String> _hash; // Shared hashtable
  final long[] _ops;
  final long[] _nanos;
  perf_hashlong_test( int tnum, NonBlockingHashMapLong<String> HM, long[] ops, long [] nanos ) { _tnum = tnum; _hash = HM; _ops = ops; _nanos = nanos; }

  static long run_once( int num_threads, NonBlockingHashMapLong<String> HM, long[] ops, long [] nanos ) throws Exception {
    Random R = new Random();
    _start = false;
    _stop = false;
    
    HM.put(0,"Cliff0");
    HM.remove(0);

    int sz = HM.size();
    int xsz=0;
    while( sz+1024 < _table_size ) {
      int idx = R.nextInt();
      for( int i=0; i<1024; i++ ) {
        int k = idx&(KEYS.length-1);
        HM.put(k,KEYS[k]);
        idx++;
      }
      sz = HM.size();
    }

    while( sz < ((_table_size>>1)+(_table_size>>3)) ) {
      int trip = 0;
      int idx = R.nextInt();
      while( true ) {
        int k = idx&(KEYS.length-1);
        String key = KEYS[k];
        if( sz < _table_size ) {
          if( HM.put(k,key) == null ) { sz++; break; }
        } else {
          if( HM.remove(k) != null ) { sz--; break; }
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
    //long nanoz = System.nanoTime();
    //System.out.println(" "+nanoz+" Create-Threads");
    perf_hashlong_test thrs[] = new perf_hashlong_test[num_threads];
    for( int i=0; i<num_threads; i++ )
      thrs[i] = new perf_hashlong_test(i, HM, ops, nanos);
    for( int i=0; i<num_threads; i++ )
      thrs[i].start();
    // Run threads
    //long nano = System.nanoTime();
    //System.out.println(" "+nano+" Start");
    long start = System.currentTimeMillis();
    _start = true;
    try { Thread.sleep(2000); } catch( InterruptedException e ){}
    _stop = true;
    long stop = System.currentTimeMillis();
    //long nanox = System.nanoTime();
    long millis = stop-start;
    //System.out.println(" "+nanox+" Stop");

    for( int i=0; i<num_threads; i++ )
      thrs[i].join();
    //long nanoy = System.nanoTime();
    //System.out.println(" "+nanoy+" Join-Done");
    return millis;
  }

  // What a worker thread does
  public void run() {
    if( _read_ratio == 0 ) {
      run_churn();
    } else {
      run_normal();
    }
  }

  // Force a large turnover of live keys, while keeping the total live-set
  // low.  10 keys kept alive per thread, out of a set of a million or so.
  // constantly churned, so we constantly need to 'cleanse' the table to flush
  // old entries.
  public void run_churn() {
    int reprobe = System.identityHashCode(Thread.currentThread());
    int idx = reprobe;

    while( !_start )            // Spin till Time To Go
      try { Thread.sleep(1); } catch( Exception e ){}

    long nano1 = System.nanoTime();
    int get_ops = 0;
    int put_ops = 0;
    int del_ops = 0;
    while( !_stop ) {
      // Insert a key 10 probes in the future,
      // remove a key  0 probes in the future,
      // Net result is the thread keeps 10 random keys in table
      int k1 = (idx+reprobe*10) & (KEYS.length-1);
      String key1 = KEYS[k1];
      _hash.put(k1,key1);
      put_ops++;

      // Remove a key  0 probes in the future
      int k2 = (idx+reprobe* 0) & (KEYS.length-1);
      String key2 = KEYS[k2];
      _hash.remove(k2);
      del_ops++;

      idx += reprobe;
    }

    // We stopped; report results into shared result structure
    long nano2 = System.nanoTime();
    int total = get_ops+put_ops+del_ops;
    _ops[_tnum] = total;
    _nanos[_tnum] = (nano2-nano1);
  }

  public void run_normal() {
    SimpleRandom R = new SimpleRandom();
    while( !_start )            // Spin till Time To Go
      try { Thread.sleep(1); } catch( Exception e ){}

    long nano1 = System.nanoTime();
    int get_ops = 0;
    int put_ops = 0;
    int del_ops = 0;
    while( !_stop ) {
      int x = R.nextInt()&((1<<20)-1);
      int k = R.nextInt()&(KEYS.length-1);
      String key = KEYS[k];
      if( x < _gr ) {
        get_ops++;
        String val = _hash.get(k);
        if( val != null && !val.equals(key) ) throw new IllegalArgumentException("Mismatched key="+key+" and val="+val);
      } else if( x < _pr ) {
        put_ops++;
	_hash.putIfAbsent( k, key );
	// An interesting version: testing get immediately after putIfAbsent.
	// Of course in a multi-threaded context it immediately throws false-positives.
        //if( _hash.putIfAbsent( key, key ) == null )
        //  if( _hash.get(key) == null )
        //    throw new Error("putIfAbsent failed to put key=" + key + " for put_ops=" + put_ops + "and getops=" +get_ops + " del_ops="+del_ops);
      } else {
        del_ops++;
        _hash.remove( k );
      }
    }
    // We stopped; report results into shared result structure
    long nano2 = System.nanoTime();
    int total = get_ops+put_ops+del_ops;
    _ops[_tnum] = total;
    _nanos[_tnum] = (nano2-nano1);
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
