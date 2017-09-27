
import static org.jctools.util.UnsafeAccess.UNSAFE;

public class {{className}}
        extends {{implementationParent}}
        implements {{flyweightInterface}} {

    public {{className}}({{#constructorParams}}
            {{type}} {{name}} {{#notLast}},{{/notLast}}
        {{/constructorParams}}) {

        super({{#constructorParams}}
            {{name}} {{#notLast}},{{/notLast}}
        {{/constructorParams}});
    }

    {{#fields}}
        public {{type}} get{{name}}() {
            return UNSAFE.get{{unsafeMethodSuffix}}(pointer + {{fieldOffset}}L);
        }

        public void set{{name}}({{type}} value) {
            UNSAFE.put{{unsafeMethodSuffix}}(pointer + {{fieldOffset}}L, value);
        }
    {{/fields}}

}
