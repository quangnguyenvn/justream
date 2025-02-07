package justream.session;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import jucommon.util.Native;
import jucommon.util.ByteStream;
import jucommon.logging.ULogger;
import jucommon.logging.FileLogger;

import justream.common.Util;
import justream.common.State;
import justream.common.NonBlockingChannel;

public abstract class Session extends NonBlockingChannel {
    
    private final String id;
    
    private long timeStart;
    private long timeout;
    private long timeLastUpdated;
    
    private int owner;
    
    protected ULogger logger;
    
    public abstract Object processNewData() throws Exception;

    public Session(String id, SocketChannel channel, long timeout) {
	super(channel);
	this.id = id;
	this.timeStart = Util.getCurrentTime();
	this.timeLastUpdated = timeStart;
	this.timeout = timeout;

	this.logger = null;
	this.owner = State.ACCEPTOR;
    }
    
    public final String getID() {
        return id;
    }
    
    public final ULogger getLogger() {
	return logger;
    }

    public final void setLogger(ULogger logger) {
	this.logger = logger;
    }
    
    public final int getOwner() {
	return owner;
    }

    public final void setOwner(int owner) {
	this.owner = owner;
    }
    
    public final boolean isExpired(long currentTime) {
	
	if (timeout == 0) return false;
	
	return (currentTime - timeLastUpdated) >= timeout;
    }
    
    public final boolean isExpired() {
	return isExpired(Util.getCurrentTime());
    }
    
    public final void update(long time) {
	timeLastUpdated = time;
    }

    public final void close(int code, String agent, String msg) {
        	
	super.close();

	if (code == State.ERROR) {
	    error("closed by agent {}. Msg {}", agent, msg); 
	} else {
	    info("EOS");
	}
    }
    
    public final void debugStream(String format, ByteStream stream, int offset) {
	
	if (logger == null) return;
	int mode = logger.getMode();

	if (mode != FileLogger.DEBUG) return;
	
	byte[] data = stream.getBytes();
	int length = stream.getLength() + stream.getOffset() - offset;
	
	logger.debug(format, new String(stream.getBytes(), offset, length));

    }

    public final void debug(Object ... args) {
	if (logger == null) return;
	args[0] = "Session " + id + " " + args[0];
	
	logger.debug(args);
    }

    public final void info(Object ... args) {
        if (logger == null) return;
        args[0] = "Session " + id + " " + args[0];            

        logger.info(args);
    }

    public final void warn(Object ... args) {
        if (logger == null) return;
        args[0] = "Session " + id + " " + args[0];            

        logger.warn(args);
    }

    public final void error(Object ... args) {
        if (logger == null) return;
        args[0] = "Session " + id + " " + args[0];            

        logger.error(args);
    }
}