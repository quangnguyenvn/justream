package justream.ws;

import java.io.IOException;

import jucommon.util.ByteStream;

import justream.common.State;
import justream.common.NonBlockingChannel;

import justream.session.Session;

import justream.http.HTTPSession;

public abstract class WSSession extends Session {
    
    private String path;

    private WSMessage wsMsg;
    
    protected abstract void onConnect();
    
    protected abstract void onPing();

    protected abstract void onPong();

    protected abstract void onText(String msg);

    protected abstract void onBinary(byte[] buf, int offset, int len);
    
    protected abstract void onClose();
    
    protected abstract void onError(Throwable t);

    public WSSession(HTTPSession session) {
	
	super(session.getID(), session.getChannel(), 0);
	
	this.path = null;

	rStream = session.getRStream();
	wStream = session.getWStream();
	setTLSEngine(session.getTLSEngine());

	setLogger(session.getLogger());

	wsMsg = new WSMessage();
	
    }
    
    private void onMessage() throws Exception {
	
	switch(wsMsg.opCode) {
	
	case WSUtil.OP_CODE_TEXT:
	    onText(new String(wsMsg.msg, wsMsg.offset, wsMsg.length));
	    break;
	case WSUtil.OP_CODE_BINARY:
	    onBinary(wsMsg.msg, wsMsg.offset, wsMsg.length);
	    break;
	case WSUtil.OP_CODE_CLOSE:
	    onClose();
	    close(State.SUCCESS, "WSS", "Close msg recv");
	    break;
	case WSUtil.OP_CODE_PING:
	    onPing();
	    break;
	case WSUtil.OP_CODE_PONG:
	    onPong();
	    break;
	default:
	    break;
    
	}
    }
    
    private int processMsg(byte[] bytes, int offset, int length) throws Exception {
	
	int msgLength = WSUtil.processMsg(bytes, offset, length, wsMsg);
	
	if (wsMsg.isFIN) {
	    onMessage();
	    wsMsg.reset();
	} 
	
	return msgLength;
    }
    
    public final String getPath() {
	return path;
    }

    public final void setPath(String path) {
	if (this.path == null) {
	    this.path = path;
	}
    }

    public final void setMaxMsgSize(int size) {
	wsMsg.setMaxMsgSize(size);
    }

    public final Object processNewData() throws Exception {
	
	ByteStream stream = rStream.getStream();

        byte[] bytes = stream.getBytes();
        int offset = stream.getOffset();
        int length = stream.getLength();
	
	while (length > 0) {
	    
            int pLength = processMsg(bytes, offset, length);
	    
	    if (pLength == 0) break;

	    offset += pLength;
	    stream.setOffset(offset);
	    
	    length = stream.getLength();

	    if (length == 0) {
		stream.reset();
		break;
	    }
        }
	
	return null;
    }
    
    public final int sendPing() throws IOException {           
        byte[] msg = WSUtil.prepareWSPingMsg();
        return write(msg, 0, msg.length);
    }
    
    public final int sendPong() throws IOException {
        byte[] msg = WSUtil.prepareWSPongMsg();
        return write(msg, 0, msg.length);
    }

    public final int sendBytes(byte[] data, int offset, int len) throws IOException {
	byte[] msg = WSUtil.prepareWSBinaryMsg(data, offset, len);
	return write(msg, 0, msg.length);    
    }
    
    public final int sendString(String data) throws IOException {
	byte[] msg = WSUtil.prepareWSTextMsg(data);
	return write(msg, 0, msg.length);
    }

    public final int enqueueBytes(byte[] data, int offset, int len) throws IOException {
        byte[] msg = WSUtil.prepareWSBinaryMsg(data, offset, len);
        return enqueue(msg, 0, msg.length);
    }

    public final int enqueueString(String data) throws IOException {
        byte[] msg = WSUtil.prepareWSTextMsg(data);
        return enqueue(msg, 0, msg.length);
    }
}