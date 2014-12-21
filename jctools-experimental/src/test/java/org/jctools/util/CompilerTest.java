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
package org.jctools.util;

import org.junit.Test;

import java.util.Locale;
import java.util.concurrent.Callable;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

public class CompilerTest {

    @Test
    public void validJavaCodeCompiles() throws Exception {
        // given
        String javaSource =
                "import java.util.concurrent.Callable;" +
                "public class A implements Callable<String> { public String call() { return \"hi\"; } }";
        SimpleCompiler compiler = new SimpleCompiler();

        // when
        CompilationResult result = compiler.compile("A", javaSource);

        // then
        assertTrue("Failed compile: " + result.getDiagnostics(), result.isSuccessful());
        assertThat(result.getDiagnostics(), hasSize(0));

        @SuppressWarnings("unchecked")
        Callable<String> a = (Callable<String>) result.getClassLoader().loadClass("A").newInstance();
        assertEquals("hi", a.call());
    }

    @Test
    public void invalidJavaFailsToCompile() throws Exception {
        // given
        String javaSource = "class A implements Callable<String> { public String call() { return \"hi\"; } }";
        SimpleCompiler compiler = new SimpleCompiler();

        // when
        CompilationResult result = compiler.compile("A", javaSource);

        // then
        assertFalse("compile succeeded", result.isSuccessful());
        assertThat(result.getDiagnostics(), hasSize(1));

        String message = result.getDiagnostics().get(0).getMessage(Locale.getDefault());
        assertThat(message, containsString("cannot find symbol"));
        assertThat(message, containsString("symbol: class Callable"));
    }

}
