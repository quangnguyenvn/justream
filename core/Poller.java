package justream.core;

import java.io.IOException;

import java.util.Set;
import java.util.Iterator;

import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import jucommon.util.ByteStream;
import jucommon.util.Queue;

import justream.common.*;

import justream.session.ReqInfo;
import justream.session.TemporarySession;

class Poller implements Runnable {
    
    private String id;

    private Queue newSessionsQueue;
    private Queue hsCompletedSessionsQueue;

    private WorkersManager wManager;

    private Selector selector;
    
    private TemporarySession[] newSessions;
    private int numNewSessions;
    
    private TemporarySession[] hsCompletedSessions;
    private int numHSCompletedSessions;

    private ByteBuffer rBuffer;
    
    public Poller(String id, Queue newSessionsQueue, WorkersManager wManager) throws IOException {
	this.id = id;
	this.newSessionsQueue = newSessionsQueue;
	this.hsCompletedSessionsQueue = new Queue(1 << 20);

	this.wManager = wManager;
	
	selector = Selector.open();
		
	newSessions = new TemporarySession[256];
	numNewSessions = 0;
	
	hsCompletedSessions = new TemporarySession[256];
	numHSCompletedSessions = 0;

	rBuffer = ByteBuffer.allocateDirect(1 << 22);
	
	(new Thread(this, id)).start();
    }
        
    private void addNewSessions() {
	
	for (int i = 0; i < numNewSessions; i++) {
	    
	    TemporarySession session = newSessions[i];
	    
	    session.info("get by poller id {}", id);
	    
	    SocketChannel channel = session.getChannel();
	    
	    try {
		
		channel.configureBlocking(false);
		SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
		session.setSelectionKey(key);
		key.attach(session);
		session.debug("registered to poller {} selector", id);
		
		TLSContext tlsContext = session.getTLSContext();

		if (tlsContext == null) {
		    session.onHandshakeFinished();
		} 

	    } catch (IOException ie) {
		session.close(State.ERROR, "PAE", ie.getMessage());
	    }
	}
    }
    
    private void remove(TemporarySession session, int state, String agent, String msg) {
	SelectionKey key = session.getSelectionKey();
	key.cancel();
	session.close(state, agent, msg); 
    }
    
    private void prepareToTransfer(TemporarySession session, SelectionKey key) {
	
	if (session.isCancelAfterPolled()) {
	    
	    if (key == null) key = session.getSelectionKey();
    
	    session.debug("Cancel key {}", key);
	    key.cancel();
	    session.setOwner(State.WORKER);
	
	}
			
	if (session.isUpgrading()) {
	    session.getRStream().store();
	}
    
    }

    private int processSession(TemporarySession session) throws Exception {
	
	int numReqs = 0;
	
	while (true) {
	    
	    ReqInfo req = (ReqInfo) session.processNewData();
	    
	    if (req == null) break;
	    
	    numReqs++;

	    if (wManager.addJob(req) == 0) {
		remove(session, State.ERROR, "PAF", "Worker Queue Full");
		return -1;
	    }
	}
	
	if (numReqs == 0) return 0;
	
	if (numReqs > 1) {
	    session.warn("multiple reqs processed: {}", numReqs);
	}
	
	ByteStream stream = session.getRStream().getStream();
		
	if (stream.getLength() == 0) {
	    stream.reset();
	    return 1;
	} else {
	    return 0;
	}
    }

    private void processHSCompletedSessions() {
	
	numHSCompletedSessions = hsCompletedSessionsQueue.get(hsCompletedSessions);

	for (int i = 0; i < numHSCompletedSessions; i++) {
	    TemporarySession session = hsCompletedSessions[i];
	    
	    try {
		
		session.tlsHandshakeFinished();
		
		int code = processSession(session);
		
		if (code != 1) continue;
		
		prepareToTransfer(session, null);
		
	    } catch (Exception e) {
		e.printStackTrace();
		session.error("process hs completed session exception: {}", e.getMessage());
		remove(session, State.ERROR, "PPF", e.getMessage());
	    }
	}
    }
    
    private void processEvents() {
	
	Set<SelectionKey> selectedKeys = selector.selectedKeys();
	Iterator<SelectionKey> iter = selectedKeys.iterator();
	
	while (iter.hasNext()) {

	    SelectionKey key = iter.next();
	    
	    iter.remove();
	    
	    if (!key.isReadable()) continue;
	    	    	    
	    TemporarySession session = (TemporarySession) key.attachment();
	    
	    int code = processEvent(session);
	    
	    if (code != 1) continue;
	    
	    prepareToTransfer(session, key);
	    
	}
    }    
    
    private int processEvent(TemporarySession session) {
	
	if (session.isClosed()) return -1;
	
	String errorMsg = null;
	
	try {
	    
	    session.setHSQueue(hsCompletedSessionsQueue);
	    
	    int length = session.read(rBuffer);
	    
	    if (length < 0) {
		remove(session, State.SUCCESS, "PPF", "Poller read EOS");
		return -1;
	    }

	    if (length == 0) return 0;
	    
	    return processSession(session);
	    
	} catch (IOException ie) {
	    errorMsg = ie.getMessage();
	    session.warn("ieexception: {}", errorMsg);
	} catch (Exception e) {
	    errorMsg = e.getMessage();
	    session.error("exception: {}", errorMsg);
	    e.printStackTrace();
	}
	
	remove(session, State.ERROR, "PPF", errorMsg);
	return -1;
    }
    
    public final String getID() {
	return id;
    }
    
    public void run() {
	
	while (true) {
	    try {
		int numSelected = selector.select(1);
		
		processHSCompletedSessions();
		
		if (numSelected > 0) {
		    processEvents();
		}
		
		numNewSessions = newSessionsQueue.get(newSessions);
		addNewSessions();
	    } catch (Exception e) {
		e.printStackTrace();
		System.out.println("Poller thread exception: " + e.getMessage());
	    }
	} 
    }
    
    public final void wakeup() {
	selector.wakeup();
    }
}