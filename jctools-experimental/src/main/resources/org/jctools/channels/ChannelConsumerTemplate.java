
import static org.jctools.util.UnsafeAccess.UNSAFE;
import org.jctools.channels.spsc.SpscChannelConsumer;
import java.nio.ByteBuffer;
import org.jctools.channels.ChannelReceiver;

public class {{className}}
        extends {{implementationParent}}<{{flyweightInterface}}>
        implements {{flyweightInterface}} {

    public {{className}}(
        final ByteBuffer buffer,
        final int capacity,
        final int messageSize,
        final ChannelReceiver<{{flyweightInterface}}> receiver) {

        super(buffer, capacity, messageSize, receiver);
    }

    public boolean read() {
        final long pointer = readAcquire();
        if (pointer == EOF) {
            return false;
        }
        this.pointer = pointer;
        receiver.accept(this);
        readRelease(pointer);
        return true;
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
