package justream.common;

import java.io.IOException;

import java.nio.channels.SocketChannel;

import jucommon.util.ByteStream;

class NativeSocketWriter extends SocketWriter {
        
    private NativeBuffer nativeBuffer;
    
    public NativeSocketWriter(SocketChannel channel, TLSEngine tlsEngine, int bufferSize, int flushSize) {
	super(channel, flushSize);
	this.nativeBuffer = new NativeBuffer(bufferSize);
    }
    
    protected void dtaw(byte[] data, int offset, int length, TransferResult tResult) throws IOException {       

        NativeBuffer buffer = nativeBuffer;

        int totalTransferLength = 0;

        while (true) {

            int remainLength = length - totalTransferLength;

            int tLen = buffer.transfer(data, offset + totalTransferLength, remainLength);

            totalTransferLength += tLen;

            if (totalTransferLength == length) {
		tResult.bufferStatus = TransferResult.STATUS_NORMAL;
		break;
	    } else if (!buffer.write(channel)) {
		tResult.bufferStatus = TransferResult.STATUS_BUFFERED;
                break;
            } else {
                continue;
            }
        }
	
	tResult.tLen = totalTransferLength;
    }
    
    protected boolean flushBuffer() throws IOException {
        return nativeBuffer.write(channel);
    }

    public final int getBufferLength() {
	return nativeBuffer.length();
    }
    
    public final void handshakeFinished() {
        nativeBuffer.reset();
    }
}