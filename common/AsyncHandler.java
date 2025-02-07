package justream.common;

import jucommon.util.List;

public interface AsyncHandler {
    
    int addMonitoredChannel(NonBlockingChannel channel);

    int processMonitoredChannels(List list);

}