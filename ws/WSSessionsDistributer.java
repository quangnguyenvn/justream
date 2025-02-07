package justream.ws;

import java.io.IOException;

import justream.common.Distributer;

public class WSSessionsDistributer implements Distributer {

    private WSPoller[] pollers;
    private final int numPollers;

    public WSSessionsDistributer() throws IOException {
	this.numPollers = 1;
	init(numPollers);
    }

    public WSSessionsDistributer(int numPollers) throws IOException {
	this.numPollers = numPollers;
	init(numPollers);
    }
    
    private void init(int numPollers) throws IOException {
		
	pollers = new WSPoller[numPollers];

	for (int i = 0; i < numPollers; i++) {
	    String id = "WSPoller#" + i;
	    pollers[i] = new WSPoller(id);
	}
    }

    public final int distribute(Object object) {

	WSSession session = (WSSession) object;

	int index = 0;
	 
	if (numPollers > 1) {
	    int numSessions = pollers[0].getNumSessions();
	    
	    for (int i = 1; i < numPollers; i++) {
		int newNumSessions = pollers[i].getNumSessions();

		if (newNumSessions < numSessions) {
		    numSessions = newNumSessions;
		    index = i;
		}
	    }
	}
	
	pollers[index].addSession(session);

	return 1;
    }
}