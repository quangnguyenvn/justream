package justream.common;

import java.nio.ByteBuffer;

import jucommon.util.ByteStream;

public class Util {
    
    public static final int UNIT_SIZE = 1 << 10;

    private Util() {
    }

    public static final void transfer(ByteBuffer buffer, ByteStream stream) {
	
	int length = buffer.remaining();
	
	if (length == 0) return;

        stream.assureEnoughSpace(length);

        int offset = stream.getOffset();
        int sLength = stream.getLength();
        buffer.get(stream.getBytes(), offset + sLength, length);

        stream.setLength(sLength + length);
    }

    public static final long getCurrentTime() {
	return System.nanoTime()/1000000L;
    }
}