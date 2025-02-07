package justream.tls;

import java.io.IOException;

import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import jucommon.util.Queue;
import jucommon.util.ByteStream;

import justream.common.NonBlockingChannel;

public class TLSEngine {
    
    private SSLEngine engine;
    
    private Queue queue;

    private NonBlockingChannel channel;
    
    private ByteStream dataRStream;

    private ByteBuffer workingBuffer;

    public TLSEngine(SSLEngine engine, Queue queue, ByteBuffer workingBuffer) {
	this.engine = engine;
	this.queue = queue;
	this.workingBuffer = workingBuffer;
    }
    
    private void processTasks() {
        Runnable task;
        int index = 1;
        while ((task = engine.getDelegatedTask()) != null) {
            System.out.println("Running task #" + index++);
            task.run();
        }
    }
    
    private int hsWrap() throws SSLException {

	ByteBuffer src = workingBuffer;
        ByteBuffer dest = workingBuffer;
        dest.clear();

        HandshakeStatus hsStatus = HandshakeStatus.NEED_WRAP;

        while (hsStatus == HandshakeStatus.NEED_WRAP) {
	    
            SSLEngineResult result = engine.wrap(src, dest);
            hsStatus = result.getHandshakeStatus();

        }
	
	int wrapLength = dest.position();

	if (wrapLength > 0) {
	    dest.flip();
	    NonBlockingChannel.transfer(dest, wrapLength, channel.getWStream());
	}

	return wrapLength;
    }
    
    private int hsUnwrap() throws SSLException {
	
	int numUnwrapMsgs = 0;

        ByteStream stream = dataRStream;
        ByteStream rStream = channel.getRStream();

        int offset = rStream.getOffset();
        int length = rStream.getLength();

        System.out.println("Try unwrap from " + offset + ", " + length);

        ByteBuffer src = ByteBuffer.wrap(rStream.getBytes(), offset, length);
        ByteBuffer dest = workingBuffer;
        dest.clear();

        HandshakeStatus hsStatus = HandshakeStatus.NEED_UNWRAP;

        Status status = Status.OK;

	while (hsStatus == HandshakeStatus.NEED_UNWRAP && status == Status.OK) {

            SSLEngineResult result = engine.unwrap(src, dest);

            hsStatus = result.getHandshakeStatus();
	    
            status = result.getStatus();
	    
	    System.out.println("Status " + status + ", HSStatus " + hsStatus);

            if (status == Status.OK) {
		numUnwrapMsgs++;
	    }

        }
	
	rStream.setOffset(src.position());
	
	dest.flip();
        NonBlockingChannel.transfer(dest, dest.position(), stream);
	
	System.out.println("Hanshake status: " + hsStatus + ", num unwrap msgs: " + numUnwrapMsgs);
        return numUnwrapMsgs;
    }
    
    public final void setDataRStream(ByteStream stream) {
	dataRStream = stream;
    }

    public final void setUseClientMode(boolean mode) {
	engine.setUseClientMode(mode);    
    }
    
    public final void setChannel(NonBlockingChannel channel) {
	this.channel = channel;
    }
    
    public final void beginHandshake() throws SSLException {
	engine.beginHandshake();
    }
    
    public boolean handshake() throws SSLException, IOException {

        HandshakeStatus handshakeStatus = engine.getHandshakeStatus();

        boolean flag = (handshakeStatus != HandshakeStatus.FINISHED &&
                        handshakeStatus != HandshakeStatus.NOT_HANDSHAKING);
	
	while (flag) {
	    
	    switch (handshakeStatus) {
            case NEED_UNWRAP:
                System.out.println("Need unwrap");
		flag = (hsUnwrap() > 0);
                break;
            case NEED_WRAP:
		System.out.println("Need wrap");
                hsWrap();
                break;
            case NEED_TASK:
		System.out.println("Need process tasks");
                processTasks();
                break;
            case FINISHED:
            case NOT_HANDSHAKING:
		System.out.println("Handshake complete");
                flag = false;
                break;
	    default:
		System.out.println("Handshake status: " + handshakeStatus);
		break;
            }

	    handshakeStatus = engine.getHandshakeStatus();
        }

	return (channel.write() == 1);
    }

    public final void processNewData() {
	
	HandshakeStatus hsStatus = engine.getHandshakeStatus();
	
	try {
	    
	    if (hsStatus == SSLEngineResult.HandshakeStatus.FINISHED ||
		hsStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
		unwrap();
	    } else {
		queue.add(this, 0);
	    }
	} catch (SSLException se) {
	    System.out.println("SSLException message: " + se.getMessage());
	}
    }
    
    public final boolean handshakeComplete() {
	HandshakeStatus hsStatus = engine.getHandshakeStatus();
	
	return (hsStatus == SSLEngineResult.HandshakeStatus.FINISHED ||
		hsStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING);
    }

    public final int wrap(byte[] bytes, int offset, int length, ByteBuffer buffer) throws SSLException {
	ByteBuffer src = ByteBuffer.wrap(bytes, offset, length);
	ByteBuffer dest = buffer;
	
	Status status = Status.OK;
	
	while (src.hasRemaining() && status == Status.OK) {
	    SSLEngineResult result = engine.wrap(src, dest);
	    status = result.getStatus();
	}

	int wrapLength = dest.position();
	if (wrapLength > 0) {
	    dest.flip();
	    NonBlockingChannel.transfer(dest, wrapLength, channel.getWStream());
	} 

	return wrapLength;
    }

    public final int unwrap() throws SSLException {

        ByteStream stream = dataRStream;
        ByteStream rStream = channel.getRStream();

        int offset = rStream.getOffset();
        int length = rStream.getLength();

        ByteBuffer src = ByteBuffer.wrap(rStream.getBytes(), offset, length);
        ByteBuffer dest = workingBuffer;
        dest.clear();

        Status status = Status.OK;

        while (status == Status.OK) {

            SSLEngineResult result = engine.unwrap(src, dest);

            status = result.getStatus();

        }

        rStream.setOffset(src.position());

        int unwrapLength = dest.position();

        if (unwrapLength > 0) {
            dest.flip();
	    NonBlockingChannel.transfer(dest, unwrapLength, stream);
        }
	
	return unwrapLength;
    }
}