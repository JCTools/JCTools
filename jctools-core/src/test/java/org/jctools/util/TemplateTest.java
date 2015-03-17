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

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TemplateTest {

    public static class Foo {
        public final String value;
        public final Integer nonStringValue;
        private final String privateValue;
        private final Integer privateNonStringValue;
        private final List<Integer> values;

        public Foo(
                String value, Integer nonStringValue, String privateValue, Integer privateNonStringValue,
                List<Integer> values) {

            this.value = value;
            this.nonStringValue = nonStringValue;
            this.privateValue = privateValue;
            this.privateNonStringValue = privateNonStringValue;
            this.values = values;
        }

        public String getPrivateValue() {
            return privateValue;
        }

        public Integer getPrivateNonStringValue() {
            return privateNonStringValue;
        }

        public List<Integer> getValues() {
            return values;
        }
    }

    private Foo foo = new Foo("World", 3, "World", 3, Arrays.asList(1,2,3));

    @Test
    public void plainTextRenderedAsIs() {
        Template template = new Template("Hello World");
        assertEquals("Hello World", template.render(foo));
    }

    @Test
    public void valuesSubstitutedIntoTemplate() {
        Template template = new Template("Hello {{value}} ");
        assertEquals("Hello World ", template.render(foo));
    }

    @Test
    public void valuesSubstitutedIntoTemplateDontNeedToBeStrings() {
        Template template = new Template("Hello {{nonStringValue}} ");
        assertEquals("Hello 3 ", template.render(foo));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingFieldShouldException() {
        Template template = new Template("Hello {{wtf}} ");
        template.render(foo);
    }

    @Test
    public void canSubstituteMultipleValueIntoTemplate() {
        Template template = new Template("Hello {{nonStringValue}} {{value}}");
        assertEquals("Hello 3 World", template.render(foo));
    }

    @Test
    public void canSubstituteValuesFromMethodsIntoTemplate() {
        Template template = new Template("Hello {{getPrivateNonStringValue}} {{getPrivateValue}}");
        assertEquals("Hello 3 World", template.render(foo));
    }

    @Test
    public void canSubstituteValuesFromLists() {
        Template template = new Template("Hello {{#getValues}}{{toString}},{{/getValues}} ");
        assertEquals("Hello 1,2,3, ", template.render(foo));
    }

    @Test
    public void canSubstituteMultipleValuesFromLists() {
        Template template = new Template(
            "Hello {{#getValues}}{{toString}},{{/getValues}} {{getPrivateNonStringValue}} " +
            "{{#getValues}}{{toString}}.{{/getValues}} {{getPrivateValue}} ");
        assertEquals("Hello 1,2,3, 3 1.2.3. World ", template.render(foo));
    }

    @Test
    public void supportsFilteringTheNotLastListValue() {
        Template template = new Template(
            "Hello {{#getValues}}{{toString}}{{#notLast}},{{/notLast}}{{/getValues}} ");
        assertEquals("Hello 1,2,3 ", template.render(foo));
    }

}
