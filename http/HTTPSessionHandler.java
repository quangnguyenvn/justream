package justream.http;

import java.io.IOException;

import java.util.TimeZone;
import java.util.Locale;
import java.util.Calendar;
import java.text.SimpleDateFormat;

import jucommon.util.VStream;
import jucommon.util.ByteStream;

import justream.session.Session;
import justream.session.SessionHandler;

public abstract class HTTPSessionHandler extends SessionHandler {
    
    protected abstract void handle(HTTPReqInfo info) throws Exception;
    
    private String contentType;
    private String[] properties;
    private int numProperties;
    
    public HTTPSessionHandler() {
	super();
	numProperties = 0;
    }

    private void setHeaderProperties(ByteStream stream) {

	for (int i = 0; i < numProperties; i++) {
	    HTTPUtil.addHeaderInfo(properties[2*i], properties[2*i + 1], stream);
	}

    }
    
    public final void setProperties(String[] keys, String[] values) {

        int length = keys.length;
        properties = new String[2*length];

        for (int i = 0; i < length; i++) {
            properties[2*i] = keys[i];
            properties[2*i + 1] = values[i];
        }

        numProperties = length;

    }
    
    public final void setHeader(HTTPSession session, HTTPReqInfo info, HTTPRespInfo option) throws Exception {
	
	VStream wStream = session.getWStream();
	
	ByteStream stream = wStream.getStream();

	HTTPAcceptorOption properties = (HTTPAcceptorOption) session.properties;
	
	int respCode = option.getRespCode();

        stream.add(info.protocol);
        stream.add(HTTPUtil.SPACE);
        stream.add(String.valueOf(respCode));
        stream.add(HTTPUtil.SPACE);
        stream.add(option.getRespText());
        stream.add(HTTPUtil.NEW_LINE);
	
	if (respCode == HTTPUtil.RESP_CODE_MOVED_PERMANENTLY) {
	    HTTPUtil.addHeaderInfo(HTTPUtil.LOCATION, option.location, stream); 
	} else {

	    HTTPUtil.addHeaderInfo(HTTPUtil.SERVER, properties.serverName, stream);
	    
	    String connection = info.connection;
	    
	    HTTPUtil.addHeaderInfo(HTTPUtil.CONNECTION, connection, stream);
	    
	    if (connection != null && connection.equalsIgnoreCase(HTTPUtil.KEEP_ALIVE)) {
		HTTPUtil.addHeaderInfo(HTTPUtil.KEEP_ALIVE, "max=" + properties.maxKeepAlive, stream);
	    }
	
	    HTTPUtil.addHeaderInfo(HTTPUtil.DATE, HTTPUtil.getTime(), stream);
	    HTTPUtil.addHeaderInfo(HTTPUtil.LAST_MODIFIED, HTTPUtil.getTime(), stream);
	    HTTPUtil.addHeaderInfo(HTTPUtil.CONTENT_TYPE, option.contentType, stream);
	    
	    if (option.contentEncoding != null) {
		HTTPUtil.addHeaderInfo(HTTPUtil.CONTENT_ENCODING, option.contentEncoding, stream);
	    }
	    
	    setHeaderProperties(stream);
	}
	
	stream.add(HTTPUtil.CONTENT_LENGTH);
	stream.add(HTTPUtil.SUB);
	stream.add(HTTPUtil.SPACE);
	info.respContentLengthPos = stream.getLength();
	
	stream.add(HTTPUtil.EMPTY_CONTENT_LENGTH);
	stream.add(HTTPUtil.NEW_LINE);
	
	stream.add(HTTPUtil.NEW_LINE);
	
	info.respBodyPos = stream.getLength();
    }
    
    public final void handleReq(Object req) throws Exception {
	HTTPReqInfo info = (HTTPReqInfo) req;
	handle(info);
    }
}
