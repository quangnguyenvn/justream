package justream.http;

import java.util.TimeZone;
import java.util.Locale;
import java.util.Calendar;
import java.text.SimpleDateFormat;

import jucommon.util.ByteStream;

public class HTTPUtil {
    
    public static final byte CR = 0x0D;
    public static final byte LF = 0x0A;
    public static final byte SUB = (byte) ':';
    public static final byte SPACE = (byte) ' ';
    public static final byte PATH_END = (byte) '?';
    public static final byte AMPERSAND = (byte) '&';
    public static final byte EQUAL = (byte) '=';

    public static final int RESP_CODE_OK = 200;
    public static final String RESP_TEXT_OK = "OK";
    
    public static final int RESP_CODE_SWITCHING_PROTOCOLS = 101;
    public static final String RESP_TEXT_SWITCHING_PROTOCOLS = "Switching Protocols";

    public static final int RESP_CODE_MOVED_PERMANENTLY = 301;
    public static final String RESP_TEXT_MOVED_PERMANENTLY = "Moved Permanently";

    public static final String NEW_LINE = "\r\n";

    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";
    public static final String METHOD_PUT = "PUT";

    public static final String SERVER = "Server";

    public static final String CONNECTION = "Connection";
    public static final String KEEP_ALIVE = "Keep-Alive";
    
    public static final String LOCATION = "Location";
    public static final String DATE = "Date";
    public static final String LAST_MODIFIED = "Last-Modified";
    
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_LENGTH = "Content-Length";
    
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    public static final String CONTENT_ENCODING = "Content-Encoding";

    public static final String WEB_SOCKET_KEY = "Sec-WebSocket-Key";
    public static final String WEB_SOCKET_ACCEPT = "Sec-WebSocket-Accept";
    
    public static final String UPGRADE = "Upgrade";

    public static final String EMPTY_CONTENT_LENGTH = "          ";
    
    private HTTPUtil() {
    }
    
    public static final boolean isMethodLegal(String method) {
	return (method.equals(METHOD_GET) ||
		method.equals(METHOD_POST) ||
		method.equals(METHOD_PUT));
    }

    public static final void addHeaderInfo(String key, String value, ByteStream stream) {
        stream.add(key);
        stream.add(": ");
        stream.add(value);
        stream.add(NEW_LINE);
    }

    public static final String getTime() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(calendar.getTime());
    }
}