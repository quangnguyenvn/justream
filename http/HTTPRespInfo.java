package justream.http;

public class HTTPRespInfo {

    private int respCode;
    private String respText;
    
    public String contentType;
    public String contentEncoding;
    
    public String location;

    public final void setRespCode(int respCode) {
	this.respCode = respCode;

	switch(respCode) {
	case HTTPUtil.RESP_CODE_OK:
	    respText = HTTPUtil.RESP_TEXT_OK;
	    break;
	case HTTPUtil.RESP_CODE_MOVED_PERMANENTLY:
	    respText = HTTPUtil.RESP_TEXT_MOVED_PERMANENTLY;
	    break;
	case HTTPUtil.RESP_CODE_SWITCHING_PROTOCOLS:
	    respText = HTTPUtil.RESP_TEXT_SWITCHING_PROTOCOLS;
	    break;
	default:
	    break;
	}
    }

    public final int getRespCode() {
	return respCode;
    }

    public final String getRespText() {
	return respText;
    }

}