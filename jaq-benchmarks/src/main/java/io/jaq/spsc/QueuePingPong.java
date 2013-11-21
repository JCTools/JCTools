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
package io.jaq.spsc;

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
public class QueuePingPong {
    public final Queue<Integer> in = SPSCQueueFactory.createQueue();
    public final Queue<Integer> out = SPSCQueueFactory.createQueue();
    public final static Integer ONE = 1;
    @GenerateMicroBenchmark
    @Group("pingpong")
    public void ping(Control cnt) {
        in.offer(ONE);
        while (!cnt.stopMeasurement && out.poll() == null) {
        }
    }

    @GenerateMicroBenchmark
    @Group("pingpong")
    public void pong(Control cnt) {
        while (!cnt.stopMeasurement && in.poll() == null) {
        }
        out.offer(ONE);
    }
    @TearDown(Level.Iteration)
    public void emptyQs() {
        while(in.poll() != null);
        while(out.poll() != null);
    }
}