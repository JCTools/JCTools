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

package org.jctools.channels.multicast;

import java.util.concurrent.TimeUnit;

import org.jctools.util.JvmInfo;
import org.jctools.util.UnsafeAccess;
import org.jctools.util.UnsafeDirectByteBuffer;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
public class SpOffHeapMulticastChannelRawTptBackoffNone {

   private static final long DELAY_PRODUCER = Long.getLong("delay.p", 0L);
   private static final long DELAY_CONSUMER = Long.getLong("delay.c", 0L);
   @Param(value = {"132000"})
   String requestedCapacity;
   @Param(value = {"8", "32"})
   String requestedMessageSize;
   private long ONE;
   private long escape;
   private OffHeapFixedMessageSizeAppender appender;
   private OffHeapFixedMessageSizeTailer tailer;
   private long tailerMessageCopyAddress;

   public static void main(final String[] args) throws Exception {
      final Options opt = new OptionsBuilder().include(SpOffHeapMulticastChannelRawTptBackoffNone.class.getSimpleName()).build();
      new Runner(opt).run();
   }

   private static void lostOrEOF(OffHeapFixedMessageSizeTailer tailer,
                                 PollCounters counters,
                                 long listened,
                                 long oldLost,
                                 SpOffHeapMulticastChannelRawTptBackoffNone bench) {
      if (listened == OffHeapFixedMessageSizeTailer.LOST) {
         final long newLost = tailer.lost();
         final long lost = newLost - oldLost;
         counters.lost += lost;
      } else if (listened == OffHeapFixedMessageSizeTailer.EOF) {
         //EOF
         counters.empty++;
         bench.backoff();
      }
   }

   @Setup()
   public void init() {
      this.ONE = 777;
      final int capacity = Integer.parseInt(requestedCapacity);
      final int messageSize = Integer.parseInt(requestedMessageSize);
      if (messageSize < 8) {
         throw new AssertionError("can't run with message size<8!");
      }
      final int bufferSize = ChannelLayout.getRequiredBufferSize(capacity, messageSize);
      this.appender = new OffHeapFixedMessageSizeAppender(UnsafeDirectByteBuffer.allocateAlignedByteBuffer(bufferSize, JvmInfo.CACHE_LINE_SIZE), capacity, messageSize);
      this.tailer = new OffHeapFixedMessageSizeTailer(this.appender.buffer(), capacity, messageSize);
      this.tailerMessageCopyAddress = this.tailer.messageCopyAddress();
   }

   @Benchmark
   @Group("tpt")
   public long shout() {
      final OffHeapFixedMessageSizeAppender appender = this.appender;
      final long sequence = appender.writeAcquire();
      try {
         final long messageAddress = appender.messageOffset(sequence);
         UnsafeAccess.UNSAFE.putLong(messageAddress, ONE);
         return messageAddress;
      } finally {
         appender.writeRelease(sequence);
         if (DELAY_PRODUCER != 0) {
            Blackhole.consumeCPU(DELAY_PRODUCER);
         }
      }
   }

   @Benchmark
   @Group("tpt")
   public void listen(PollCounters counters) {
      final long ONE = this.ONE;
      final OffHeapFixedMessageSizeTailer tailer = this.tailer;
      final long messageCopyAddress = this.tailerMessageCopyAddress;
      final long oldLost = tailer.lost();
      final long listened = tailer.readAcquire();
      if (listened >= 0) {
         final long readValue = UnsafeAccess.UNSAFE.getLong(messageCopyAddress);
         tailer.readRelease(listened);
         if (readValue != ONE) {
            escape = readValue;
         }
         counters.listened++;
      } else {
         lostOrEOF(tailer, counters, listened, oldLost, this);
      }
      if (DELAY_CONSUMER != 0) {
         Blackhole.consumeCPU(DELAY_CONSUMER);
      }
   }

   protected void backoff() {

   }

   @Setup(Level.Iteration)
   public void chase() {
      tailer.chase();
   }

   @AuxCounters
   @State(Scope.Thread)
   public static class PollCounters {

      public long empty;
      public long lost;
      public long listened;

      @Setup(Level.Iteration)
      public void clean() {
         empty = lost = listened = 0;
      }
   }

}
