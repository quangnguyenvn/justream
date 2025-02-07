package justream.common;

import java.io.IOException;

import jucommon.util.ByteStream;
import jucommon.util.List;
import jucommon.util.NotifiableQueue;

public class AsyncWriter implements AsyncHandler {

    private static final int DEFAULT_QUEUE_SIZE = 1 << 20;

    private int idleWaitingTime;
    private int busyWaitingTime;

    private NotifiableQueue queue;
    
    private NonBlockingChannel[] channels;
    
    public AsyncWriter(boolean asyncThread) {
	init(asyncThread, DEFAULT_QUEUE_SIZE);
    }

    public AsyncWriter(boolean asyncThread, int size) {
	init(asyncThread, size);
    }
	
    private void init(boolean asyncThread, int size) {
	
	idleWaitingTime = 1000;
	busyWaitingTime = 1000;

	queue = new NotifiableQueue(size);
	
	channels = new NonBlockingChannel[size];
	
	if (!asyncThread) return;

	new Thread("AsyncWriter"){

            public void run() {
		
                while (true) {
		    
                    int numChannels = processMonitoredChannels(null);

                    int wTime = (numChannels == 0)?idleWaitingTime:busyWaitingTime;
		    
                    queue.waitEvent(wTime);
		    
                }
            }
        }.start();
    }
    
    private int write(NonBlockingChannel channel) throws IOException {
	
	ByteStream stream = channel.getWStream().getStream();

	synchronized(stream) {
	    return channel.write();
	}
    
    }

    private void processChannel(NonBlockingChannel channel, List list) {
	
	if (channel.isClosed()) return;
	
	try {
	    int code = write(channel);
	    
	    if (code == 1) {
		if (list != null) {
		    list.add(channel);
		}
	    } else {
		queue.add(channel);
	    } 

	} catch (IOException ie) {
	}
    }
    
    public final void setIdleWaitingTime(int time) {
	idleWaitingTime = time;
    }

    public final void setBusyWaitingTime(int time) {
	busyWaitingTime = time;
    }
    
    public final int addMonitoredChannel(NonBlockingChannel channel) {
	return queue.add(channel);
    }
    
    public final int processMonitoredChannels(List list) {
	
	int numChannels = queue.get(channels);
	
	for (int i = 0; i < numChannels; i++) {
	    processChannel(channels[i], list);
	}

	return numChannels;
    }
}