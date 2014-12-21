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

import org.jctools.channels.ChannelReceiver;

import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;

import static org.jctools.channels.mapping.OldBytecodeGenerator.Customisation;
import static org.jctools.channels.mapping.Primitive.replaceWithPrimitive;

/**
 * Entry class into type mapping code. Does flyweight generation.
 *
 * @param <S> the struct element type
 */
public final class OldMapper<S> {

	private final TypeInspector inspector;
    private final Class<S> structInterface;
    private final boolean classFileDebugEnabled;

    public OldMapper(Class<S> structInterface, boolean classFileDebugEnabled) {
        this.structInterface = structInterface;
        this.classFileDebugEnabled = classFileDebugEnabled;
        inspector = new TypeInspector(structInterface);
    }

    /**
     * @return the size that each message takes up in bytes
     */
    public int getSizeInBytes() {
        return inspector.getSizeInBytes();
    }

    public <I> I newFlyweight(Class<I> type, Customisation customisation, Object ... args) {
        try {
            Class<?>[] constructorParameterTypes = getTypes(args);
            Class<I> implementation = new OldBytecodeGenerator<S,I>(inspector, type, constructorParameterTypes,
                                                                 structInterface, classFileDebugEnabled,
                                                                 customisation).generate();

            Constructor<I> constructor = implementation.getConstructor(constructorParameterTypes);
            return constructor.newInstance(args);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Class<?>[] getTypes(Object... args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            Class<?> type = args[i].getClass();
            type = replaceWithPrimitive(type);
            type = usePublicApiClass(type);
            types[i] = type;
        }
        return types;
    }

    private Class<?> usePublicApiClass(Class<?> type) {
        if ("DirectByteBuffer".equals(type.getSimpleName()))
            return ByteBuffer.class;

        if (ChannelReceiver.class.isAssignableFrom(type))
            return ChannelReceiver.class;

        return type;
    }

}
