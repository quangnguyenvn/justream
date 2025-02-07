package justream.core;

import justream.session.SessionHandler;

class HandlersInfo {
    
    private static int MAX_DOMAINS = 1 << 6;

    private HandlerInfo[] infos;
    private int numInfos;

    public HandlersInfo() {
	infos = new HandlerInfo[MAX_DOMAINS];
	numInfos = 0;
    }
    
    public final void add(HandlerInfo info) {
	infos[numInfos++] = info;
    }

    public final HandlerInfo get(String host) {
	int length = numInfos;
	for (int i = 0; i < length; i++) {
	    
	    HandlerInfo info = infos[i];
	    SessionHandler handler = info.handler;
	    
	    String handlerHost = handler.getHost(); 

	    if (host.indexOf(handlerHost) >= 0) {
		return info;
	    }
	}

	return null;
    }
}