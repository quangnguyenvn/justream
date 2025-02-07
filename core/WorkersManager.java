package justream.core;

import java.io.IOException;

import java.util.Map;
import java.util.HashMap;

import jucommon.util.List;
import jucommon.util.ListItem;

import justream.session.ReqInfo;
import justream.session.SessionHandler;

public class WorkersManager {
    
    private static final int MAX_GROUPS = 1 << 6;

    private static final int MAX_KEYS = 1 << 10;
    
    private WorkersGroup[] wGroups;
    private int numGroups;

    private Object[] handlersInfo;
    private int numHandlersInfo;
    
    private List prefixHandlersList;
    private Map<String, Integer> keysMap;
    	
    public WorkersManager(int numWorkers) throws IOException {
	
	wGroups = new WorkersGroup[MAX_GROUPS];
	numGroups = 0;
	
	handlersInfo = new Object[MAX_KEYS];
	numHandlersInfo = 0;
	
	prefixHandlersList = new List();
	keysMap = new HashMap<String, Integer>();
	
	createWorkersGroup(null, numWorkers); 
    }
    
    private int createWorkersGroup(String key, int numWorkers) throws IOException {
	
	int index = numGroups;

	WorkersGroup wGroup = new WorkersGroup(key, index, numWorkers);
	
	wGroups[numGroups++] = wGroup;
	
	wGroup.start();
	
	return index;
    }
    
    private void addToList(HandlerInfo info) {
	prefixHandlersList.add(info);
    }

    private void addToMap(String key, HandlerInfo info) {
	handlersInfo[numHandlersInfo] = info;
        keysMap.put(key, numHandlersInfo);
        numHandlersInfo++;
    }

    private void updateMap(HandlerInfo info) {
	
	SessionHandler handler = info.handler;
	
	String host = handler.getHost();
	String key = handler.getEndpoint();

	if (host == null) {
	    addToMap(key, info);
	    return;
	}
	
	Object object = getHandlers(key);
	
	if (object == null) {
	    addToMap(key, info);
            return;
	}
	
	HandlersInfo hInfo;

	if (object instanceof HandlerInfo) {
	    hInfo = new HandlersInfo();
	    hInfo.add((HandlerInfo) object);
	} else {
	    hInfo = (HandlersInfo) object;
	}

	hInfo.add(info);
    }
    
    private Object getHandlers(String key) {
	Integer integer = keysMap.get(key);

        if (integer != null) {
            return handlersInfo[integer.intValue()];
        } else {
	    return null;
	}
    }

    private HandlerInfo getHandler(String key, String host) {
	
	Integer integer = keysMap.get(key);
	
        if (integer != null) {
	    Object object = handlersInfo[integer.intValue()];

	    if (object instanceof HandlerInfo) {
		return (HandlerInfo) object;
	    }

	    HandlersInfo hInfo = (HandlersInfo) object;

	    return hInfo.get(host);
	}

	int numItems = prefixHandlersList.getNumItems();
	if (numItems == 0) return null;

	ListItem item = prefixHandlersList.getHead();

	for (int i = 0; i < numItems; i++) {
	    
	    HandlerInfo info = (HandlerInfo) item.object;
	    SessionHandler handler = info.handler;
	    
	    String hKey = handler.getEndpoint();
	    String hHost = handler.getHost();

	    if (key.startsWith(hKey) && (hHost == null || host.startsWith(hHost))) {
		return info;
	    }
	    
	    item = item.next;
	}

	return null;
	
    }

    public final void addHandler(SessionHandler sHandler, int numWorkers) throws IOException {
        
	int index = 0;
	
	String key = sHandler.getEndpoint();
	
	if (numWorkers > 0) {
	    index = createWorkersGroup(key, numWorkers);
	} 

	HandlerInfo info = new HandlerInfo();
	info.handler = sHandler;
	info.wGroup = index;
	
	if (sHandler.isPrefixMatching()) {
	    addToList(info);
	} else {
	    updateMap(info);
	}
    }
    
    public final int addJob(ReqInfo reqInfo) {
	
	String host = reqInfo.getHost();
	String key = reqInfo.getKey();
	
	HandlerInfo hInfo = getHandler(key, host);
	
	if (hInfo == null) return 0;
		
	WorkersGroup wGroup = wGroups[hInfo.wGroup];
	reqInfo.setHandler(hInfo.handler);
	
	return wGroup.addJob(reqInfo);
    }
}