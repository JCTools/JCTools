package org.jctools.jmh.collections;

import java.lang.reflect.Constructor;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import org.openjdk.jmh.annotations.Warmup;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode({ Mode.AverageTime })
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
public class SetOps {
    static class Key {
        final int hash;

        Key(int i) {
            hash = i;
        }

        public int hashCode() {
            return hash;
        }

        public boolean equals(Object obj) {
            return this == obj;
        }
    }

    @Param("1024")
    int size;
    @Param("512")
    int occupancy;
    @Param("2048")
    int keyBound;
    @Param({ "java.util.HashSet", "org.jctools.sets.OpenHashSet"})//, "koloboke" })
    String type;
    private Set<Key> set;
    private Key key;

    @Setup(Level.Trial)
    public void prepare() throws Exception {
        set = createSet(type, size);
        Random r = new Random(666);

        for (int i = 0; i < occupancy - 1; i++) {
            set.add(new Key(r.nextInt(keyBound)));
        }
        key = new Key(r.nextInt(keyBound));
        set.add(key);
    }

    @Benchmark
    public boolean add() {
        return set.add(key);
    }

    @Benchmark
    public boolean remove() {
        return set.remove(key);
    }

    @Benchmark
    public boolean contains() {
        return set.contains(key);
    }

    @Benchmark
    public int sum() {
        int sum = 0;
        for(Key k : set) {
            sum += k.hash;
        }
        return sum;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static Set<Key> createSet(String queueType, final int capacity) throws Exception {
//        if(queueType.equals("koloboke")) {
//            return HashObjSets.newMutableSet(capacity);
//        }

        Class clazz = Class.forName(queueType);
        Constructor constructor;
        constructor = clazz.getConstructor(Integer.TYPE);
        return (Set<Key>) constructor.newInstance(capacity);
    }
}
