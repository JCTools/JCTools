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

import static org.jctools.channels.mapping.Primitive.simplifyType;

import java.lang.reflect.Constructor;
import java.util.List;

import javax.tools.Diagnostic;

import org.jctools.util.CompilationResult;
import org.jctools.util.SimpleCompiler;
import org.jctools.util.StringWrappingJavaFile;
import org.jctools.util.Template;

public class Mapper<S> {

    private final boolean debugEnabled;
    private final TypeInspector inspector;
    private final Class<S> structInterface;
    private final SimpleCompiler compiler;

    public Mapper(Class<S> structInterface, boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        this.structInterface = structInterface;
        inspector = new TypeInspector(structInterface);
        compiler = new SimpleCompiler();
    }

    /**
     * @return the size that each message takes up in bytes
     */
    public int getSizeInBytes() {
        return Primitive.INT.sizeInBytes + inspector.getSizeInBytes();
    }

    public <I> I newFlyweight(Class<I> implementationParent, String templateFileName, Object... args) {
        Template template = Template.fromFile(implementationParent, templateFileName);
        return newFlyweight(implementationParent, templateFileName, template, args);
    }

    public <I> I newFlyweight(Class<I> implementationParent, String templateFileName, Template template, Object... args) {
        Class<?>[] constructorParameterTypes = getTypes(args);
        ClassViewModel model = new ClassViewModel(implementationParent, constructorParameterTypes, structInterface,
                inspector);
        String source = template.render(model);
        debugLogSource(source);
        CompilationResult result = compiler.compile(model.className(), source);
        checkCompileFailures(templateFileName, result);
        return instantiateImplementation(constructorParameterTypes, model.className(), result, args);
    }

    private void debugLogSource(String source) {
        if (debugEnabled) {
            System.err.println("---------------------------------------");
            System.err.println("Source: ");
            System.err.println(source);
            System.err.println("---------------------------------------");
        }
    }

    @SuppressWarnings("unchecked")
    private <I> I instantiateImplementation(Class<?>[] constructorParameterTypes, String name,
            CompilationResult result, Object[] args) {

        try {
            Class<I> implementation = (Class<I>) result.getClassLoader().loadClass(name);
            Constructor<I> constructor = implementation.getConstructor(constructorParameterTypes);
            return constructor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void checkCompileFailures(String templateFile, CompilationResult result) {
        List<Diagnostic<StringWrappingJavaFile>> diagnostics = result.getDiagnostics();

        if (debugEnabled) {
            if (diagnostics.isEmpty()) {
                System.err.println("No compile diagnostics for: " + templateFile);
            } else {
                System.err.println("---------------------------------------");
                System.err.println("Compile diagnostics for: " + templateFile);
                for (Diagnostic<StringWrappingJavaFile> diagnostic : diagnostics) {
                    System.err.println(diagnostic);
                    System.err.println();
                }
                System.err.println("---------------------------------------");
            }
        }

        if (!result.isSuccessful()) {
            throw new IllegalArgumentException("Unable to compile " + templateFile);
        }
    }

    private Class<?>[] getTypes(Object... args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = simplifyType(args[i].getClass());
        }
        return types;
    }

}
