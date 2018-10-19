package org.jctools.jmh.collections;

import org.jctools.maps.NonBlockingHashSet;
import org.jctools.maps.nbhm_test.SimpleRandom;
import org.jctools.sets.SingleWriterHashSet;
import org.jctools.util.Pow2;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.ThreadParams;

import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Tolstopyatov Vsevolod
 * @since 24/04/17
 */
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class ConcurrentSetThroughput {

    @Param(value = {"NonBlockingHashSet", "ConcurrentHashSet", "SingleWriterHashSet"})
    private String implementation;

    @Param(value = "75")
    private static int readRatio;

    @Param(value = "100000")
    private static int tableSize;

    private static String[] testData;
    private static int containsRatio;
    private static int addRatio;

    private Set<String> set;

    @Setup(Level.Trial)
    public void createSet(ThreadParams threads) {
        createImplementation(threads);
        setRatios();
    }

    private void createImplementation(ThreadParams threads) {
        if ("ConcurrentHashSet".equalsIgnoreCase(implementation)) {
            set = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        } else if ("NonBlockingHashSet".equalsIgnoreCase(implementation)) {
            set = new NonBlockingHashSet<String>();
        } else if ("SingleWriterHashSet".equalsIgnoreCase(implementation)) {
            if (threads.getGroupIndex() == 0 && threads.getSubgroupIndex() == 0 && threads.getSubgroupThreadCount() != 1) {
                throw new IllegalArgumentException("Trying to benchmark SingleWriterHashSet with multiple writer threads");
            }
            set = new SingleWriterHashSet<String>(16);
        } else {
            throw new IllegalArgumentException("Unsupported map: " + implementation);
        }
    }

    @State(Scope.Thread)
    public static class ThreadState {
        private SimpleRandom random = new SimpleRandom();

        int next() {
            return random.next();
        }
    }

    private void setRatios() {
        containsRatio = (readRatio << 20) / 100;
        addRatio = (((1 << 20) - containsRatio) >> 1) + containsRatio;
    }

    @Setup(Level.Trial)
    public void prepareSet() {
        testData = new String[Pow2.roundToPowerOfTwo(tableSize)];
        for (int i = 0; i < testData.length; i++) {
            testData[i] = String.valueOf(i) + "abc" + String.valueOf(i * 17 + 123);
        }

        Random rand = new Random();

        int sz = set.size();
        while (sz + 1024 < tableSize) {
            int idx = rand.nextInt();
            for (int i = 0; i < 1024; i++) {
                String key = testData[idx & (testData.length - 1)];
                set.add(key);
                idx++;
            }
            sz = set.size();
        }

        while (sz < ((tableSize >> 1) + (tableSize >> 3))) {
            int trip = 0;
            int idx = rand.nextInt();
            while (true) {
                String key = testData[idx & (testData.length - 1)];
                if (sz < tableSize) {
                    if (set.add(key)) {
                        sz++;
                        break;
                    }
                } else if (set.remove(key)) {
                    sz--;
                    break;
                }
                idx++;
                if ((trip & 15) == 15) {
                    idx = rand.nextInt();
                }
                ++trip;
            }
        }

        if (sz != set.size()) {
            throw new AssertionError("Size does not match table contents sz=" + sz + " size()=" + set.size());
        }
    }

    @Benchmark
    @Group("rw")
    public boolean randomContainsAddRemove(ThreadState state) {
        String key = testData[state.next() & (testData.length - 1)];
        int x = state.next() & ((1 << 20) - 1);
        if (x < containsRatio) {
            return set.contains(key);
        } else if (x < addRatio) {
            return set.add(key);
        } else {
            return set.remove(key);
        }
    }

    @Benchmark
    @Group("rw")
    public boolean randomGet(ThreadState state) {
        String key = testData[state.next() & (testData.length - 1)];
        return set.contains(key);
    }
}
