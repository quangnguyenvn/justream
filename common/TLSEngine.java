package justream.common;

import java.io.IOException;

import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import jucommon.util.NotifiableQueue;
import jucommon.util.ByteStream;

public class TLSEngine {
    
    private static final ByteBuffer BUFFER_EMPTY;
    
    public static final int STATE_INIT = 0;
    public static final int STATE_READY = 1;
    public static final int STATE_HANDSHAKING = 2;
    public static final int STATE_COMPLETED = 3;
    public static final int STATE_FINISHED = 4;

    static {
	BUFFER_EMPTY = ByteBuffer.allocate(0);
    }
    
    private SSLEngine engine;
    
    private NotifiableQueue queue;
    
    private ByteStream iStream;
    private ByteStream oStream;
        
    private int handshakeState;
    
    private static final ThreadLocal<TLSBuffer> iBuffer = new ThreadLocal<TLSBuffer>() {
        @Override
        protected TLSBuffer initialValue() {
            return TLSContext.createTLSBuffer();
        }
    };

    private static final ThreadLocal<TLSBuffer> oBuffer = new ThreadLocal<TLSBuffer>() {
        @Override
        protected TLSBuffer initialValue() {
            return TLSContext.createTLSBuffer();
        }
    };
    
    public TLSEngine(SSLEngine engine, NotifiableQueue queue) {
	this.engine = engine;
	this.queue = queue;
	iStream = new ByteStream(Util.UNIT_SIZE);
	oStream = new ByteStream(Util.UNIT_SIZE);
		
	handshakeState = STATE_INIT;
    }
    
    private static ByteBuffer getEmptyReadBuffer() {
	return BUFFER_EMPTY;
    }
    
    private static TLSBuffer getIBuffer() {
	TLSBuffer tlsBuffer = iBuffer.get();
	tlsBuffer.clear();
	return tlsBuffer;
    }

    private static TLSBuffer getOBuffer() {
        TLSBuffer tlsBuffer = oBuffer.get();
        tlsBuffer.clear();
        return tlsBuffer;
    }

    private void beginHandshake() throws SSLException {
        handshakeState = STATE_HANDSHAKING;
        engine.beginHandshake();
    }

    private void processTask() {
	Runnable task = engine.getDelegatedTask();
	if (task != null) {
	    task.run();
	}
    }
    
    private boolean hsWrap() throws SSLException {

	TLSBuffer dest = getOBuffer();
		
	ByteBuffer buffer = dest.getBuffer();

	SSLEngineResult result = engine.wrap(BUFFER_EMPTY, buffer);
            
	Status status = result.getStatus();
	
	if (status == Status.BUFFER_OVERFLOW) {
	    dest.resize();
	} else {
	    dest.flip();
	    Util.transfer(buffer, oStream);
	}

	return (status == Status.OK || status == Status.BUFFER_OVERFLOW);
    }

    private boolean hsUnwrap() throws SSLException {
	
	ByteStream stream = iStream;
	
	Status status;
	
	int length = stream.getLength();
	
	if (length == 0) return false;
	
	ByteBuffer src = ByteBuffer.wrap(stream.getBytes(), stream.getOffset(), length);
	TLSBuffer dest = getIBuffer();
		
	SSLEngineResult result = engine.unwrap(src, dest.getBuffer());
	
	status = result.getStatus();
	
	if (status == Status.BUFFER_OVERFLOW) {
	    dest.resize();
	}
	
	stream.setOffset(src.position());
	
	return (status == Status.OK || status == Status.BUFFER_OVERFLOW);
    }
    
    private void wrap(ByteBuffer src, TLSBuffer dest) throws SSLException {
	
	Status status = Status.OK;

        while (src.hasRemaining()) {
            SSLEngineResult result = engine.wrap(src, dest.getBuffer());
	    
            status = result.getStatus();
	    
	    if (status != Status.OK) {
		if (status == Status.BUFFER_OVERFLOW) {
		    dest.resize();
		} else {
		    break;
		}
	    }
        }
    }

    private void unwrap(ByteBuffer src, TLSBuffer dest) throws SSLException {
	
        Status status = Status.OK;
	
	while (true) {
	    
	    SSLEngineResult result = engine.unwrap(src, dest.getBuffer());
	    
	    status = result.getStatus();
	    
	    if (status == Status.OK) {
		if (src.remaining() == 0) break;
	    } else {
		if (status == Status.BUFFER_OVERFLOW) {
		    dest.resize();
		} else {
		    break;
		}
	    }
	}
    }
    
    private ByteBuffer unwrap() throws SSLException {

        TLSBuffer dest = getIBuffer();

        ByteStream stream = iStream;

	ByteBuffer src = ByteBuffer.wrap(stream.getBytes(), stream.getOffset(), stream.getLength());

	unwrap(src, dest);
	
	stream.setOffset(src.position());
	
	return dest.getBuffer();
    }    

    public final ByteStream getOStream() {
	return oStream;
    }
                
    public final void setUseClientMode(boolean mode) {
	engine.setUseClientMode(mode);    
    }
        
    public final void add(Object object) {
	queue.add(object);
    }
    
    public final boolean isHandshakeCompleted() {
	return (handshakeState == STATE_FINISHED ||
		handshakeState == STATE_COMPLETED);
    }

    public final boolean isHandshakeFinished() {
	return (handshakeState == STATE_FINISHED);
    }
    
    public final void handshakeReady() {
	handshakeState = STATE_READY;
    }

    public final boolean handshakeFinished(ByteStream rStream) throws SSLException {
	
	ByteBuffer buffer = unwrap();
	    
	buffer.flip();
	Util.transfer(buffer, rStream);
	
	if (handshakeState == STATE_FINISHED) return false;
	    
	handshakeState = STATE_FINISHED;
	
	ByteStream stream = oStream;
	stream.reset();
		
	return true;
    }
        
    public final int handshake() throws SSLException, IOException {
	
	if (handshakeState == STATE_INIT || 
	    handshakeState == STATE_COMPLETED ||
	    handshakeState == STATE_FINISHED) {
	    
	    return handshakeState;
	
	}
	
	if (handshakeState == STATE_READY) {
	    beginHandshake();
	}
	
	boolean flag = true;
	
	HandshakeStatus handshakeStatus = null; 

	while (flag) {
	    
	    handshakeStatus = engine.getHandshakeStatus();
	    
	    switch (handshakeStatus) {
            case NEED_UNWRAP:
		flag = hsUnwrap();
                break;
            case NEED_WRAP:
		flag = hsWrap();
                break;
            case NEED_TASK:
		processTask();
                break;
            case FINISHED:
	    case NOT_HANDSHAKING:
		flag = false;
		break;
	    default://should never happen
		flag = false;
		break;
            }
	}
	
	if (handshakeStatus == HandshakeStatus.NOT_HANDSHAKING || handshakeStatus == HandshakeStatus.FINISHED) {
	    handshakeState = STATE_COMPLETED;
	}
	
	return handshakeState;
    }
    
    public final ByteBuffer wrap(byte[] bytes, int offset, int length) throws SSLException {
	ByteBuffer src = ByteBuffer.wrap(bytes, offset, length);
	
	TLSBuffer dest = getOBuffer();
	
	wrap(src, dest);
		
	return dest.getBuffer();
    }
    
    public final ByteBuffer unwrap(ByteBuffer data) throws SSLException {
	
	ByteStream stream = iStream;
	
	if (!isHandshakeFinished()) {
	    synchronized(this) {
		Util.transfer(data, stream);
	    }
	    return null;
	}

	ByteBuffer src;
	    
	int length = stream.getLength();

	if (length > 0) {
	    Util.transfer(data, stream);
	    length = stream.getLength();
	    src = ByteBuffer.wrap(stream.getBytes(), stream.getOffset(), length);
	} else {
	    src = data;
	}
	
	TLSBuffer dest = getIBuffer();
	
	unwrap(src, dest);
	
	if (src == data) {
	    Util.transfer(src, stream);  
	} else {
	    stream.setOffset(src.position());
	}
	
	return dest.getBuffer();
    }
}