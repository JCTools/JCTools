
import static org.jctools.util.UnsafeAccess.UNSAFE;
import org.jctools.channels.spsc.SpscChannelProducer;

public class {{className}}
        extends {{implementationParent}}<{{flyweightInterface}}>
        implements {{flyweightInterface}} {

    public {{className}}({{#constructorParams}}
            {{type}} {{name}} {{#notLast}},{{/notLast}}
        {{/constructorParams}}) {

        super({{#constructorParams}}
            {{name}} {{#notLast}},{{/notLast}}
        {{/constructorParams}});
    }

    public {{flyweightInterface}} currentElement() {
        return this;
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
