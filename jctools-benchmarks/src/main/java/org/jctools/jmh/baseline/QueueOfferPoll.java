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
package org.jctools.jmh.baseline;

import org.jctools.queues.QueueByTypeFactory;
import org.openjdk.jmh.annotations.*;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * Measure cost of offer/poll on single thread.
 * 
 * @author nitsanw
 * 
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class QueueOfferPoll {
    private static final int BURST_SIZE = Integer.getInteger("burst.size", 1);
    private static final Integer DUMMY_MESSAGE = 1;
    private final Queue<Integer> q = QueueByTypeFactory.createQueue();
    

    @Benchmark
    public int offerAndPollLoops() {
        for (int i = 0; i < BURST_SIZE; i++) {
            q.offer(DUMMY_MESSAGE);
        }
        Integer result = null;
        for (int i = 0; i < BURST_SIZE; i++) {
            result = q.poll();
        }
        return result.intValue();
    }
}