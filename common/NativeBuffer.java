package justream.common;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.nio.channels.SocketChannel;

class NativeBuffer {
    
    private ByteBuffer buffer;
    
    private int size;
    private int head;
    private int tail;

    public NativeBuffer(int size) {
        
	this.buffer = ByteBuffer.allocateDirect(size);
        
	this.size = size;
        this.head = 0;
        this.tail = 0;
    
    }
    
    private void compact(int length) {
        
	if (head == tail) {
            head = 0;
            tail = 0;
            return;
        }

        int pAvailable = size - tail;
        int cAvailable = tail - head;

        if (cAvailable < Util.UNIT_SIZE && pAvailable < Util.UNIT_SIZE && pAvailable < length) {
	    buffer.compact();
            head = 0;
            tail = cAvailable;
        }
    }

    private void switchProducerMode(int length) {
	
	compact(length);

	buffer.limit(size);
        buffer.position(tail);
    }

    private void switchConsumerMode() {
	buffer.position(head);
        buffer.limit(tail);
    }

    public final void reset() {
	buffer.clear();
	head = 0;
	tail = 0;
    }

    public final ByteBuffer getBuffer() {
	return buffer;
    }
    
    public final boolean isEmpty() {
	return (head == tail);
    }
    
    public final int length() {
	return (tail - head);
    }

    public final int transfer(byte[] data, int offset, int length) {

        if (length == 0) return 0;

	switchProducerMode(length);

        int tLen = Math.min(length, size - tail);
	
        buffer.put(data, offset, tLen);

        tail += tLen;

        switchConsumerMode();
	
        return tLen;
    }
    
    public final boolean write(SocketChannel channel) throws IOException {    
	
	if (head == tail) return true;
	
	int wLen = channel.write(buffer);
        head += wLen;
	
	return (head == tail);
    }
}