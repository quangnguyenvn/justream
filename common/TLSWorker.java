package justream.common;

import java.io.IOException;

import java.nio.ByteBuffer;

import jucommon.util.Queue;
import jucommon.util.NotifiableQueue;

public class TLSWorker implements Runnable {
    
    private static final int MAX_SESSIONS = 1 << 20;
    
    private static final int UNIT = 1 << 10;
    
    private String id;

    private NotifiableQueue newSessionsQueue;
    private Queue pendingSessionsQueue;
    
    private NonBlockingChannel[] pendingSessions;
        
    private NonBlockingChannel[] newSessions;
    
    public TLSWorker() {
	this.id = "TLSWorker";
	init();
    }

    public TLSWorker(String id) {
	this.id = "TLSWorker_" + id;
	init();
    }
    
    private void init() {
	
	this.newSessionsQueue = new NotifiableQueue(MAX_SESSIONS);
	this.pendingSessionsQueue = new Queue(MAX_SESSIONS);
	
	this.pendingSessions = new NonBlockingChannel[MAX_SESSIONS];
	this.newSessions = new NonBlockingChannel[UNIT];
	
	(new Thread(this, id)).start();
    
    }
    
    private boolean processSession(long currentTime, NonBlockingChannel channel) {
	
	if (channel.isExpired(currentTime)) {        
	    channel.close(State.ERROR, "TST", "TLS Session Timeout");
	    return true;
	}
	
	boolean code = true;
	
	try {
	    	    	    
	    code = channel.tlsHandshake();
	    
	    if (code) {
		channel.update(currentTime);
	    } else {
		code = (pendingSessionsQueue.add(channel) == 1);
	    }

	} catch (Exception e) {
	    e.printStackTrace();
	    System.out.println("TLSHandshakeException: " + e.getMessage());
	}
	
	if (!code) {
	    channel.close(State.ERROR, "TQF", "TLS Queue Full");
	}

	return code;
    }
    
    private void processPendingSessions() {
	
	int numPendingSessions = pendingSessionsQueue.get(pendingSessions);
	
	if (numPendingSessions == 0) return;

	long currentTime = Util.getCurrentTime();
	
	for (int i = 0; i < numPendingSessions; i++) {
	    NonBlockingChannel channel = pendingSessions[i];
	    processSession(currentTime, channel);
	}
	
    }
    
    private int processNewSessions() {
	
	int numNewSessions = newSessionsQueue.get(newSessions);
	
	if (numNewSessions == 0) return 0;
	
	long currentTime = Util.getCurrentTime();
	
	int numProcessedSessions = 0;
	
	for (int i = 0; i < numNewSessions; i++) {
	    
	    NonBlockingChannel channel = newSessions[i];
	    
	    boolean code = processSession(currentTime, channel);
	    
	    if (code) {
		numProcessedSessions++;
	    } else {
		break;
	    }
	}
	
	if (numProcessedSessions == numNewSessions) {
	    return numProcessedSessions;
	} else {
	    for (int i = numProcessedSessions; i < numNewSessions; i++) {
		NonBlockingChannel channel = newSessions[i];
		channel.close(State.ERROR, "TQF", "TLS Queue Full");
	    }
	    return -1;
	}
    }

    public void run() {
	
	while (true) {
	    
	    processPendingSessions();
	    
	    int numSessions = processNewSessions();
	    
	    int numPendingSessions = pendingSessionsQueue.available();
	    
	    long wTime;

	    if (numSessions > 0 || numPendingSessions > 0) {
		wTime = 1;
	    } else {
		wTime = 1000;
	    }
	    
	    newSessionsQueue.waitEvent(wTime);
	    
	}
    }
    
    public final NotifiableQueue getQueue() {
	return newSessionsQueue;
    }

}