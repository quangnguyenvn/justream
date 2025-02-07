package justream.core;

import java.io.IOException;

import java.nio.ByteBuffer;

import jucommon.util.List;
import jucommon.util.ListItem;
import jucommon.util.NotifiableQueue;
import jucommon.util.ByteStream;

import justream.common.*;

import justream.session.ReqInfo;
import justream.session.Session;
import justream.session.TemporarySession;
import justream.session.SessionHandler;

class Worker extends AsyncWriter implements Runnable {
    
    private String key;
    private int id;
    
    private NotifiableQueue newReqsQueue;
        
    private ReqInfo[] newReqs;
    private int numNewReqs;
    
    private List list;
    
    public Worker(String key, int id) throws IOException {
	
	super(false, 1 << 20);

	this.key = key;
	this.id = id;
	
	this.newReqsQueue = new NotifiableQueue(1 << 20);
		
	newReqs = new ReqInfo[64];
	numNewReqs = 0;
	
	list = new List();
    }
    
    private String getThreadName(String key, int id) {
	if (key == null) {
	    key = "global";
	}
	
	return "w_" + key + "_" + id;
    }
    
    private void cleanCompletedChannels(List list) {
	
	int numItems = list.getNumItems();

	ListItem item = list.getHead();

	for (int i = 0; i < numItems; i++) {
	    
	    TemporarySession session =  (TemporarySession) item.object;
	    item = item.next;
	    
	    session.cleanup(State.SUCCESS, "WFS", null);
	}
    }
    
    private void processNewReqs() {
	
	for (int i = 0; i < numNewReqs; i++) {
	    ReqInfo req = newReqs[i];
	    
	    try {
		processNewReq(req);
	    } catch (Exception e) {
		e.printStackTrace();
		Session session = req.getSession();
		session.close(State.ERROR, "WPF", e.getMessage());
	    }
	}
	
    }

    private void processNewReq(ReqInfo req) throws Exception {
	
	TemporarySession session = (TemporarySession) req.getSession();

	session.info("start process by worker {}", id);
	
	session.setAsyncHandler(this);
		
	SessionHandler handler = req.getHandler();
	
	handler.handleReq(req);
	
	if (session.isClosed()) {
	    session.warn("closed at worker {}", id);
	} 

	session.info("end process by worker {}", id);
    }
        
    public final int getID() {
	return id;
    }
    
    public final void start() {
	(new Thread(this, getThreadName(key, id))).start();
    }
    
    public void run() {
	
	while (true) {
		
	    newReqsQueue.waitEvent(1);
	    	    
	    list.reset();
	    processMonitoredChannels(list);
	    
	    cleanCompletedChannels(list);

	    numNewReqs = newReqsQueue.get(newReqs);
	    processNewReqs();
	    
	} 
    }
    
    public final int addNewReqInfo(ReqInfo reqInfo) {
	return newReqsQueue.add(reqInfo);
    }
}