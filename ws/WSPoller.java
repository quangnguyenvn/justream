package justream.ws;

import java.io.IOException;

import java.util.Set;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import jucommon.util.ByteStream;
import jucommon.util.NotifiableQueue;

import justream.common.*;

public class WSPoller implements Runnable {
    
    private String id;

    private NotifiableQueue queue;
    
    private Selector selector;
    
    private int numActiveSessions;
    
    private WSSession[] newSessions;
    private int numNewSessions;
    
    private ByteBuffer rBuffer;
    
    private Map<String, Object> resourcesMap;

    public WSPoller(String id) throws IOException {
	this.id = id;
	this.queue = new NotifiableQueue(1 << 20);
	
	selector = Selector.open();
	
	numActiveSessions = 0;
	
	newSessions = new WSSession[512];
	numNewSessions = 0;
	
	rBuffer = ByteBuffer.allocateDirect(1 << 13);
	resourcesMap = new HashMap<String, Object>();
	
	(new Thread(this, id)).start();
    }

    private void addNewWaitingChannels() {
	numNewSessions = queue.get(newSessions);
	
	for (int i = 0; i < numNewSessions; i++) {
	    WSSession session = newSessions[i];
	    
	    try {
		add(session);
	    } catch (IOException ie) {
		ie.printStackTrace();
		session.close(State.ERROR, "WSAE", ie.getMessage());
	    }
	}
	
    }
    
    private void add(WSSession session) throws IOException {
	SocketChannel channel = session.getChannel();
	
	session.debug("registered to ws poller");           
	
	session.getRStream().load();
	session.getWStream().load();
	
	SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
	key.attach(session);
	session.onConnect();
	
	numActiveSessions++;

	session.info("num active sessions: {}", numActiveSessions);
    }

    private void remove(WSSession session, int state, String agent, String reason) {
	session.close(state, agent, reason);
	numActiveSessions--;
	
	session.info("num active sessions: {}", numActiveSessions);
    }

    private void processEvents() throws IOException {
	
	int numSelected = selector.selectNow();

	if (numSelected == 0) return;
	
	Set<SelectionKey> selectedKeys = selector.selectedKeys();
	Iterator<SelectionKey> iter = selectedKeys.iterator();
	
	while (iter.hasNext()) {
	    
	    SelectionKey key = iter.next();
	    
	    iter.remove();

	    if (key.isReadable()) {
		
		WSSession session =  (WSSession) key.attachment();
		
		if (!session.isClosed()) {
		    processEvent(session);
		}

	    }
	    
	}
	
    }
    
    private void processEvent(WSSession session) {
	
	try {
	    
	    int rLength = session.read(rBuffer);
	    
	    if (rLength < 0) {
		remove(session, State.ERROR, "WSPRF", "Read failed");
		return;
	    }
	    session.processNewData();
	} catch (IOException ie) {
	    remove(session, State.ERROR, "WSPRFIE", ie.getMessage());
	} catch (Exception e) {
	    e.printStackTrace();
	    remove(session, State.ERROR, "WSPRFE", e.getMessage());
	}
    }
    
    public void run() {
	
	while (true) {
	    
	    try {
		queue.waitEvent(1);
		addNewWaitingChannels();
		processEvents();
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }

    public final int addSession(WSSession session) {
	return queue.add(session);
    }

    public final int getNumSessions() {
	
	int numWaitingSessions = queue.getQueue().available();
	
	return numWaitingSessions + numActiveSessions;

    }
}