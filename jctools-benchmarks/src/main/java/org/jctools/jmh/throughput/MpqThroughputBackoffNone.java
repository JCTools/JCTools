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
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
public class MpqThroughputBackoffNone {
	private static final long DELAY_PRODUCER = Long.getLong("delay.p", 0L);
	private static final long DELAY_CONSUMER = Long.getLong("delay.c", 0L);
	private static final Integer ONE = 777;
	MessagePassingQueue<Integer> q;
	private Integer escape;
	@Param(value = { "SpscArrayQueue", "MpscArrayQueue", "SpmcArrayQueue", "MpmcArrayQueue" })
	String qType;
	@Param(value = { "132000" })
	int qCapacity;

	@Setup()
	public void createQandPrimeCompilation() {
		q = MessagePassingQueueByTypeFactory.createQueue(qType, 128);

		// stretch the queue to the limit, working through resizing and full
		for (int i = 0; i < 128 + 100; i++) {
			q.offer(ONE);
		}
		for (int i = 0; i < 128 + 100; i++) {
			q.poll();
		}
	    // stretch the queue to the limit, working through resizing and full
        for (int i = 0; i < 128 + 100; i++) {
            q.relaxedOffer(ONE);
        }
        for (int i = 0; i < 128 + 100; i++) {
            q.relaxedPoll();
        }
		// make sure the important common case is exercised
		for (int i = 0; i < 20000; i++) {
		    q.offer(ONE);
            q.poll();
            q.relaxedOffer(ONE);
            q.relaxedPoll();
		}
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

	private static ThreadLocal<Object> marker = new ThreadLocal<Object>();

	@State(Scope.Thread)
	public static class ConsumerMarker {
		public ConsumerMarker() {
			marker.set(this);
		}
	}

	@Benchmark
	@Group("nor")
	public void offerNoR(OfferCounters counters) {
		if (!q.offer(ONE)) {
			counters.offersFailed++;
			backoff();
		} else {
			counters.offersMade++;
		}
		if (DELAY_PRODUCER != 0) {
			Blackhole.consumeCPU(DELAY_PRODUCER);
		}
	}

	@Benchmark
	@Group("nor")
	public void pollNoR(PollCounters counters, ConsumerMarker cm) {
		Integer e = q.poll();
		if (e == null) {
			counters.pollsFailed++;
			backoff();
		} else if (e == ONE) {
			counters.pollsMade++;
		} else {
			escape = e;
		}
		if (DELAY_CONSUMER != 0) {
			Blackhole.consumeCPU(DELAY_CONSUMER);
		}
	}

	@Benchmark
	@Group("bothr")
	public void offerBothR(OfferCounters counters) {
		if (!q.relaxedOffer(ONE)) {
			counters.offersFailed++;
			backoff();
		} else {
			counters.offersMade++;
		}
		if (DELAY_PRODUCER != 0) {
			Blackhole.consumeCPU(DELAY_PRODUCER);
		}
	}

	@Benchmark
	@Group("bothr")
	public void pollBothR(PollCounters counters, ConsumerMarker cm) {
		Integer e = q.relaxedPoll();
		if (e == null) {
			counters.pollsFailed++;
			backoff();
		} else if (e == ONE) {
			counters.pollsMade++;
		} else {
			escape = e;
		}
		if (DELAY_CONSUMER != 0) {
			Blackhole.consumeCPU(DELAY_CONSUMER);
		}
	}
    @Benchmark
    @Group("pr")
    public void offerPR(OfferCounters counters) {
        if (!q.relaxedOffer(ONE)) {
            counters.offersFailed++;
            backoff();
        } else {
            counters.offersMade++;
        }
        if (DELAY_PRODUCER != 0) {
            Blackhole.consumeCPU(DELAY_PRODUCER);
        }
    }

    @Benchmark
    @Group("pr")
    public void pollPR(PollCounters counters, ConsumerMarker cm) {
        Integer e = q.poll();
        if (e == null) {
            counters.pollsFailed++;
            backoff();
        } else if (e == ONE) {
            counters.pollsMade++;
        } else {
            escape = e;
        }
        if (DELAY_CONSUMER != 0) {
            Blackhole.consumeCPU(DELAY_CONSUMER);
        }
    }
    
    @Benchmark
    @Group("cr")
    public void offerCR(OfferCounters counters) {
        if (!q.offer(ONE)) {
            counters.offersFailed++;
            backoff();
        } else {
            counters.offersMade++;
        }
        if (DELAY_PRODUCER != 0) {
            Blackhole.consumeCPU(DELAY_PRODUCER);
        }
    }

    @Benchmark
    @Group("cr")
    public void pollCR(PollCounters counters, ConsumerMarker cm) {
        Integer e = q.relaxedPoll();
        if (e == null) {
            counters.pollsFailed++;
            backoff();
        } else if (e == ONE) {
            counters.pollsMade++;
        } else {
            escape = e;
        }
        if (DELAY_CONSUMER != 0) {
            Blackhole.consumeCPU(DELAY_CONSUMER);
        }
    }
	@TearDown(Level.Iteration)
	public void emptyQ() {
		if (marker.get() == null)
			return;
		// sadly the iteration tear down is performed from each participating
		// thread, so we need to guess
		// which is which (can't have concurrent access to poll).
		while (q.poll() != null)
			;
	}

	protected void backoff() {
	}
}
