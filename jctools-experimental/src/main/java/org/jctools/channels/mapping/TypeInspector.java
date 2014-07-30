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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.reflect.Modifier.isAbstract;
import static java.util.Arrays.asList;

public class TypeInspector {
    
    private final Class<?> flyweightClass;
    
    final List<Method> getters;
    final Map<String, Method> setters;
    
    public TypeInspector(Class<?> flyweightClass) {
        this.flyweightClass = flyweightClass;
        if(!flyweightClass.isInterface())
        	throw new InvalidInterfaceException("Your flyweight class must be an interface");
        
        getters = findGetters();
        setters = findSetters();
        checkRemainingMethods(flyweightClass);
    }

	private void checkRemainingMethods(Class<?> klass) {
		List<Method> methods = new ArrayList<Method>(asList(klass.getDeclaredMethods()));
		methods.removeAll(getters);
		methods.removeAll(setters.values());
        for (Method method : methods)
			if (isAbstract(method.getModifiers()))
				throw new InvalidInterfaceException(klass.getName() + " has abstract methods that are neither getters nor setters");
	}

	private List<Method> findGetters() {
        List<Method> methods = new ArrayList<Method>();
        for (Method method : flyweightClass.getDeclaredMethods()) {
            String name = method.getName();
			if (!name.startsWith("get"))
                continue;

			ensureAbstract(method);
            returnsPrimitive(method);
            hasNoParameters(method);
            methods.add(method);
        }
        return methods;
    }
	
    private void ensureAbstract(Method method) {
		if (!isAbstract(method.getModifiers()))
			throw new InvalidInterfaceException(method + " must be abstract, since its a getter or setter");
	}

	private void hasNoParameters(Method method) {
        if (method.getParameterTypes().length != 0)
            throw new InvalidInterfaceException(method.getName() + " is a getter with one or more parameters");
    }

    private void returnsPrimitive(Method method) {
        if (!method.getReturnType().isPrimitive())
        	throw new InvalidInterfaceException(method.getName() + " is a getter that doesn't return a primitive");
    }

    Primitive getReturn(Method method) {
        return Primitive.of(method.getReturnType());
    }

	private Map<String, Method> findSetters() {
		Map<String, Method> methods = new HashMap<String, Method>();
        for (Method method : flyweightClass.getDeclaredMethods()) {
            if (!method.getName().startsWith("set"))
                continue;

            ensureAbstract(method);
            returnsVoid(method);
            hasOnePrimitiveParameter(method);
            methods.put(method.getName(), method);
        }
        return methods;
	}

    private void hasOnePrimitiveParameter(Method method) {
		Class<?>[] parameters = method.getParameterTypes();
		if (parameters.length != 1)
			throw new InvalidInterfaceException(method.getName() + " is a setter with more than one parameter");
		
		if (!parameters[0].isPrimitive())
			throw new InvalidInterfaceException(method.getName() + " is a setter with a non-primitive parameter");
	}

	private void returnsVoid(Method method) {
		if (method.getReturnType() != Void.TYPE)
			throw new InvalidInterfaceException(method.getName() + " is a setter that doesn't return void");
	}

	public int getSizeInBytes() {
        int total = 0;
        for (Method getter : getters) {
            total += getReturn(getter).sizeInBytes;
        }
        return total;
    }

	public Method setterFor(Method getter) {
		String name = getter.getName().replaceFirst("get", "set");
		Method method = setters.get(name);
		if (method == null)
			throw new InvalidInterfaceException("Unable to find setter with name: " + name);
		return method;
	}

}
