package justream.ws;

import java.io.IOException;

import java.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import jucommon.util.ByteStream;

import justream.common.State;
import justream.common.Distributer;

import justream.session.ReqInfo;
import justream.session.Session;
import justream.session.SessionHandler;

import justream.http.*;

public abstract class WSSessionHandler extends SessionHandler {
    
    private static final String MAGIC_KEY = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    
    private Distributer distributer;  

    protected abstract WSSession upgrade(HTTPSession session) throws Exception;
    
    private static final ThreadLocal<ByteStream> localStream = new ThreadLocal<ByteStream>() {
        @Override
        protected ByteStream initialValue() {
            return new ByteStream(1 << 8);
        }
    };

    public WSSessionHandler() throws IOException {
	super();
	distributer = new WSSessionsDistributer();
    }
    
    private static byte[] base64Encode(byte[] bytes) {
        byte[] encodedBytes = Base64.getEncoder().encode(bytes);
        return encodedBytes;
    }
    
    private static String getWebSocketAcceptKey(String key) {
	
	ByteStream stream = localStream.get();
	stream.reset();
	stream.add(key);
	stream.add(MAGIC_KEY);
		
	byte[] bytes = stream.getBytes();
	int offset = stream.getOffset();
	int length = stream.getLength();

	MessageDigest md;
	try {
	    md = MessageDigest.getInstance("SHA-1");
	} catch (NoSuchAlgorithmException e) {
	    return null;
	}

	md.update(bytes, offset, length);

	byte[] sha1 = md.digest();
	byte[] base64 = base64Encode(sha1);
	
	return new String(base64);
    }
    
    public final void handleReq(Object req) {
	
	HTTPReqInfo info = (HTTPReqInfo) req;

        HTTPSession session = (HTTPSession) info.getSession();
	
        ByteStream stream = session.getWStream().getStream();
	int msgOffset = stream.getOffset() + stream.getLength();

	HTTPAcceptorOption properties = (HTTPAcceptorOption) session.properties;
	
	stream.add(info.protocol);
	stream.add(HTTPUtil.SPACE);
	stream.add(String.valueOf(HTTPUtil.RESP_CODE_SWITCHING_PROTOCOLS));
	stream.add(HTTPUtil.SPACE);
	stream.add(HTTPUtil.RESP_TEXT_SWITCHING_PROTOCOLS);
	stream.add(HTTPUtil.NEW_LINE);
	
	HTTPUtil.addHeaderInfo(HTTPUtil.CONNECTION, HTTPUtil.UPGRADE, stream);
	info.respContentLengthPos = -1;
	
	HTTPUtil.addHeaderInfo(HTTPUtil.UPGRADE, "websocket", stream);
	
	String acceptKey = getWebSocketAcceptKey(info.webSocketKey);
	
	HTTPUtil.addHeaderInfo(HTTPUtil.WEB_SOCKET_ACCEPT, acceptKey, stream);

	stream.add(HTTPUtil.NEW_LINE);
	
	session.debugStream("-->: {}", stream, msgOffset);

	try {
	    
	    session.complete(info);
	    
	    WSSession wsSession = upgrade(session);
	    
	    wsSession.setPath(info.path);

	    wsSession.getWStream().store();
	    
	    distributer.distribute(wsSession);
	    
	} catch (Exception e) {
	    session.close(State.ERROR, "WSUE", e.getMessage());
	}
    }
}
