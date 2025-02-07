package justream.core;

public class AcceptorOption {
    
    public String host;
    public int port;
    public int numPollers;
    public int numWorkers;
    public int sessionsQueueSize;
    
    public int recvBufSize;

    public AcceptorOption() {
	host = null;
	port = 0;
	numPollers = 1;
	numWorkers = 4;
	sessionsQueueSize = 1 << 20;
	recvBufSize = 0;
    }
}