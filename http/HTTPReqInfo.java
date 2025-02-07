package justream.http;

import jucommon.util.ByteStream;

import justream.session.Session;
import justream.session.SessionHandler;
import justream.session.ReqInfo;

public class HTTPReqInfo implements ReqInfo {
    
    private static final int DEFAULT_PARAMS = 1 << 3;
    
    public static final int ENCODING_NONE = 0;      
    public static final int ENCODING_GZIP = 1;      
    
    public static final String ENCODING_GZIP_NAME = "gzip";

    private SessionHandler sHandler;
    
    public String srcAddress;

    public Session session;
    
    public ByteStream stream;

    public String[] params;   
    public int paramsPos;
    
    public String[] headerProperties;
    public int headerPropertiesPos;

    public int respBodyPos;
    public int respContentLengthPos;

    public String method;
    public String path;
    
    public String protocol;
        
    public String host;
    public String connection;
    public String webSocketKey;
    
    public int contentLength;
    public String contentType;
    
    public int encoding;
    
    public HTTPReqInfo() {
	contentLength = 0;
	encoding = ENCODING_NONE;
    }
    
    private static final int getEncoding(String encoding) {
	if (encoding.indexOf(ENCODING_GZIP_NAME) >= 0) {
	    return ENCODING_GZIP;
	} 

	return ENCODING_NONE;
    }

    private static final boolean isStringNull(String val) {
	return (val == null || val.length() == 0);
    }

    private static void setParam(String key, String val, HTTPReqInfo info) {
	
	if (isStringNull(key) || isStringNull(val)) return;

	if (info.params == null) {
	    info.params = new String[DEFAULT_PARAMS];
	    info.paramsPos = 0;
	} 
	
	String[] params = info.params;
	
	if (info.paramsPos == params.length) {
	    int length = params.length;
	    String[] newParams = new String[length << 1];
	    System.arraycopy(params, 0, newParams, 0, length);
	    info.params = newParams;
	    params = newParams;
	}
	
	params[info.paramsPos++] = key;
        params[info.paramsPos++] = val;
    }
    
    private static void setHeaderProperty(String key, String val, HTTPReqInfo info) {
	
	if (info.headerProperties == null) {
            info.headerProperties = new String[DEFAULT_PARAMS];
            info.headerPropertiesPos = 0;
        }

        String[] headers = info.headerProperties;

        if (info.headerPropertiesPos == headers.length) {
            int length = headers.length;
            String[] newHeaders = new String[length << 1];
            System.arraycopy(headers, 0, newHeaders, 0, length);
            info.headerProperties = newHeaders;
            headers = newHeaders;
        }

        headers[info.headerPropertiesPos++] = key;
        headers[info.headerPropertiesPos++] = val;
    }

    private static final int nextDiffVal(byte[] data, int pos, int endPos, byte val) {
	
	while (data[pos] == val) {
	    pos++;
	    if (pos == endPos) return -1;
	}
	
	return pos;
    }
    
    private static final int nextVal(byte[] data, int pos, int endPos, byte val) {
		
	int index = pos;
	
        while (true) {
	    if (index == endPos) return -1;
	    
	    if (data[index] == val) break;
	    
	    index++;
	}

        return index;
    }
    
    private static final int pathEnd(byte[] data, int pos, int endPos) {

        while (data[pos] != HTTPUtil.SPACE && data[pos] != HTTPUtil.PATH_END) {
            pos++;
            if (pos == endPos) return -1;
        }

        return pos;
    }

    private static final int extractReqParameters(byte[] data, int pos, int endPos, HTTPReqInfo info) {
	
	int startPos = pos;
	
	String key = null;
	String value = null;
	
	while (true) {
	    
	    if (pos == endPos) return pos;

	    byte val = data[pos++];
	    
	    if (val == HTTPUtil.EQUAL) {
		key = new String(data, startPos, pos - 1 - startPos);
		startPos = pos;
	    } else if (val == HTTPUtil.AMPERSAND) {
		value = new String(data, startPos, pos - 1 - startPos);
		setParam(key, value, info);
		key = null;
		value = null;
		startPos = pos;
	    } else if (val == HTTPUtil.SPACE) {
		value = new String(data, startPos, pos - 1 - startPos);
		setParam(key, value, info);
		break;
	    } 

	    /*int startPos = pos;
	    
	    int nextPos = nextVal(data, pos, endPos, HTTPUtil.EQUAL);
	    if (nextPos < 0) return pos;
	    
	    String key = new String(data, startPos, nextPos - startPos);
	    pos = nextPos + 1;
	    
	    startPos = pos;

	    byte val;

	    while (true) {
		if (pos == endPos) return pos;
		val = data[pos];
		if (val == HTTPUtil.AMPERSAND || val == HTTPUtil.SPACE) break;
		pos++;
	    }

	    String value = new String(data, startPos, pos - startPos);
	    
	    setParam(key, value, info);
	    	    
	    pos++;
	    
	    if (pos == endPos || val == HTTPUtil.SPACE) break;*/
	}

	return pos;
    }
    
    public static final HTTPReqInfo extractReqDescription(byte[] data, int offset, int length) throws Exception {
	
	int pos = offset;
	int endPos = offset + length;
		
	pos = nextDiffVal(data, pos, endPos, HTTPUtil.SPACE); 
	if (pos < 0) return null;
	
	int startPos = pos;
	pos = nextVal(data, pos, endPos, HTTPUtil.SPACE);
	if (pos < 0) return null;
	
	String method = new String(data, startPos, pos - startPos);
			
	pos = nextDiffVal(data, pos, endPos, HTTPUtil.SPACE);
        
	if (pos < 0) return null;
	
	startPos = pos;
        pos = pathEnd(data, pos, endPos);
        	
	if (pos < 0) return null;
        
       	String path = new String(data, startPos, pos - startPos);
	
	HTTPReqInfo info = new HTTPReqInfo();
	info.method = method;
	info.path = path;

	if (data[pos] == HTTPUtil.PATH_END) {
	    pos = extractReqParameters(data, pos + 1, endPos, info);
	    if (pos < 0) return info;
	}
	
	pos = nextDiffVal(data, pos, endPos, HTTPUtil.SPACE);
	if (pos < 0) return null;
	
	info.protocol = new String(data, pos, endPos - pos);
	return info;
    }

    public static final void setHeaderProperty(HTTPReqInfo info, String key, String val) {
	if (key.equalsIgnoreCase("Host")) {
	    info.host = val;
	} else if (key.equalsIgnoreCase(HTTPUtil.CONNECTION)) {
	    info.connection = val;
	} else if (key.equalsIgnoreCase(HTTPUtil.WEB_SOCKET_KEY)) {
	    info.webSocketKey = val;
	} else if (key.equalsIgnoreCase(HTTPUtil.CONTENT_LENGTH)) {
	    info.contentLength = Integer.parseInt(val.trim());
	} else if (key.equalsIgnoreCase(HTTPUtil.CONTENT_TYPE)) {
	    info.contentType = val;
	} else if (key.equalsIgnoreCase(HTTPUtil.ACCEPT_ENCODING)) {
	    info.encoding = HTTPReqInfo.getEncoding(val);
	} else {
	    setHeaderProperty(key, val, info);
	}
    }

    public static final String getParam(HTTPReqInfo info, String key) {

	String[] params = info.params;
	int length = info.paramsPos;

        for (int i = 0; i < length; i+=2) {
            
	    if (key.equalsIgnoreCase(params[i])) {
                return params[i + 1];
            }
	    
        }

        return null;

    }
    
    public static final String[] getParams(HTTPReqInfo info) {
	return info.params;
    }
    
    public static final void setSrcAddress(HTTPReqInfo info, String address) {
	info.srcAddress = address;
    }

    public static final String getSrcAddress(HTTPReqInfo info) {
	return info.srcAddress;
    }

    public static final int getParamsLength(HTTPReqInfo info) {
	return info.paramsPos;
    }

    public static String[] getHeadersProperties(HTTPReqInfo info) {
	return info.headerProperties;
    }

    public static final int getHeaderPropertiesLength(HTTPReqInfo info) {
        return info.headerPropertiesPos;
    }
    
    public static final String getContent(HTTPReqInfo info) {
	ByteStream stream = info.stream;
	return stream.getString();
    }        
    
    public final String getHost() {
	return host;
    }

    public final String getKey() {
	return path;
    }

    public final Session getSession() {
	return session;
    }

    public final SessionHandler getHandler() {
        return sHandler;
    }

    public final void setHandler(SessionHandler handler) {
        sHandler = handler;
    }
}