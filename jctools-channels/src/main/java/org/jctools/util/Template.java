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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * A single class templating library for doing runtime code-gen.
 *
 * Not Threadsafe.
 */
public class Template {

    private final String template;
    private int index;
    private int previousIndex;

    public static Template fromFile(final Class<?> resourceRoot, final String fileName) {
        InputStream templateStream = resourceRoot.getResourceAsStream(fileName);
        if(templateStream == null) {
            throw new IllegalArgumentException("Template file of the name: \'"+fileName+"\' was not found.");
        }
        return fromStream(templateStream);
    }

    private static Template fromStream(InputStream templateStream) {
        if(templateStream == null) {
            throw new IllegalArgumentException("Null template stream");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(templateStream));
        try {
            try {
                StringBuilder buffer = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line);
                    buffer.append('\n');
                }
                return new Template(buffer.toString());
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Template(final String template) {
        this.template = template;
    }

    public String render(Object o) {
        StringBuilder result = new StringBuilder(template.length());
        render(o, result);
        return result.toString();
    }

    private void render(Object obj, StringBuilder result) {
        render(obj, result, false);
    }

    private void render(Object obj, StringBuilder result, boolean last) {
        index = 0;
        while(scanNextTag()) {
            copyPrefixTo(result);
            index += 2;
            if (isLoopTag()) {
                index++;
                String tagName = readTagName();
                Template body = extractLoopBody(tagName);
                List<?> values = (List<?>) readTagValue(tagName, obj, last);
                int lastIndex = values.size() - 1;
                for (int i = 0; i < values.size(); i++) {
                    body.render(values.get(i), result, i == lastIndex);
                }
            } else {
                String tagName = readTagName();
                result.append(readTagValue(tagName, obj, false));
            }
        }
        copySuffixTo(result);
    }

    private Template extractLoopBody(String tagName) {
        String closingTag = "{{/" + tagName + "}}";
        int endOfBody = template.indexOf(closingTag, index);
        Template body = new Template(template.substring(index, endOfBody));
        index = endOfBody + closingTag.length();
        return body;
    }

    private boolean isLoopTag() {
        return template.charAt(index) == '#';
    }

    private boolean scanNextTag() {
        previousIndex = index;
        index = template.indexOf("{{", index);
        return index != -1;
    }

    private void copyPrefixTo(StringBuilder result) {
        result.append(template, previousIndex, index);
    }

    private Object readTagValue(String tagName, Object obj, boolean last) {
        Object value = readBuiltinTag(tagName, obj, last);
        if (value != null)
            return value;

        return readField(tagName, obj);
    }

    private Object readBuiltinTag(String tagName, Object obj, boolean last) {
        if ("notLast".equals(tagName)) {
            if (last) {
                return emptyList();
            } else {
                return singletonList(obj);
            }
        }

        return null;
    }

    private Object readField(String tagName, Object obj) {
        Class<?> cls = obj.getClass();
        try {
            return cls.getField(tagName)
                      .get(obj);
        } catch (NoSuchFieldException ignored) {
            try {
                return cls.getMethod(tagName)
                          .invoke(obj);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(e);
            } catch (InvocationTargetException e) {
                throw new IllegalArgumentException(e);
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException(e);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String readTagName() {
        int endTagIndex = template.indexOf("}}", index);
        String tagName = template.substring(index, endTagIndex);
        index = endTagIndex + 2;
        return tagName;
    }

    private void copySuffixTo(StringBuilder result) {
        // Copy from previousIndex because index will be -1 at this point
        result.append(template, previousIndex, template.length());
    }

}
