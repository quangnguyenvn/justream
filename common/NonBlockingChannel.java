
package justream.common;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.nio.channels.SocketChannel;

import java.net.SocketException;
import java.net.InetSocketAddress;

import jucommon.util.ByteStream;
import jucommon.util.VStream;
import jucommon.util.Queue;

public abstract class NonBlockingChannel {
    
    protected static final int UNIT_SIZE = 1 << 10;
    
    private Queue hsQueue;

    private SocketChannel channel;
    
    private TLSContext tlsContext;
    private TLSEngine tlsEngine;
    
    private SocketWriter writer;
    
    private boolean writeAlarmFlag;
    private int writeBufferSizeAlarm;
    
    protected VStream rStream;       
    
    protected VStream wStream;        
    
    public abstract void onHandshakeFinished();
    public abstract void close(int state, String agent, String msg);
    public abstract boolean isExpired(long currentTime);
    public abstract void update(long currentTime);

    public NonBlockingChannel(SocketChannel channel) {
	this.channel = channel;
	this.tlsContext = null;
	this.tlsEngine = null;
	this.writer = new DirectSocketWriter(channel);
	
	this.rStream = new VStream(UNIT_SIZE);
	this.wStream = new VStream(UNIT_SIZE);
	
	writeAlarmFlag = false;
	writeBufferSizeAlarm = 0;
    }
    
    private TransferResult flushStream(ByteStream stream, boolean force) throws IOException {
	
	byte[] data = stream.getBytes();
	int offset = stream.getOffset();
	int length= stream.getLength();
	
	TransferResult tResult = writer.taw(tlsEngine, data, offset, length, force);
	
	int tLen = tResult.tLen;
	
	if (tLen > 0) {
	    stream.setOffset(offset + tLen);
	}

	return tResult;
    }
    
    private boolean isAlarm() throws IOException {
	
	boolean code;
	
	if (writeAlarmFlag) {
	    ByteStream stream = wStream.getStream();
	    
	    code = (stream.getLength() > writeBufferSizeAlarm);

	    if (code) {
		flushStream(stream, true);
		code = (stream.getLength() > writeBufferSizeAlarm); 
	    }
	} else {
	    code = false;
	}

	return code;
    }
    
    private int taw(byte[] data, int offset, int length, boolean flushFlag) throws IOException {

        ByteStream stream = wStream.getStream();

	boolean code;
	
	TransferResult tResult;
	int tLen;
	
        if (stream.getLength() == 0) {
	    tResult = writer.taw(tlsEngine, data, offset, length, flushFlag);
	    
	    tLen = tResult.tLen;
	    
            if (tLen == length) {
                code = true;
            } else {
                stream.add(data, offset + tLen, length - tLen);
                code = false;
            }
        } else {
            
	    if (isAlarm()) return -1;
	                
	    stream.add(data, offset, length);
            tResult = flushStream(stream, flushFlag);
        }
	
	if (flushFlag || tResult.needFlush) {
	    tResult = writer.flush(tlsEngine);
	} 

	if (tResult.bufferStatus == TransferResult.STATUS_EMPTY) {
	    return 1;
	} else {
	    return 0;
	}
    }
    
    private void process(ByteBuffer buffer, ByteStream stream) throws Exception {
	
        if (tlsEngine != null) {
	    buffer.flip();
            buffer = tlsEngine.unwrap(buffer);

            if (buffer == null) {
                tlsEngine.add(this);
                return;
            }
        }
	
        buffer.flip();
        Util.transfer(buffer, stream);
    }

    protected final void close() {
	rStream = null;
	wStream = null;


        try {
            channel.close();
        } catch (Exception ie) {
        }

        channel = null;
    }
    
    public final TLSContext getTLSContext() {
	return tlsContext;
    }

    public final void setTLSContext(TLSContext tlsContext) {
	this.tlsContext = tlsContext;
    }

    public final TLSEngine getTLSEngine() {
	return tlsEngine;
    }
    
    public final void setTLSEngine(TLSEngine tlsEngine) {
	this.tlsEngine = tlsEngine;
    }

    public final void setHSQueue(Queue queue) {
        hsQueue = queue;
    }


    public final boolean tlsHandshake() throws Exception {
	boolean code = false;
	
	synchronized(tlsEngine) {
	    int hsState = tlsEngine.handshake(); 	
	    
	    if (hsState == TLSEngine.STATE_FINISHED) return true;

	    if (hsState == TLSEngine.STATE_HANDSHAKING || hsState == TLSEngine.STATE_COMPLETED) {
		code = writer.flushTLSOStream(tlsEngine);
	    } 
	    
	    if (code && hsState == TLSEngine.STATE_COMPLETED) {
		hsQueue.add(this);
	    }
	}
	
	return code;
    }

    public final boolean isClosed() {
        return (channel == null);
    }

    public final SocketChannel getChannel() {                                                                                     
        return channel;  
    }
    
    public final String getRemoteAddress() throws IOException {
	InetSocketAddress address = (InetSocketAddress) channel.getRemoteAddress();
	return address.getHostString();
    }
    
    public final VStream getRStream() {
	return rStream;
    }
    
    public final VStream getWStream() {
        return wStream;    
    }
            
    public final void setWriteAlarmFlag(boolean flag) {
	writeAlarmFlag = flag;
    }

    public final void setWriteBufferSizeAlarm(int size) {
	 writeAlarmFlag = true;
	 writeBufferSizeAlarm = size;
    }
    
    public final void switchToNativeWriter(int bufferSize, int flushSize) {
	writer = new NativeSocketWriter(channel, tlsEngine, bufferSize, flushSize);
    }
    
    public final void tlsHandshakeFinished() throws Exception {
	
	if (tlsEngine == null) return;

	synchronized(tlsEngine) {
	    
	    boolean code = tlsEngine.handshakeFinished(rStream.getStream());

	    if (!code) return;
			
	    writer.handshakeFinished();

	    wStream.getStream().reset();
	
	    onHandshakeFinished();
	}

    }
    
    public final int getPendingDataLength() {
		
	ByteStream stream = wStream.getStream();
	
	int length = stream.getLength();

	if (tlsEngine != null) {
	    stream = tlsEngine.getOStream();
	    length += stream.getLength();
	}

	length += writer.getBufferLength();
	
	return length;

    }

    public final int read(ByteBuffer buffer) throws Exception {
	
	if (tlsContext != null && tlsEngine == null) {
	    tlsEngine = tlsContext.createTLSEngine();
	    tlsEngine.handshakeReady();
	}
	
	ByteStream stream = rStream.getStream();

	while (true) {
	    buffer.clear();
	
	    int length = channel.read(buffer);
	
	    if (length < 0) return length;

	    if (length == 0) break;
	    
	    process(buffer, stream);

	    if (buffer.capacity() > 0) break;
	}
	
	return stream.getLength();
    }
    
    public final int write() throws IOException {

        ByteStream stream = wStream.getStream();
	
	int length = stream.getLength();
	
	TransferResult tResult = null;
	
	if (length > 0) {
	    tResult = flushStream(stream, true);
        }
	
	if (tResult != null && tResult.tLen != length) return 0;

	tResult = writer.flush(tlsEngine);
	
	if (tResult.bufferStatus == TransferResult.STATUS_EMPTY) {
	    return 1;
	} else {
	    return 0;
	}
    }

    public final int write(byte[] data, int offset, int length) throws IOException {
	return taw(data, offset, length, true);
    }
    
    public final int enqueue(byte[] data, int offset, int length) throws IOException {
	return taw(data, offset, length, false);        
    }
}