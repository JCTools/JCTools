package org.jctools.maps.nbhm_test.jmh;

import org.jctools.maps.NonBlockingHashMap;
import org.jctools.maps.nbhm_test.SimpleRandom;
import org.jctools.util.Pow2;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.ThreadParams;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode({Mode.Throughput})
@Warmup(iterations = 2, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 6, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class ConcurrentMapThroughput {

    /*
     * Note that in NonBlockingHashMap, puts that update an entry to the same reference are
     * short circuited and do not mutate the hash table. Such operations are equivalent to a get.
     */

    @Param(value = {"NonBlockingHashMap", "ConcurrentHashMap"})
    private String implementation;

    @Param(value = "50")
    private static int readRatio;

    @Param(value = "100000")
    private static int tableSize;

    private static String testData[];
    private static int _gr, _pr;

    private Map<String, String> map;

    @Setup(Level.Trial)
    public void createMap(ThreadParams threads) {
        validateParameters();
        createImplementation(threads);
        setRatios();
    }

    private void validateParameters() {
        if (readRatio < 0 || readRatio > 100) {
            throw new IllegalArgumentException("readRatio must be a value between 0 and 100");
        }
        if (tableSize < 100 || tableSize > Pow2.MAX_POW2) {
            throw new IllegalArgumentException("tableSize must be a value between 100 and " + Pow2.MAX_POW2);
        }
    }

    private void createImplementation(ThreadParams threads) {
        if ("ConcurrentHashMap".equalsIgnoreCase(implementation)) {
            map = new ConcurrentHashMap<String, String>(16, 0.75f, 16);
        } else if ("NonBlockingHashMap".equalsIgnoreCase(implementation)) {
            map = new NonBlockingHashMap<String, String>();
        } else if ("HashMap".equalsIgnoreCase(implementation)){
            map = threads.getGroupCount() == 1 ?
                    new HashMap<String, String>() : Collections.synchronizedMap(new HashMap<String, String>());
        } else{
            throw new IllegalArgumentException("Unsupported map: " + implementation);
        }
    }

    private void setRatios() {
        _gr = (readRatio << 20) / 100;
        _pr = (((1 << 20) - _gr) >> 1) + _gr;
    }

    @Setup(Level.Trial)
    public void prepareMap() {
        testData = new String[Pow2.roundToPowerOfTwo(tableSize)];
        for (int i = 0; i < testData.length; i++) {
            testData[i] = String.valueOf(i) + "abc" + String.valueOf(i * 17 + 123);
        }

        Random rand = new Random();

        int sz = map.size();
        while (sz + 1024 < tableSize) {
            int idx = rand.nextInt();
            for (int i = 0; i < 1024; i++) {
                String key = testData[idx & (testData.length - 1)];
                map.put(key, key);
                idx++;
            }
            sz = map.size();
        }

        while (sz < ((tableSize >> 1) + (tableSize >> 3))) {
            int trip = 0;
            int idx = rand.nextInt();
            while (true) {
                String key = testData[idx & (testData.length - 1)];
                if (sz < tableSize) {
                    if (map.put(key, key) == null) {
                        sz++;
                        break;
                    }
                } else {
                    if (map.remove(key) != null) {
                        sz--;
                        break;
                    }
                }
                idx++;
                if ((trip & 15) == 15)
                    idx = rand.nextInt();
                if (trip++ > 1024 * 1024) {
                    if (trip > 1024 * 1024 + 100)
                        throw new AssertionError(
                                String.format("barf trip %d %d numkeys=%d", sz, map.size(), testData.length));
                    System.out.println(key);
                }
            }
        }

        if (sz != map.size()) {
            throw new AssertionError("size does not match table contents sz=" + sz + " size()=" + map.size());
        }
    }

    @State(Scope.Thread)
    public static class ThreadState {
        private SimpleRandom random = new SimpleRandom();
        int next() { return random.next(); }
    }

    @Benchmark
    @Threads(2)
    public String randomGetPutRemove(ThreadState state) {
        String key = testData[state.next() & (testData.length - 1)];
        int x = state.next() & ((1 << 20) - 1);
        if (x < _gr) {
            String val = map.get(key);
            if (val != null && !val.equals(key))
                throw new AssertionError("Mismatched key=" + key + " and val=" + val);
            return val;
        } else if (x < _pr) {
            return map.putIfAbsent(key, key);
        } else {
            return map.remove(key);
        }
    }

}
