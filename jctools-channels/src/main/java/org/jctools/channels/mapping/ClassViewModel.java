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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.jctools.channels.spsc.SpscOffHeapFixedSizeRingBuffer;

public class ClassViewModel {

    private final Class<?> implementationParent;
    private final Class<?>[] constructorParams;
    private final Class<?> structInterface;
    private final TypeInspector inspector;

    public ClassViewModel(
            Class<?> implementationParent, Class<?>[] constructorParams, Class<?> structInterface,
            TypeInspector inspector) {

        this.implementationParent = implementationParent;
        this.constructorParams = constructorParams;
        this.structInterface = structInterface;
        this.inspector = inspector;
    }

    public String className() {
        return implementationParent.getSimpleName() + "_" + structInterface.getSimpleName();
    }

    public String implementationParent() {
        return cleanClassName(implementationParent);
    }

    public String flyweightInterface() {
        return cleanClassName(structInterface);
    }

    // Account for anonymous inner classes
    private String cleanClassName(Class<?> cls) {
        return cls.getName().replace('$', '.');
    }

    public List<Variable> constructorParams() {
        List<Variable> variables = new ArrayList<Variable>(constructorParams.length);
        for (int i = 0; i < constructorParams.length; i++) {
            variables.add(new Variable(constructorParams[i].getName(), "arg" + i, 0, ""));
        }
        return variables;
    }

    public List<Variable> fields() {
        int fieldOffset = SpscOffHeapFixedSizeRingBuffer.MESSAGE_INDICATOR_SIZE;
        List<Variable> fields = new ArrayList<Variable>();
        for (Method method : inspector.getters) {
            Primitive type = Primitive.of(method.getReturnType());
            String name = method.getName().substring(3);
            fields.add(new Variable(type.javaEquivalent.getName(), name, fieldOffset, type.unsafeMethodSuffix()));
            fieldOffset += type.sizeInBytes;
        }
        return fields;
    }

}