/*
 * Copyright 2012 Real Logic Ltd.
 *
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
package org.jctools.handrolled.throughput.mpmc;

/**
 * These tests aren't to describe "real world" performance, but to derive comparisons between different implementations, and because of
 * something called OSR, you CANNOT depend on these tests to describe what they are testing in the "real world".
 *
 *
 * see:
 * http://psy-lob-saw.blogspot.de/2016/02/wait-for-it-counteduncounted-loops.html
 * http://www.cliffc.org/blog/2011/11/22/what-the-heck-is-osr-and-why-is-it-bad-or-good/
 *
 * We can, however try to make this as "real world" as possible by telling the JVM to compile the bytecode. Use these VM arguments.
 *   -XX:-TieredCompilation -Xcomp -server
 *
 *  TODO: replace these tests with JMH for more accurate tests
 */
@SuppressWarnings("Duplicates")
public class AllQueuePerfTest {
    public static final int REPETITIONS = 50 * 1000 * 100;

    private static final int producers = 1;
    private static final int consumers = 4;

    private static final int bestRunsToAverage = 4;
    private static final int runs = 10;
    private static final int warmups = 3;
    private static final boolean showStats = false;

    public static void main(final String[] args) throws Exception {
        System.out.format("reps: %,d  Concurrency: " + producers + " producers, " + consumers + " consumers\n", REPETITIONS);

        new Block_MpmcATQ_PerfTest().run(REPETITIONS, producers, consumers, warmups, runs, bestRunsToAverage, showStats);
        new MpmcArrayQueue_PerfTest().run(REPETITIONS, producers, consumers, warmups, runs, bestRunsToAverage, showStats);
        new LBQ_PerfTest().run(REPETITIONS, producers, consumers, warmups, runs, bestRunsToAverage, showStats);
        new LTQ_PerfTest().run(REPETITIONS, producers, consumers, warmups, runs, bestRunsToAverage, showStats);
    }
}
