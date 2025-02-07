package justream.common;

import jucommon.util.NotifiableQueue;

class TLSWorkers {
    
    private TLSWorker[] workers;
    private int rrIndex;

    public TLSWorkers(int numWorkers) {
	
	workers = new TLSWorker[numWorkers];
	rrIndex = 0;
	
	for (int i = 0; i < numWorkers; i++) {
	    workers[i] = new TLSWorker(String.valueOf(i));
	}
    
    }
    
    public final NotifiableQueue getRRQueue() {
	TLSWorker worker = workers[rrIndex++];
	if (rrIndex == workers.length) {
	    rrIndex = 0;
	}
	return worker.getQueue();
    }
}