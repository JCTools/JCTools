
import static org.jctools.util.UnsafeAccess.UNSAFE;
import org.jctools.channels.spsc.SpscChannelConsumer;
import java.nio.ByteBuffer;
import org.jctools.channels.ChannelReceiver;
import java.util.function.LongConsumer;

public class {{className}}
        extends {{implementationParent}}<{{flyweightInterface}}>
        implements {{flyweightInterface}} {

    private final LongConsumer readPointer;

    public {{className}}(
        final ByteBuffer buffer,
        final int capacity,
        final int messageSize,
        final ChannelReceiver<{{flyweightInterface}}> receiver) {

        super(buffer, capacity, messageSize, receiver);
        this.readPointer = this::onRead;
    }

    private void onRead(long pointer){
        this.pointer = pointer;
        this.receiver.accept(this);
    }

    public final int read(int limit) {
        return read(this.readPointer, limit);
    }

    public final boolean read() {
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
