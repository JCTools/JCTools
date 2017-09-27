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
package org.jctools.jmh.throughput;

import java.util.concurrent.TimeUnit;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MessagePassingQueueByTypeFactory;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
public class MpqDrainFillThroughputBackoffNone {
    static final Integer TEST_ELEMENT = 1;
    Integer element = TEST_ELEMENT;
    Integer escape;
    MessagePassingQueue<Integer> q;

    @Param(value = { "SpscArrayQueue", "MpscArrayQueue", "SpmcArrayQueue", "MpmcArrayQueue" })
    String qType;
    
    @Param(value = { "132000" })
    int qCapacity;

    @Setup()
    public void createQandPrimeCompilation() {
        q = MessagePassingQueueByTypeFactory.createQueue(qType, qCapacity);
    }

    @AuxCounters
    @State(Scope.Thread)
    public static class PollCounters {
        public int pollsFailed;
        public int pollsMade;

        @Setup(Level.Iteration)
        public void clean() {
            pollsFailed = pollsMade = 0;
        }
    }

    @AuxCounters
    @State(Scope.Thread)
    public static class OfferCounters {
        public int offersFailed;
        public int offersMade;

        @Setup(Level.Iteration)
        public void clean() {
            offersFailed = offersMade = 0;
        }
    }

    @Benchmark
    @Group("normal")
    public void fill(final OfferCounters counters) {
        long filled = q.fill(new MessagePassingQueue.Supplier<Integer>() {
            public Integer get() {
                counters.offersMade++;
                return element;
            }
        });
        if (filled == 0) {
            counters.offersFailed++;
            backoff();
        }
    }

    @Benchmark
    @Group("normal")
    public void drain(final PollCounters counters) {
        long drained = q.drain(new MessagePassingQueue.Consumer<Integer>() {
            public void accept(Integer e) {
                if (e == TEST_ELEMENT) {
                    counters.pollsMade++;
                } else {
                    escape = e;
                }
            }
        });
        if (drained == 0) {
            counters.pollsFailed++;
            backoff();
        }
    }
//
//    @Benchmark
//    @Group("prepetual")
//    public void fill(final OfferCounters counters, final Control ctl) {
//        WaitStrategy w = new WaitStrategy(){
//            public int idle(int idleCounter) {
//                backoff();
//                return idleCounter++;
//            }
//        };
//        ExitCondition e = new ExitCondition() {
//            public boolean keepRunning() {
//                return !ctl.stopMeasurement;
//            }
//        };
//        q.fill(new MessagePassingQueue.Supplier<Integer>() {
//            public Integer get() {
//                counters.offersMade++;
//                return element;
//            }
//        }, w, e);
//    }
//
//    @Benchmark
//    @Group("prepetual")
//    public void drain(final PollCounters counters, final Control ctl) {
//        WaitStrategy w = new WaitStrategy(){
//            public int idle(int idleCounter) {
//                backoff();
//                return idleCounter++;
//            }
//        };
//        ExitCondition e = new ExitCondition() {
//            public boolean keepRunning() {
//                return !ctl.stopMeasurement;
//            }
//        };
//        q.drain(new MessagePassingQueue.Consumer<Integer>() {
//            public void accept(Integer e) {
//                if (e == element) {
//                    counters.pollsMade++;
//                } else {
//                    escape = e;
//                }
//            }
//        }, w, e);
//    }
    
    @TearDown(Level.Iteration)
    public void emptyQ() {
        synchronized (q)
        {
            while (q.poll() != null)
                ;
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    protected void backoff() {
    }
}
