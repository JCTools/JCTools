package org.jctools.util;

import javax.tools.JavaFileObject;
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
