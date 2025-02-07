package justream.ws;

public class WSMessage {
    
    private static final int MAX_FRAMES = 1 << 6;
    
    private int maxMsgSize;

    public int[] framesPositions;
    public int numFrames;
    
    public boolean isFIN;

    public int opCode;

    public byte[] msg;
    public int offset;
    public int length;

    public WSMessage() {
	maxMsgSize = 0;
	framesPositions = new int[MAX_FRAMES << 1];
	reset();
    }
    
    private void shrink() {
	
	int pos = offset + framesPositions[1];
	    
	for (int i = 1; i < numFrames; i++) {
	    int cpyPos = 2*i;
	    int cpyLength = framesPositions[cpyPos + 1];
	    System.arraycopy(msg, framesPositions[cpyPos], msg, pos, cpyLength);
	    pos += cpyLength;
	}

	length = pos - offset;
    }
    
    public final int getMaxMsgSize() {
	return maxMsgSize;
    }

    public final void setMaxMsgSize(int size) {
	this.maxMsgSize = size;
    }

    public final void reset() {
        isFIN = false;
        numFrames = 0;
    }

    public final void addFrame(int start, int length) {
	int pos = numFrames << 1;
	
	framesPositions[pos] = start;
	framesPositions[pos + 1] = length;
	
	numFrames++;
    }
    
    public final void prepare(byte[] bytes) {
	msg = bytes;
	offset = framesPositions[0];
	
	if (numFrames == 1) {
	    length = framesPositions[1];
	} else {
	    shrink();
	}
    }
}