package justream.common;

public class State {
    
    private State() {
    }
    
    public static final int ACCEPTOR = 0;
    public static final int POLLER = 1;
    public static final int WORKER = 2;
    public static final int ASYN_WORKER = 3;

    public static final int ERROR = 0;
    public static final int SUCCESS = 1;
}
