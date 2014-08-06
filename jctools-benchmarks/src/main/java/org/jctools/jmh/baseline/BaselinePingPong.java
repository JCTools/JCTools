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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Control;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@State(Scope.Group)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class BaselinePingPong {

    public final AtomicBoolean flag = new AtomicBoolean();

    @Benchmark
    @Group("pingpong")
    public void ping(Control cnt) {
        while (!cnt.stopMeasurement && !flag.compareAndSet(false, true)) {
            // this body is intentionally left blank
        }
    }

    @Benchmark
    @Group("pingpong")
    public void pong(Control cnt) {
        while (!cnt.stopMeasurement && !flag.compareAndSet(true, false)) {
            // this body is intentionally left blank
        }
    }
}