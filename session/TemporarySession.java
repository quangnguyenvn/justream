package justream.session;

import java.io.IOException;

import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;

import jucommon.util.ByteStream;
import jucommon.util.Queue;

import justream.common.AsyncHandler;
import justream.common.State;
import justream.common.NonBlockingChannel;

public abstract class TemporarySession extends Session {

    private int workersGroupID;
    private int workerID;
    
    private SelectionKey selectionKey;
        
    protected AsyncHandler asyncHandler;

    public Object properties;
        
    public abstract boolean isCancelAfterPolled();
    public abstract boolean isCloseAfterHandled();
    public abstract boolean isUpgrading();

    public TemporarySession(String id, SocketChannel channel, long timeout) {
	super(id, channel, timeout);
	
	this.workersGroupID = -1;
	this.workerID = -1;

	this.asyncHandler = null;
    }
    
    public final void setAsyncHandler(AsyncHandler handler) {
	asyncHandler = handler;
    }
    
    public final int getWorkersGroupID() {
        return workersGroupID;
    }

    public final void setWorkersGroupID(int wID) {
        workersGroupID = wID;
    }

    public final int getWorkerID() {
	return workerID;
    }
    
    public final void setWorkerID(int wID) {
	workerID = wID;
    }
    
    public final SelectionKey getSelectionKey() {
        return selectionKey;
    }

    public final void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }
    
    public final void cleanup(int state, String agent, String msg) {
		
	if ((state != State.SUCCESS) ||
	    (getOwner() == State.WORKER && isCloseAfterHandled())) {
	    
	    close(state, agent, msg);
	}
	
    }

    public void tryComplete() throws IOException {
	if (write() == 1) {
	    cleanup(State.SUCCESS, "TS", "EOD");
	} else {
	    if (asyncHandler != null) {
		asyncHandler.addMonitoredChannel(this);
	    }
	}
	
    }
}