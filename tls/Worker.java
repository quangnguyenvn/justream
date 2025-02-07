package justream.tls;

import java.io.IOException;

import java.nio.ByteBuffer;

import jucommon.util.Queue;

import justream.common.NonBlockingChannel;

class Worker implements Runnable {
    
    private static final int MAX_SESSIONS = 1 << 20;
    private static final int MAX_BUFFER_SIZE = 1 << 20;

    private Queue newSessionsQueue;
    private Queue pendingSessionsQueue;
    
    private TLSEngine[] pendingSessions;
    private int numPendingSessions;
    
    private ByteBuffer workingBuffer;

    public Worker() {
	this.newSessionsQueue = new Queue(MAX_SESSIONS);
	this.pendingSessionsQueue = new Queue(MAX_SESSIONS);
	this.pendingSessions = new TLSEngine[MAX_SESSIONS];
	
	this.workingBuffer = ByteBuffer.allocate(MAX_BUFFER_SIZE);

	(new Thread(this)).start();
    }
    
    private void waiting() {
        synchronized(this) {
            try {
                wait(1);
            } catch (InterruptedException ie) {
            }
        }
    }

    private boolean processSession(TLSEngine engine, int numRetries) {
	boolean code = true;
	
	try {
	    code = engine.handshake();
	    
	    if (!code) {
		code = (pendingSessionsQueue.add(engine, numRetries) == 1);
	    }
	} catch (IOException ie) {
	}

	return code;
    }
    
    private void processPendingSessions() {
	numPendingSessions = pendingSessionsQueue.get(pendingSessions);

	for (int i = 0; i < numPendingSessions; i++) {
	    TLSEngine engine = pendingSessions[i];

	    processSession(engine, 0);
	}
    }
    
    private void processNewSessions() {
	
	while(true) {
	    
            TLSEngine engine = (TLSEngine) newSessionsQueue.get();
	    
	    if (engine == null) break;
	    
	    System.out.println("Process new session");
	    boolean code = processSession(engine, 1);
	    
	    if (!code) {
		newSessionsQueue.add(engine, 0);
		break;
	    }

	}
    }

    public void run() {
	while (true) {
	    waiting();
	    processPendingSessions();
	    processNewSessions();
	}
    }
    
    public final Queue getQueue() {
	return newSessionsQueue;
    }

    public final ByteBuffer getWorkingBuffer() {
	return workingBuffer;
    }
}