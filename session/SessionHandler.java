package justream.session;

public abstract class SessionHandler {
    
    private String host;
    private String endpoint;     
    
    private boolean isPrefixMatching;
        
    public abstract void handleReq(Object req) throws Exception;

    public SessionHandler() {
	host = null;
	endpoint = null;
	isPrefixMatching = false; 
    }

    public final boolean isPrefixMatching() {
	return isPrefixMatching;
    }
    
    public final void setPrefixMatching() {
	isPrefixMatching = true;
    }
    
    public final String getHost() {
	return host;
    }
    
    public final void setHost(String host) {
	this.host = host;
    }

    public final String getEndpoint() {
	return endpoint;
    }

    public final void setEndpoint(String endpoint) {
	this.endpoint = endpoint;
    }
}