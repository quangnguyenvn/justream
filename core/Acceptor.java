package justream.core;

import java.io.IOException;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;

import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;

import jucommon.util.Queue;
import jucommon.logging.ULogger;

import justream.common.TLSEngine;

import justream.common.TLSContext;

import justream.common.State;

import justream.session.*;

public abstract class Acceptor implements Runnable {

    private String id;

    private String host;
    private int port;
    
    private int recvBufSize;
    
    private Queue queue;
    	
    private Poller[] pollers;
    private int numPollers;
    private int rrIndex;
    
    private WorkersManager wManager;

    private ServerSocketChannel sChannel;
    
    private TLSContext tlsContext;

    private long seq;
    
    protected ULogger logger;

    protected abstract Session createSession(String id, SocketChannel channel);
    
    public Acceptor(AcceptorOption option) throws IOException {
	init(option);
    }
    
    public Acceptor(AcceptorOption option, ULogger logger) throws IOException {
        init(option);
	this.logger = logger;
    }

    private void init(AcceptorOption option) throws IOException {
	this.host = option.host;
        this.port = option.port;
	this.id = host + "_" + port;
	this.recvBufSize = option.recvBufSize;

	queue = new Queue(option.sessionsQueueSize);
	
	wManager = new WorkersManager(option.numWorkers);
	
	numPollers = option.numPollers;
	pollers = new Poller[numPollers];
	rrIndex = 0;

	for (int i = 0; i < numPollers; i++) {
            String id = "Poller#" + i;
	    pollers[i] = new Poller(id, queue, wManager);
        }
	
	tlsContext = null;
	seq = System.currentTimeMillis()*1000;
    }
    
    private Session newSession() {
	
	try {
	    
	    SocketChannel channel = sChannel.accept();
	    
	    Session session = createSession(String.valueOf(seq++), channel);

	    session.setTLSContext(tlsContext);
	    
	    session.info("accepted channel {}", channel);

	    session.setOwner(State.POLLER);
	    
	    return session;
	} catch (Exception e) {
	    System.out.println("Exception in creating session: " + e.getMessage());
	    return null;
	}
    }

    public final void setLogger(ULogger logger) {
	this.logger = logger;
    }

    public final void setTLSContext(TLSContext context) {
	tlsContext = context;
    }

    public void start() {
	(new Thread(this, "Acceptor:" + port)).start();
    }
    
    public void run() {
	
	try {
	    sChannel = ServerSocketChannel.open();
	    
	    InetSocketAddress addr;
	    if (host == null) {
		addr = new InetSocketAddress(port);
	    } else {
		addr = new InetSocketAddress(host, port);
	    }

	    if (recvBufSize > 0) {
		sChannel.setOption(StandardSocketOptions.SO_RCVBUF, recvBufSize);
	    }
	    
	    sChannel.socket().setReuseAddress(true);
	    sChannel.bind(addr);
	    
	    while (true) {
		
		Session session = newSession();
		
		if (session == null) continue;
		
		if (queue.add(session) == 0) {
		    session.close(State.ERROR, "AAF", "Pollers queue full");
		    continue;
		} 
		
		session.debug("added to pollers queue");

		if (queue.available() < 128) {
		    Poller poller = pollers[rrIndex++];
		    if (rrIndex == numPollers) rrIndex = 0;
		    session.debug("wake up poller id {} to service", poller.getID());
		    poller.wakeup();
		}
	    }
	    
	} catch (IOException ie) {
	    ie.printStackTrace();
	    System.out.println("Acceptor " + id + " exited on IOException: " + ie.getMessage());
	}
    }
    
    public void addHandler(SessionHandler sHandler, int numWorkers) throws IOException {
	wManager.addHandler(sHandler, numWorkers);
    }
}