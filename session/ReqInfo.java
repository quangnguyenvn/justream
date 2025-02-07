package justream.session;

public interface ReqInfo {
    
    String getHost();

    String getKey();
    
    Session getSession();
    
    void setHandler(SessionHandler handler);
    
    SessionHandler getHandler();

}

