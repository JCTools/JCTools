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

import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

class GeneratedClassLoader extends ClassLoader {
    
    private static final String DUMP_DIR = System.getProperty("user.home") + File.separator + "channel_debug_files" + File.separator;
    
    private final boolean classFileDebugEnabled;

    GeneratedClassLoader(final boolean classFileDebugEnabled) {
        this.classFileDebugEnabled = classFileDebugEnabled;
    }
    
	synchronized Class<?> defineClass(final String binaryName, final ClassWriter cw) {
		final byte[] bytecode = cw.toByteArray();
		logDebugInfo(binaryName, bytecode);
		return defineClass(binaryName, bytecode, 0, bytecode.length);
	}

    private void logDebugInfo(final String binaryName, final byte[] bytecode) {
        if (!classFileDebugEnabled)
            return;
        
		final File file = new File(DUMP_DIR + makeFilename(binaryName));
		if (!dumpDirExists(file.getParentFile()))
		    throw new IllegalStateException("Unable to create directory: " + file.getParent());
		
		writeBytecodeToFile(bytecode, file);
    }

    private void writeBytecodeToFile(final byte[] bytecode, final File file) {
        OutputStream out = null;
		try {
			out = new FileOutputStream(file);
			out.write(bytecode);
			out.flush();
		} catch (IOException e) {
            e.printStackTrace();
        } finally {
			closeStream(out);
		}
    }

    private boolean dumpDirExists(final File dumpDir) {
        return (dumpDir.exists() && dumpDir.isDirectory()) || dumpDir.mkdirs();
    }

    private String makeFilename(final String binaryName) {
        return binaryName.replace('.', File.separatorChar) + ".class";
    }

    private void closeStream(OutputStream out) {
        if (out == null)
            return;

    	try {
    		out.close();
    	} catch (IOException e) {
    	    e.printStackTrace();
    	}
    }

}
