package justream.http;

import java.io.IOException;

import java.nio.channels.SocketChannel;

import jucommon.util.ByteStream;
import jucommon.util.VStream;

import justream.session.TemporarySession;

public class HTTPSession extends TemporarySession {
    
    private static final int MAX_FIRST_LINE_LENGTH = 1 << 12;

    private String connection;
        
    private Object req;

    public Object ext;
    
    public HTTPSession(String id, SocketChannel channel, long timeout) {
	super(id, channel, timeout);
    }
    
    private int getReqDescription(byte[] data, int offset, int length) throws Exception {
	
	int endPos = offset + length;
	int currentPos = offset;
	
	while (true) {
	    if (currentPos == endPos) return offset;
	    
	    byte val = data[currentPos++];
	    
	    if (val == HTTPUtil.LF) break;
	}
	
	endPos = currentPos;
	
	while (true) {
	    
	    byte val = data[--endPos];
		
	    if (val != HTTPUtil.CR && val != HTTPUtil.LF && val != HTTPUtil.SPACE) break;
	    
	}
	
	int firstLineLength = endPos - offset + 1;

	HTTPReqInfo reqInfo = HTTPReqInfo.extractReqDescription(data, offset, firstLineLength);
	
	if (reqInfo == null && firstLineLength > MAX_FIRST_LINE_LENGTH) {
	    throw new Exception("Incorrect header req: " + new String(data, offset, firstLineLength)); 
	}

	req = reqInfo;
		
	return currentPos;
    }
    
    private boolean processNewLine(byte[] data, int offset, int length, int middle) {
	
	while (true) {
	    
	    if (length == 0) return true;
	    
	    byte val = data[offset];
	    if (val == HTTPUtil.CR || val == HTTPUtil.LF || val == HTTPUtil.SPACE) {
		offset++;
		length--;
		middle--;
		continue;
	    }  
	    break;
	}
	
	String key = new String(data, offset, middle);
	
	int end = offset + length - 1;
	
	while (data[end] == HTTPUtil.CR || data[end] == HTTPUtil.LF) {
	    end--;
	}
	
	int valStartPos = offset + middle + 1;
	
	String value = new String(data, valStartPos, end - valStartPos + 1).trim();
	
	HTTPReqInfo.setHeaderProperty((HTTPReqInfo) req, key, value);
	
	return false;
    }
    
    public void onHandshakeFinished() {
    }

    public final void setProperties(HTTPAcceptorOption option) {
	this.properties = option;
    }

    public final Object processNewData() throws Exception {
	
	ByteStream stream = rStream.getStream();

	byte[] data = stream.getBytes();
	int offset = stream.getOffset();
	int length = stream.getLength();
	
	int currentPos = offset;
	
	currentPos = getReqDescription(data, offset, length);
	    
	if (req == null) return null;
	
	debugStream("<--: {}", stream, offset);

	int endPos = offset + length;
	int startPos = currentPos;
	
	int middlePos = -1;
	
	boolean isLastHeaderLine = false;

	while (true) {
	    
	    if (currentPos == endPos) return null;
	    
	    byte val = data[currentPos];
	    
	    if (val == HTTPUtil.SUB) {
		if (middlePos == -1) {
		    middlePos = currentPos;
		}
	    } else if (val == HTTPUtil.LF) {
	        isLastHeaderLine = processNewLine(data, startPos, currentPos - startPos, middlePos - startPos);
		startPos = currentPos + 1;
		middlePos = -1;
	    }
	    
	    currentPos++;
	    
	    if (isLastHeaderLine) break;
	}
	
	HTTPReqInfo info = (HTTPReqInfo) req;
	req = null;
	
	connection = info.connection;

	if (connection != null) connection = connection.toLowerCase();
	
	int reqLength = currentPos - offset + info.contentLength;
	
	if (length >= reqLength) {
	    info.stream = new ByteStream(stream.getBytes(), offset, reqLength);     
	    stream.setOffset(offset + reqLength);
	    HTTPReqInfo.setSrcAddress(info, getRemoteAddress());
	    info.session = this;
	    return info;
	} else {
	    debug("input incompleted. Length {} while expected length {}: {}", 
		  length, reqLength, new String(data, offset, length));
	    return null;
	}
    }
    
    public final boolean isCancelAfterPolled() {
	return (connection == null || !connection.equalsIgnoreCase(HTTPUtil.KEEP_ALIVE));
    }
    
    public final boolean isCloseAfterHandled() {
	
	if (connection == null) return true;
	
	String[] parts = connection.split(",");
	
	int length = parts.length;
	
	for (int i = 0; i < length; i++) {
	    String part = parts[i];

	    if (part.equalsIgnoreCase(HTTPUtil.KEEP_ALIVE) ||
		part.equalsIgnoreCase(HTTPUtil.UPGRADE)) {
		return false;
	    }
	}

	return true;
    }
    
    public final boolean isUpgrading() {

	if (connection == null) return false;
	
	String[] parts = connection.split(",");

        int length = parts.length;

        for (int i = 0; i < length; i++) {
            String part = parts[i];

            if (part.equalsIgnoreCase(HTTPUtil.UPGRADE)) {
                return true;
            }
        }

        return false;
    }

    public final void complete(HTTPReqInfo info) throws IOException {
		
	ByteStream stream = wStream.getStream();
	
	if (info.respContentLengthPos >= 0) {
	    int bodyLength = stream.getLength() - info.respBodyPos;
	    stream.fill(info.respContentLengthPos, String.valueOf(bodyLength));
	}
	
	super.tryComplete();
    }
}
