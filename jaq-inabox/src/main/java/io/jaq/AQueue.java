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
package io.jaq;


/**
 * A minimal top level queue interface which allows producer/consumers access via separate interfaces.
 * 
 * @author nitsanw
 *
 * @param <E> element type
 */
public interface AQueue<E> {
    /**
     * @return a consumer instance to be used for this particular thread.
     */
    QConsumer<E> consumer();
    /**
     * @return a producer instance to be used for this particular thread.
     */
    QProducer<E> producer();
    
    int size();
}
