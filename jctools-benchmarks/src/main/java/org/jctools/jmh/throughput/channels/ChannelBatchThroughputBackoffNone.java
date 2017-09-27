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

package org.jctools.jmh.throughput.channels;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.jctools.channels.Channel;
import org.jctools.channels.ChannelConsumer;
import org.jctools.channels.ChannelProducer;
import org.jctools.channels.ChannelReceiver;
import org.jctools.channels.mpsc.MpscChannel;
import org.jctools.channels.multicast.SpMulticastChannel;
import org.jctools.channels.spsc.SpscChannel;
import org.jctools.util.JvmInfo;
import org.jctools.util.Pow2;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
public class ChannelBatchThroughputBackoffNone {

   private static final long DELAY_PRODUCER = Long.getLong("delay.p", 0L);
   private static final long DELAY_CONSUMER = Long.getLong("delay.c", 0L);
   private static ThreadLocal<Object> marker = new ThreadLocal<Object>();
   @Param(value = {"132000"})
   int capacity;
   @Param(value = {"SpMulticast", "Spsc", "Mpsc"})
   ChannelThroughputBackoffNone.Type type;
   int limit;
   private ByteBuffer buffer;
   private Channel<Ping> channel;
   private ChannelProducer<Ping> producer;
   private ChannelConsumer consumer;
   private ChannelReceiver<Ping> receiver;

   // Deliberately not a local to avoid constant folding.
   private long writeValue = 1L;

   public static void main(String[] args) throws RunnerException {
      Options opt = new OptionsBuilder().include(ChannelBatchThroughputBackoffNone.class.getSimpleName()).build();

      new Runner(opt).run();
   }

   @Setup
   public void setup(final Blackhole blackhole) {
      receiver = new ChannelReceiver<Ping>() {
         @Override
         public void accept(Ping element) {
            blackhole.consume(element.getValue());
         }
      };
      buffer = ByteBuffer.allocateDirect(Pow2.roundToPowerOfTwo(capacity * 2) * (8 + 4) + JvmInfo.CACHE_LINE_SIZE * 5);

      switch (type) {
         case Spsc:
            channel = new SpscChannel<Ping>(buffer, capacity, Ping.class);
            break;
         case Mpsc:
            channel = new MpscChannel<Ping>(buffer, capacity, Ping.class);
            break;
         case SpMulticast:
            channel = new SpMulticastChannel<Ping>(buffer, capacity, Ping.class);
            break;
         default:
            throw new IllegalArgumentException();
      }
      producer = channel.producer();
      consumer = channel.consumer(receiver);
      this.limit = channel.maximumCapacity();
      OfferCounters oc = new OfferCounters();
      PollCounters pc = new PollCounters();
      for (int i = 0; i < 100000; i++) {
         offer(oc);
         poll(pc, null);
      }
   }

   @Benchmark
   @Group("tpt")
   public void offer(OfferCounters counters) {
      ChannelProducer<Ping> lProducer = producer;
      if (!lProducer.claim()) {
         counters.offersFailed++;
      } else {
         Ping element = lProducer.currentElement();
         element.setValue(writeValue);
         lProducer.commit();
         counters.offersMade++;
      }
      if (DELAY_PRODUCER != 0) {
         Blackhole.consumeCPU(DELAY_PRODUCER);
      }
   }

   @Benchmark
   @Group("tpt")
   public void poll(PollCounters counters, ConsumerMarker cs) {
      final int read = consumer.read(this.limit);
      if (read == 0) {
         counters.pollsFailed++;
      } else {
         counters.pollsMade += read;
      }
      if (DELAY_CONSUMER != 0) {
         Blackhole.consumeCPU(DELAY_CONSUMER);
      }
   }

   @TearDown(Level.Iteration)
   public void emptyQ() {
      if (marker.get() == null)
         return;
      // sadly the iteration tear down is performed from each participating thread, so we need to guess
      // which is which (can't have concurrent access to poll).
      //TODO use chase method for multicast channel!!!
      for (int i = 0; i < 2; i++) {
         while (consumer.read()) {

         }
      }
   }

   public enum Type {
      Spsc, Mpsc, SpMulticast
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

   @State(Scope.Thread)
   public static class ConsumerMarker {

      public ConsumerMarker() {
         marker.set(this);
      }
   }
}
