package justream.common;

class TransferResult {
    
    public static final int STATUS_EMPTY = 0;
    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_BUFFERED = 2;
    
    public int tLen;
    public int bufferStatus;
    
    public boolean needFlush;
    
    public TransferResult() {
	reset();
    }

    public void reset() {
	tLen = 0;
	bufferStatus = STATUS_EMPTY;
	needFlush = false;
    }

}