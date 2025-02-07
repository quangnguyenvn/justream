package justream.common;

import java.nio.ByteBuffer;

public class TLSBuffer {

    private ByteBuffer buffer;
    
    public TLSBuffer(int size) {
	buffer = ByteBuffer.allocate(size);
    }

    public final ByteBuffer getBuffer() {
	return buffer;
    }
    
    public final void resize() {
	int size = buffer.capacity();

        ByteBuffer newBuffer = ByteBuffer.allocate(size << 1);

        newBuffer.put(buffer.array(), buffer.arrayOffset(), buffer.position());

        buffer = newBuffer;
    }
    
    public final void flip() {
	buffer.flip();
    }
    
    public final void clear() {
	buffer.clear();
    }
    
}