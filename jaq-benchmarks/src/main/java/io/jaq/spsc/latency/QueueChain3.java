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
package io.jaq.spsc.latency;

import io.jaq.spsc.SPSCQueueFactory;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.logic.Control;

@State(Scope.Group)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class QueueChain3 {
    public final Queue<Integer> link1 = SPSCQueueFactory.createQueue();
    public final Queue<Integer> link2 = SPSCQueueFactory.createQueue();
    public final Queue<Integer> link3 = SPSCQueueFactory.createQueue();
    public final Queue<Integer> link4 = SPSCQueueFactory.createQueue();
    public final static Integer ONE = 1;

    @GenerateMicroBenchmark
    @Group("pingpong")
    public Integer loop(Control cnt) {
        link1.offer(ONE);
        return read(cnt, link4);
    }

    private Integer read(Control cnt, Queue<Integer> q) {
        Integer e = null;
        while (!cnt.stopMeasurement && (e = q.poll()) == null) {
        }
        return e;
    }

    @GenerateMicroBenchmark
    @Group("pingpong")
    public void step1(Control cnt) {
        Integer e = read(cnt, link1);
        if (e != null)
            link2.offer(e);
    }

    @GenerateMicroBenchmark
    @Group("pingpong")
    public void step2(Control cnt) {
        Integer e = read(cnt, link2);
        if (e != null)
            link3.offer(e);
    }

    @GenerateMicroBenchmark
    @Group("pingpong")
    public void step3(Control cnt) {
        Integer e = read(cnt, link3);
        if (e != null)
            link4.offer(e);
    }

    @TearDown(Level.Iteration)
    public void emptyQs() {
        while (link1.poll() != null)
            ;
        while (link2.poll() != null)
            ;
        while (link3.poll() != null)
            ;
        while (link4.poll() != null)
            ;
    }
}