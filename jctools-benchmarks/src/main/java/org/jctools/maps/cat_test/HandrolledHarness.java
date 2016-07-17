package org.jctools.maps.cat_test;

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
public class HandrolledHarness extends Thread {

  static int _thread_min, _thread_max, _thread_incr;
  static int _ctr_impl;

  static Counter make_ctr( final int impl ) {
    switch( impl ) {
    case  1: return new RaceyCounter();
    case  2: return new SyncCounter();
    case  3: return new LockCounter();
    case  4: return new AtomicCounter();
    case  5: return new UnsafeCounter();
    case  6: return new   StripeLockCounter(  8);
    case  7: return new StripeUnsafeCounter(  8);
    case  8: return new   StripeLockCounter( 64);
    case  9: return new StripeUnsafeCounter( 64);
    case 10: return new   StripeLockCounter(256);
    case 11: return new StripeUnsafeCounter(256);
    case 12: return new CATCounter();
    default:
      throw new Error("Bad imple");
    }
  }

  static volatile boolean _start;
  static volatile boolean _stop;
  //static final int NUM_CPUS = Runtime.getRuntime().availableProcessors();

  static int check( String arg, String msg, int lower, int upper ) {
    return check( Integer.parseInt(arg), msg, lower, upper );
  }
  static int check( int x, String msg, int lower, int upper ) {
    if( x < lower || x > upper )
      throw new Error(msg+" must be from "+lower+" to "+upper);
    return x;
  }

  public static void main( String args[] ) {
      if (args.length == 0) {
          args = new String[]{"2","8","2","0"};
      }

    // Parse args
    try {
      _thread_min   = check( args[0], "thread_min", 1, 100000 );
      _thread_max   = check( args[1], "thread_max", 1, 100000 );
      _thread_incr  = check( args[2], "thread_incr", 1, 100000 );
      _ctr_impl     = check( args[3], "implementation", -1, 13 );

      int trips = (_thread_max - _thread_min)/_thread_incr;
      _thread_max = trips*_thread_incr + _thread_min;

    } catch( Error e ) {
      System.out.println("Usage: harness thread-min thread-max thread-incr impl[All=0]");
      throw e;
    }
    String name = _ctr_impl == 0 ? "ALL" : (_ctr_impl==-1 ? "Best" : make_ctr(_ctr_impl).name());
    System.out.println("=====  "+name+"   =====");
    System.out.println("Threads from "+_thread_min+" to "+_thread_max+" by "+_thread_incr);

    // Do some warmup
    System.out.println("==== Warmup -variance: ");
    run_till_stable(Math.min(_thread_min,2),1);

    // Now do the real thing
    int num_trials = 7;         // Number of Trials
    System.out.print("==== Counter  Threads   Trial:");
    for( int i=0; i<num_trials; i++ )
      System.out.printf("    %3d    ",i);
    System.out.println("    Average");
    for( int i=_thread_min; i<=_thread_max; i += _thread_incr )
      run_till_stable( i, num_trials );
  }

  static void run_till_stable( int num_threads, int num_trials ) {
    if( _ctr_impl > 0 ) {
      run_till_stable(num_threads,num_trials,_ctr_impl);
    } else if( _ctr_impl == 0 ) {
      for( int impl=1;impl<13; impl++ )
        run_till_stable(num_threads,num_trials,impl);
      System.out.println();
    } else {
      run_till_stable(num_threads,num_trials,11); // big stripage Unsafe
      run_till_stable(num_threads,num_trials,12); // CAT
    }
  }

  static void run_till_stable( int num_threads, int num_trials, int impl ) {

    Counter C = make_ctr(impl);
    System.out.printf("=== %10.10s  %3d  cnts/sec=",C.name(),num_threads);
    long[] trials = new long[num_trials]; // Number of trials
    long total_ops = 0;                   // Total ops altogether
    long total_ops_sec = 0;               // Sum of ops/sec for each run

    // Run some trials
    for( int j=0; j<trials.length; j++ ) {
      long[] ops = new long[num_threads];
      long millis = run_once(num_threads,C,ops);
      long sum = 0;
      for( int i=0; i<num_threads; i++ )
        sum += ops[i];
      total_ops += sum;
      sum = sum*1000L/millis;
      trials[j] = sum;
      total_ops_sec += sum;
      System.out.printf(" %10d",sum);
    }

    // Compute nice trial results
    if( trials.length > 2 ) {
      // Toss out low & high
      int lo=0;
      int hi=0;
      for( int j=1; j<trials.length; j++ ) {
        if( trials[lo] > trials[j] ) lo=j;
        if( trials[hi] < trials[j] ) hi=j;
      }
      long total2 = total_ops_sec - (trials[lo]+trials[hi]);
      trials[lo] = trials[trials.length-1];
      trials[hi] = trials[trials.length-2];
      // Print avg,stddev
      long avg = total2/(trials.length-2);
      long stddev = compute_stddev(trials,trials.length-2);
      long p = stddev*100/avg;  // std-dev as a percent

      System.out.printf(" %10d",avg);
      System.out.printf(" (+/-%2d%%)",p);
    }

    long loss = total_ops - C.get();
    if( loss != 0 ) {
      System.out.print("  Lossage=");
      int loss_per = (int)(loss*100/total_ops);
      System.out.print(loss_per == 0 ? (""+loss) : (""+loss_per+"%"));
    }

    if( C instanceof CATCounter ) {
      CATCounter cat = (CATCounter)C;
      System.out.print(" autotable="+ cat.internal_size());
      if( loss != 0 ) cat.print();
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

  final int _tnum;
  final Counter _C;
  final long[] _ops;
  public HandrolledHarness() { _tnum=-1; _C=null; _ops=null; }
  private HandrolledHarness( int tnum, Counter C, long[] ops ) { _tnum = tnum; _C = C; _ops = ops; }

  static long run_once( int num_threads, Counter C, long[] ops ) {
    _start = false;
    _stop = false;

    // Launch threads
    HandrolledHarness thrs[] = new HandrolledHarness[num_threads];
    for( int i=0; i<num_threads; i++ ) {
      thrs[i] = new HandrolledHarness(i, C, ops);
      //int h1 = System.identityHashCode(thrs[i]);
      //int h2 = h1;
      //h2 ^= (h2>>>20) ^ (h2>>>12);
      //h2 ^= (h2>>> 7) ^ (h2>>> 4);
      //System.out.printf("%x ",h1&0xfff);
    }
    //System.out.println("");
    for( int i=0; i<num_threads; i++ )
      thrs[i].start();
    // Run threads
    long start = System.currentTimeMillis();
    _start = true;
    try { Thread.sleep(2000); } catch( InterruptedException e ){/*empty*/}
    _stop = true;
    long stop = System.currentTimeMillis();
    long millis = stop-start;

    for( int i=0; i<num_threads; i++ ) {
      try {
        thrs[i].join();
      } catch( InterruptedException e ) {/*empty*/}
    }
    return millis;
  }

  // What a worker thread does
  public void run() {
    while( !_start )            // Spin till Time To Go
      try { Thread.sleep(1); } catch( InterruptedException e ){/*empty*/}

    int ops = 0;
    while( !_stop ) {
      ops++;
      _C.add(1);
    }
    // We stopped; report results into shared result structure
    _ops[_tnum] = ops;
  }

}
