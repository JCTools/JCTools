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
package org.jctools.queues.varhandle.padding;

/**
 * Right padding: 128 bytes for Apple Silicon, x86 prefetch, and future CPUs.
 *
 * @author <a href="https://github.com/amarziali">Andrea Marziali</a>
 */
class LongRhsPadding extends LongValue {
  @SuppressWarnings("unused")
  private long p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p20, p21, p22, p23, p24;
}
