package justream.http;

import java.io.IOException;

import java.nio.channels.SocketChannel;

import jucommon.logging.ULogger;

import justream.session.Session;

import justream.core.Acceptor;

public class HTTPAcceptor extends Acceptor {
    
    private HTTPAcceptorOption option;
        
    public HTTPAcceptor(HTTPAcceptorOption option) throws IOException {
	super(option);
	init(option);
    }
    
    public HTTPAcceptor(HTTPAcceptorOption option, ULogger logger) throws IOException {
	super(option, logger);
	init(option);
    }

    private void init(HTTPAcceptorOption option) {
	this.option = option;
	String host = option.host;
        if (host == null) host = "all";
    }

    protected Session createSession(String id, SocketChannel channel) {
	HTTPSession session = new HTTPSession(id, channel, option.timeout);
	session.setProperties(option);
	session.setLogger(logger);
	return session;
    }
}