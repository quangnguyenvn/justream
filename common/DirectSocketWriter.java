package justream.common;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.nio.channels.SocketChannel;

class DirectSocketWriter extends SocketWriter{
        
    public DirectSocketWriter(SocketChannel channel) {
	super(channel, 0);
    }
    
    protected void dtaw(byte[] data, int offset, int length, TransferResult tResult) throws IOException {

        ByteBuffer buffer = ByteBuffer.wrap(data, offset, length);

        tResult.tLen = channel.write(buffer);

    }
    
    protected boolean flushBuffer() throws IOException {
        return true;
    }

    public final void handshakeFinished() {
    }
    
    public final int getBufferLength() {
        return 0;
    }
}