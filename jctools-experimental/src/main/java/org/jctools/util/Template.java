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

import java.lang.reflect.InvocationTargetException;

/**
 * A single class templating library for doing runtime code-gen.
 *
 * Not Threadsafe.
 */
public class Template {

    private final String template;
    private int index;
    private int previousIndex;

    public Template(String template) {
        this.template = template;
    }

    public String render(Object o) {
        StringBuilder result = new StringBuilder(template.length());
        render(o, result);
        return result.toString();
    }

    private void render(Object obj, StringBuilder result) {
        index = 0;
        while(scanNextTag()) {
            copyPrefixTo(result);
            index += 2;
            if (isLoopTag()) {
                index++;
                String tagName = readTagName();
                Template body = extractLoopBody(tagName);
                for (Object child : (Iterable<?>) readTagValue(tagName, obj)) {
                    body.render(child, result);
                }
            } else {
                String tagName = readTagName();
                result.append(readTagValue(tagName, obj));
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

    private Object readTagValue(String tagName, Object o) {
        Class<?> cls = o.getClass();
        try {
            return cls.getField(tagName)
                      .get(o);
        } catch (NoSuchFieldException ignored) {
            try {
                return cls.getMethod(tagName)
                          .invoke(o);
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
