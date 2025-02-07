package justream.http;

import justream.core.AcceptorOption;

public class HTTPAcceptorOption extends AcceptorOption {
    
    public String serverName;
    public int maxKeepAlive;
    public int timeout;

    public HTTPAcceptorOption() {
	super();
	maxKeepAlive = 0;
	timeout = 0;
	//timeout = 5000;
    }
}