package org.jctools.maps.nbhm_test;

import java.util.AbstractSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jctools.maps.NonBlockingHashSet;
import org.jctools.maps.NonBlockingSetInt;

/*
 * Written by Cliff Click and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 * Big Chunks of code shamelessly copied from Doug Lea's test harness which is also public domain.
 */

public class perf_set_test extends Thread {

    static int _read_ratio, _gr, _pr;
    static int _thread_min, _thread_max, _thread_incr;
    static int _table_size;

    static int KEYMAX = 1000;
    static Integer KEYS[];
    static volatile boolean _start;
    static volatile boolean _stop;
    static final String _names[] = { "All", "CHMKeySet", "NBHashSet", "NBSetInt" };

    static int check(String arg, String msg, int lower, int upper) {
        return check(Integer.parseInt(arg), msg, lower, upper);
    }

    static int check(int x, String msg, int lower, int upper) {
        if (x < lower || x > upper)
            throw new IllegalArgumentException(msg + " must be from " + lower + " to " + upper);
        return x;
    }

    public static void main(String args[]) {
        if (args.length == 0) {
            args = new String[] { "75", "2", "8", "2", "100000", "0" };
        }
        // Parse args
        int impl;
        try {
            _read_ratio = check(args[0], "read%", 0, 100);
            _thread_min = check(args[1], "thread_min", 1, 100000);
            _thread_max = check(args[2], "thread_max", 1, 100000);
            _thread_incr = check(args[3], "thread_incr", 1, 100000);
            _table_size = check(args[4], "table_size", 1, 100000000);
            impl = check(args[5], "impl", 0, _names.length);

            _gr = (_read_ratio << 20) / 100;
            _pr = (((1 << 20) - _gr) >> 1) + _gr;

            int trips = (_thread_max - _thread_min) / _thread_incr;
            _thread_max = trips * _thread_incr + _thread_min;

        }
        catch (RuntimeException e) {
            System.out.print(
                    "Usage: perf_set_test read%[0=churn test] thread-min thread-max thread-increment set_size impl[");
            for (String s : _names)
                System.out.print(s + ",");
            System.out.println("]");
            throw e;
        }

        System.out.print(_read_ratio + "% gets, " + ((100 - _read_ratio) >> 1) + "% inserts, "
                + ((100 - _read_ratio) >> 1) + "% removes, " + "table_size=" + _table_size);
        if (_read_ratio == 0)
            System.out.print(" -- churn");

        String name = _names[impl];
        System.out.println(" " + name);
        System.out.println("Threads from " + _thread_min + " to " + _thread_max + " by " + _thread_incr);

        // Do some warmup. Make an array of Integers as Keys
        KEYMAX = 1;
        while (KEYMAX < _table_size)
            KEYMAX <<= 1;
        if (_read_ratio == 0)
            KEYMAX = 1024 * 1024; // The churn test uses a large key set
        KEYS = new Integer[KEYMAX];
        for (int i = 0; i < KEYMAX; i++)
            KEYS[i] = i;

        System.out.println("Warmup -variance: ");
        run(Math.min(_thread_min, 2), 1, impl);

        // Now do the real thing
        System.out.print("==== Counter  Threads   Trial: ");
        int num_trials = 7; // Number of Trials
        for (int i = 0; i < num_trials; i++)
            System.out.printf(" %3d       ", i);
        System.out.println("   Avg      Stddev");
        for (int i = _thread_min; i <= _thread_max; i += _thread_incr)
            run(i, num_trials, impl);
    }

    static void run(int num_threads, int num_trials, int impl) {
        if (impl == 0) {
            for (int i = 1; i < _names.length; i++)
                run_till_stable(num_threads, num_trials, i);
        }
        else {
            run_till_stable(num_threads, num_trials, impl);
        }
    }

    static void run_till_stable(int num_threads, int num_trials, int impl) {
        Set<Integer> AS;
        switch (impl) {
        case 1:
            AS = ConcurrentHashMap.newKeySet();
            break;
        case 2:
            AS = new NonBlockingHashSet<Integer>();
            break;
        case 3:
            AS = new NonBlockingSetInt();
            break;
        default:
            throw new Error("unimplemented");
        }
        System.out.printf("=== %10.10s  %3d  cnts/sec=", _names[impl], num_threads);

        // Quicky sanity check
        for (int i = 0; i < 100; i++) {
            AS.add(KEYS[i]);
            for (int j = 0; j < i; j++) {
                if (!AS.contains(KEYS[j])) {
                    throw new Error("Broken table, put " + i + " but cannot find #" + j);
                }
            }
        }

        long[] trials = new long[num_trials]; // Number of trials
        long total = 0;

        for (int j = 0; j < trials.length; j++) {
            long[] ops = new long[num_threads];
            long[] nanos = new long[num_threads];
            long millis = run_once(num_threads, AS, ops, nanos);
            long sum = 0;
            for (int i = 0; i < num_threads; i++)
                sum += ops[i];
            long ops_per_sec = (sum * 1000L) / millis;
            trials[j] = ops_per_sec;
            total += ops_per_sec;
            System.out.printf(" %10d", ops_per_sec);
        }

        if (trials.length > 2) {
            // Toss out low & high
            int lo = 0;
            int hi = 0;
            for (int j = 1; j < trials.length; j++) {
                if (trials[lo] < trials[j])
                    lo = j;
                if (trials[hi] > trials[j])
                    hi = j;
            }
            total -= (trials[lo] + trials[hi]);
            trials[lo] = trials[trials.length - 1];
            trials[hi] = trials[trials.length - 2];
            // Print avg,stddev
            long avg = total / (trials.length - 2);
            long stddev = compute_stddev(trials, trials.length - 2);
            long p = stddev * 100 / avg; // std-dev as a percent

            if (trials.length - 2 > 2) {
                // Toss out low & high
                lo = 0;
                hi = 0;
                for (int j = 1; j < trials.length - 2; j++) {
                    if (trials[lo] < trials[j])
                        lo = j;
                    if (trials[hi] > trials[j])
                        hi = j;
                }
                total -= (trials[lo] + trials[hi]);
                trials[lo] = trials[trials.length - 2 - 1];
                trials[hi] = trials[trials.length - 2 - 2];
                // Print avg,stddev
                avg = total / (trials.length - 2 - 2);
                stddev = compute_stddev(trials, trials.length - 2 - 2);
                p = stddev * 100 / avg; // std-dev as a percent
            }
            System.out.printf(" %10d", avg);
            System.out.printf(" (+/-%2d%%)  %d", p, AS.size());
        }
        System.out.println();
    }

    static long compute_stddev(long[] trials, int len) {
        double sum = 0;
        double squ = 0.0;
        for (int i = 0; i < len; i++) {
            double d = (double) trials[i];
            sum += d;
            squ += d * d;
        }
        double x = squ - sum * sum / len;
        double stddev = Math.sqrt(x / (len - 1));
        return (long) stddev;
    }

    // Worker thread fields
    final int _tnum;
    final long[] _ops;
    final long[] _nanos;
    final Set<Integer> _set;

    public perf_set_test() {
        _tnum = 0;
        _ops = null;
        _nanos = null;
        _set = null;
    }

    perf_set_test(int tnum, Set set, long[] ops, long[] nanos) {
        _tnum = tnum;
        _set = set;
        _ops = ops;
        _nanos = nanos;
    }

    static long run_once(int num_threads, Set AS, long[] ops, long[] nanos) {
        Random R = new Random();
        _start = false;
        _stop = false;

        // Fill the Set with data
        AS.add(0);
        AS.remove(0);

        int sz = AS.size();
        while (sz + 1024 < _table_size) {
            int idx = R.nextInt();
            for (int i = 0; i < 1024; i++) {
                int k = idx & (KEYMAX - 1);
                AS.add(k);
                idx++;
            }
            sz = AS.size();
        }

        while (sz < ((_table_size >> 1) + (_table_size >> 3))) {
            int trip = 0;
            int idx = R.nextInt();
            while (true) {
                int k = idx & (KEYMAX - 1);
                if (sz < _table_size) {
                    if (AS.add(k)) {
                        sz++;
                        break;
                    }
                }
                else {
                    if (AS.remove(k)) {
                        sz--;
                        break;
                    }
                }
                idx++;
                if ((trip & 15) == 15)
                    idx = R.nextInt();
                if (trip++ > 1024 * 1024) {
                    if (trip > 1024 * 1024 + 100)
                        throw new RuntimeException(
                                "barf trip " + sz + " " + AS.size() + " numkeys=" + KEYMAX);
                    System.out.println(k);
                }
            }
        }

        if (sz != AS.size()) {
            throw new Error("size does not match table contents sz=" + sz + " size()=" + AS.size());
        }

        // Launch threads
        perf_set_test thrs[] = new perf_set_test[num_threads];
        for (int i = 0; i < num_threads; i++)
            thrs[i] = new perf_set_test(i, AS, ops, nanos);
        for (int i = 0; i < num_threads; i++)
            thrs[i].start();
        // Run threads
        long start = System.currentTimeMillis();
        _start = true;
        try {
            Thread.sleep(2000);
        }
        catch (InterruptedException e) {
            /* empty */}
        // Stop and collect threads
        _stop = true;
        long stop = System.currentTimeMillis();
        long millis = stop - start;
        for (int i = 0; i < num_threads; i++)
            try {
                thrs[i].join();
            }
            catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        return millis;
    }

    // What a worker thread does
    public void run() {
        if (_read_ratio == 0) {
            if (_set instanceof NonBlockingSetInt)
                throw new Error("unimplemented");
            else
                run_churn_int((AbstractSet<Integer>) _set);
        }
        else {
            if (_set instanceof NonBlockingSetInt)
                run_normal_prim((NonBlockingSetInt) _set);
            else
                run_normal_int((Set<Integer>) _set);
        }
    }

    // Force a large turnover of live keys, while keeping the total live-set
    // low. 10 keys kept alive per thread, out of a set of a million or so.
    // constantly churned, so we constantly need to 'cleanse' the table to flush
    // old entries.
    public void run_churn_int(Set<Integer> as) {
        int reprobe = System.identityHashCode(Thread.currentThread());
        int idx = reprobe;

        while (!_start) // Spin till Time To Go
            try {
                Thread.sleep(1);
            }
            catch (Exception e) {
                /* empty */}

        long nano1 = System.nanoTime();
        int get_ops = 0;
        int put_ops = 0;
        int del_ops = 0;
        while (!_stop) {
            // Insert a key 10 probes in the future,
            // remove a key 0 probes in the future,
            // Net result is the thread keeps 10 random keys in table
            int k1 = (idx + reprobe * 10) & (KEYMAX - 1);
            as.add(k1);
            put_ops++;

            // Remove a key 0 probes in the future
            int k2 = idx & (KEYMAX - 1);
            as.remove(k2);
            del_ops++;

            idx += reprobe;
        }

        // We stopped; report results into shared result structure
        long nano2 = System.nanoTime();
        int total = get_ops + put_ops + del_ops;
        _ops[_tnum] = total;
        _nanos[_tnum] = (nano2 - nano1);
    }

    public void run_normal_prim(NonBlockingSetInt prim) {
        SimpleRandom R = new SimpleRandom();
        while (!_start) // Spin till Time To Go
            try {
                Thread.sleep(1);
            }
            catch (Exception e) {
                /* empty */}

        long nano1 = System.nanoTime();
        int get_ops = 0;
        int put_ops = 0;
        int del_ops = 0;
        while (!_stop) {
            int x = R.nextInt() & ((1 << 20) - 1);
            int k = R.nextInt() & (KEYMAX - 1);
            if (x < _gr) {
                get_ops++;
                prim.contains(k);
            }
            else if (x < _pr) {
                put_ops++;
                prim.add(k);
            }
            else {
                del_ops++;
                prim.remove(k);
            }
        }
        // We stopped; report results into shared result structure
        long nano2 = System.nanoTime();
        int total = get_ops + put_ops + del_ops;
        _ops[_tnum] = total;
        _nanos[_tnum] = (nano2 - nano1);
    }

    public void run_normal_int(Set<Integer> as) {
        SimpleRandom R = new SimpleRandom();
        while (!_start) // Spin till Time To Go
            try {
                Thread.sleep(1);
            }
            catch (Exception e) {
                /* empty */}

        long nano1 = System.nanoTime();
        int get_ops = 0;
        int put_ops = 0;
        int del_ops = 0;
        while (!_stop) {
            int x = R.nextInt() & ((1 << 20) - 1);
            int k = R.nextInt() & (KEYMAX - 1);
            if (x < _gr) {
                get_ops++;
                as.contains(KEYS[k]);
            }
            else if (x < _pr) {
                put_ops++;
                as.add(KEYS[k]);
            }
            else {
                del_ops++;
                as.remove(KEYS[k]);
            }
        }
        // We stopped; report results into shared result structure
        long nano2 = System.nanoTime();
        int total = get_ops + put_ops + del_ops;
        _ops[_tnum] = total;
        _nanos[_tnum] = (nano2 - nano1);
    }

    // Fairly fast random numbers
    static final class SimpleRandom {
        private final static long multiplier = 0x5DEECE66DL;
        private final static long addend = 0xBL;
        private final static long mask = (1L << 48) - 1;
        static final AtomicLong seq = new AtomicLong(-715159705);
        private long seed;

        SimpleRandom() {
            seed = System.nanoTime() + seq.getAndAdd(129);
        }

        public int nextInt() {
            return next();
        }

        public int next() {
            long nextseed = (seed * multiplier + addend) & mask;
            seed = nextseed;
            return ((int) (nextseed >>> 17)) & 0x7FFFFFFF;
        }
    }

}
