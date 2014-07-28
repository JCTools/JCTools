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
package org.jctools.channels.mapping;

import java.lang.reflect.Constructor;

/**
 * Entry class into type mapping code. Does flyweight generation.
 *
 * @param <E>
 */
public final class Mapper<E> {

	private final TypeInspector inspector;
    private final Class<E> implementation;
	private final Constructor<E> constructor;

    public Mapper(Class<E> representingClass, boolean classFileDebugEnabled) {
		inspector = new TypeInspector(representingClass);
        implementation = new BytecodeGenerator<E>(inspector, representingClass, classFileDebugEnabled).generate();
        try {
        	constructor = implementation.getConstructor(Long.TYPE);
        } catch (RuntimeException e) {
        	throw e;
        } catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

    /**
     * @return the size that each message takes up in bytes
     */
    public int getSizeInBytes() {
        return inspector.getSizeInBytes();
    }

    /**
     * @see org.jctools.channels.mapping.Flyweight
     * @param address the pointer to initially set the flyweight to
     * @return the flyweight, it also implements org.jctools.channels.mapping.Flyweight
     */
	public E newFlyweight(long address) {
		try {
			return constructor.newInstance(address);
		} catch (RuntimeException e) {
        	throw e;
        } catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
