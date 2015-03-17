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

import javax.tools.SimpleJavaFileObject;
import java.net.URI;

/**
 * A Java file object which is used to represent the Java source code coming from a string.
 */
public class StringWrappingJavaFile extends SimpleJavaFileObject {

    /**
     * The source code of this "file".
     */
    private final String code;

    /**
     * Constructs a new JavaSourceFromString.
     *
     * @param name
     *            the name of the compilation unit represented by this file
     *            object
     * @param code
     *            the source code for the compilation unit represented by this
     *            file object
     */
    public StringWrappingJavaFile(String name, String code) {
        super(getFileUri(name), Kind.SOURCE);
        this.code = code;
    }

    private static URI getFileUri(String name) {
        return URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }

}
