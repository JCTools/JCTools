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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
public class SpOffHeapFixedMessageSizeAppenderTpt {

   @Param(value = {"132000"})
   String requestedCapacity;

   @Param(value = {"8", "32"})
   String requestedMessageSize;

   private OffHeapFixedMessageSizeAppender appender;

   public static void main(final String[] args) throws Exception {
      final Options opt = new OptionsBuilder().include(SpOffHeapFixedMessageSizeAppenderTpt.class.getSimpleName()).warmupIterations(5).measurementIterations(5).forks(1).build();
      new Runner(opt).run();
   }

   @Setup()
   public void init() {
      final int capacity = Integer.parseInt(requestedCapacity);
      final int messageSize = Integer.parseInt(requestedMessageSize);
      final int bufferSize = ChannelLayout.getRequiredBufferSize(capacity, messageSize);
      this.appender = new OffHeapFixedMessageSizeAppender(UnsafeDirectByteBuffer.allocateAlignedByteBuffer(bufferSize, JvmInfo.CACHE_LINE_SIZE), capacity, messageSize);
   }

   @Benchmark
   public long shout() {
      final OffHeapFixedMessageSizeAppender appender = this.appender;
      final long sequence = appender.writeAcquire();
      try {
         final long messageAddress = appender.messageOffset(sequence);
         UnsafeAccess.UNSAFE.putLong(messageAddress, 1L);
         return messageAddress;
      } finally {
         appender.writeRelease(sequence);
      }
   }

}
