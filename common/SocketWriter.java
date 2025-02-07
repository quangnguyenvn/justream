package justream.common;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.nio.channels.SocketChannel;

import jucommon.util.ByteStream;

abstract class SocketWriter {
    
    private TransferResult tResult;

    protected SocketChannel channel;
    
    protected int flushSize;

    protected abstract void dtaw(byte[] data, int offset, int length, TransferResult tResult) throws IOException;
    
    protected abstract boolean flushBuffer() throws IOException;
    
    public abstract void handshakeFinished();
        
    public abstract int getBufferLength();

    public SocketWriter(SocketChannel channel, int flushSize) {
	this.tResult = new TransferResult();
	this.channel = channel;
	this.flushSize = flushSize;
    }
    
    private boolean flushStream(ByteStream stream, TransferResult result) throws IOException {
		
	byte[] data = stream.getBytes();
	
	int offset = stream.getOffset();
	
	int length = stream.getLength();

	dtaw(data, offset, length, result);
	
	int tLen = result.tLen;
	
        stream.setOffset(offset + tLen);
	
	return (tLen == length);
    } 
    
    public final boolean flushTLSOStream(TLSEngine tlsEngine) throws IOException {
	
	ByteStream stream = tlsEngine.getOStream();

	if (stream.getLength() == 0) return true;
	
	TransferResult result = tResult;
        result.reset();

	return flushStream(stream, result);
    
    }

    public final TransferResult taw(TLSEngine tlsEngine, byte[] data, int offset, int length, 
				    boolean flushFlag) throws IOException {
	
	TransferResult result = tResult;
	result.reset();
	
	if (tlsEngine == null) {
	    dtaw(data, offset, length, result);
	    return result;
	}
	
        if (!flushTLSOStream(tlsEngine)) {
	    result.tLen = 0;
	    return result;
	}
	
	if (tlsEngine.isHandshakeCompleted()) {
	    
	    if (flushFlag || length > flushSize) {
		
		ByteBuffer dest = tlsEngine.wrap(data, offset, length);
		
		int tlsLength = dest.position();

		dest.flip();
		
		byte[] tlsData = dest.array();
		int tlsOffset = dest.arrayOffset();
		
		dtaw(tlsData, tlsOffset, tlsLength, result);
		
		int tlsTLen = result.tLen;
		result.tLen = length;
		
		dest.position(tlsOffset + tlsTLen);
		
		int remaining = dest.remaining();
		
		if (remaining > 0) {
		    Util.transfer(dest, tlsEngine.getOStream());
		}

	    } else {
		result.tLen = 0;
	    }
	    
	} else {
	    dtaw(data, offset, length, result);
	}
	
	result.needFlush = (result.bufferStatus == TransferResult.STATUS_NORMAL && getBufferLength() >= flushSize);
	return result;
    }

    public final TransferResult flush(TLSEngine tlsEngine) throws IOException {
	
	TransferResult result = tResult;
	result.reset();
	
	ByteStream tlsOStream;
	int tlsOStreamLength;

	if (tlsEngine == null) {
	    tlsOStream = null;
	    tlsOStreamLength = 0;
	} else {
	    tlsOStream = tlsEngine.getOStream();
	    tlsOStreamLength = tlsOStream.getLength();
	}
	
	boolean code = (tlsOStreamLength == 0);

	if (code && getBufferLength() == 0) return result;
		
	while (true) {
	    
	    if (!code && tlsOStream != null) {
		code = flushStream(tlsOStream, result);
	    }
	    
	    boolean wCode = flushBuffer();

	    if (!wCode) {
		result.bufferStatus = TransferResult.STATUS_BUFFERED;
		break;
	    }

	    if (code) break;
	}
	
	result.needFlush = (result.bufferStatus == TransferResult.STATUS_NORMAL && getBufferLength() >= flushSize); 
	return result;
    }
}