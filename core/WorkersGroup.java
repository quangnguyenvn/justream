package justream.core;

import java.io.IOException;

import justream.session.TemporarySession;
import justream.session.ReqInfo;

class WorkersGroup {
   
    private int id;

    private Worker[] workers;
    
    private int numWorkers;
    private int rrIndex;

    public WorkersGroup(String key, int id, int numWorkers) throws IOException {
	
	this.id = id;
	
	workers = new Worker[numWorkers];
	this.numWorkers = numWorkers;
	
	for (int i = 0; i < numWorkers; i++) {
	    workers[i] = new Worker(key, i);
	}
	
	rrIndex = 0;

    }
    
    private int add(Worker worker, ReqInfo reqInfo) {
	return worker.addNewReqInfo(reqInfo);
    }
    
    private int add(TemporarySession session, ReqInfo reqInfo) {
		
        for (int i = 0; i < numWorkers; i++) {
	    
            Worker worker = workers[rrIndex++];
	    if (rrIndex == numWorkers) rrIndex = 0;
	    
	    session.setWorkerID(worker.getID());
	    int code = add(worker, reqInfo);
            
	    if (code == 1) return 1; 
	}
	
	session.setWorkerID(-1);
	
	return 0;

    }
    
    public final void start() {
	
	int length = workers.length;

	for (int i = 0; i < length; i++) {
	    workers[i].start();
	}
    }

    public final int addJob(ReqInfo reqInfo) {
	
	TemporarySession session = (TemporarySession) reqInfo.getSession();

	int workersGroupID = session.getWorkersGroupID();
	int workerID = session.getWorkerID();
	
	if (workersGroupID != id) {
	    workerID = -1;
	    session.setWorkersGroupID(id);
	}
	
	int code;

	if (workerID >= 0) {
	    code = add(workers[workerID], reqInfo);
	} else {
	    code = add(session, reqInfo);
	} 
	
	return code;
    }
}
