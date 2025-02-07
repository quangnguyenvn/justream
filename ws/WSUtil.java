package justream.ws;

import java.io.IOException;

import justream.session.Session;

public class WSUtil {
    
    public static final int OP_CODE_TEXT = 1;
    public static final int OP_CODE_BINARY = 2;
    public static final int OP_CODE_CLOSE = 8;
    public static final int OP_CODE_PING = 9;
    public static final int OP_CODE_PONG = 10;

    private WSUtil() {
    }
    
    private static int calculateExtraPayloadBytes(int length) {
        if (length <= 125) return 0;
        if (length <= 65536) return 2;
        return 8;
    }
    
    private static void putInt(int val, byte[] buf, int offset, int length) {
	
	long lVal = (long) val;
	    
	int shift = 8*(length - 1);
	
	for (int i = 0; i < length; i++) {
	    buf[offset + i] = (byte) (lVal >> shift);
	    shift -= 8;
	}
    }
    
    private static void prepareHeader(byte[] msg, int offset, int opCode, int payloadBytes, int payloadLength) {
	
	msg[offset] = (byte) ((opCode | 0x00000080));      
	
	if (opCode == OP_CODE_PONG) return;
	
	if (opCode == OP_CODE_PING && payloadLength == 0) return;

        if (payloadBytes == 0) {
            msg[offset + 1] = (byte) (payloadLength);      
        } else if (payloadBytes == 2) {
            msg[offset + 1] = 126;      
            putInt(payloadLength, msg, offset + 2, 2);
        } else if (payloadBytes == 8) {
            msg[offset + 1] = 127;      
            putInt(payloadLength, msg, offset + 2, 8);
        }

    }
    
    private static byte[] prepareWSMsg(byte[] data, int offset, int len, int opCode) {
	int payloadBytes = calculateExtraPayloadBytes(len);
	
	byte[] msg = new byte[2 + payloadBytes + len];
    
	prepareHeader(msg, 0, opCode, payloadBytes, len);
	System.arraycopy(data, offset, msg, 2 + payloadBytes, len);
	
	return msg;
    }

    public static final boolean getFIN(byte val) {
	return (val < 0);
    }

    public static final int getOPCode(byte val) {
	return val & 0x0F;
    }
    
    public static final boolean getMask(byte val) {
	return (val < 0);
    }

    public static final int getPayloadLength(byte val) {
	return (val & 0x7F);
    }

    public static final int getLengthFromBytes(byte[] val, int offset, int length) {
	long result = 0;

	for (int i = 0; i < length - 1; i++) {
	    result |= (val[offset] & 0xFF);
	    result <<= 8;
	}

	result |= (val[offset + length - 1] & 0xFF);
	
	return (int) result;
    }

    public static final void mask(byte[] data, int offset, int length, byte[] mask, int maskOffset) {
	for (int i = 0; i < length; i++) {
	    data[offset + i] ^= mask[maskOffset + (i%4)];
	}
    }
    
    public static final int prepareWSBinaryMsg(byte[] data, int offset, int len, byte[] wsMsg, int wsMsgOffset) {
	
	int payloadBytes = calculateExtraPayloadBytes(len);
	int length = 2 + payloadBytes + len;
	
	prepareHeader(wsMsg, wsMsgOffset, OP_CODE_BINARY, payloadBytes, len);
	System.arraycopy(data, offset, wsMsg, wsMsgOffset, len);
	
	return length;
    }
    
    public static final byte[] prepareWSBinaryMsg(byte[] data, int offset, int len) {
	return prepareWSMsg(data, offset, len, OP_CODE_BINARY);
    }
    
    public static final byte[] prepareWSTextMsg(byte[] data, int offset, int len) {
	return prepareWSMsg(data, offset, len, OP_CODE_TEXT);
    }
    
    public static final byte[] prepareWSTextMsg(String text) {
	
	int len = text.length();

        int payloadBytes = calculateExtraPayloadBytes(len);

        byte[] msg = new byte[2 + payloadBytes + len];
	
        prepareHeader(msg, 0, OP_CODE_TEXT, payloadBytes, len);
	
	int offset = 2 + payloadBytes;
	
	for (int i = 0; i < len; i++) {
	    msg[i + offset] = (byte) text.charAt(i);
	}

        return msg;
    }
    
    public static final byte[] prepareWSPingMsg() {
	byte[] msg = new byte[1];
        prepareHeader(msg, 0, OP_CODE_PING, 0, 0);
        return msg;
    }

    public static final byte[] prepareWSPongMsg() {    
	byte[] msg = new byte[1];
	prepareHeader(msg, 0, OP_CODE_PONG, 0, 0);
	return msg;
    }

    public static final int processMsg(byte[] bytes, int offset, int length, WSMessage wsMsg) throws Exception {
	
        byte first = bytes[offset];

	boolean isFIN = WSUtil.getFIN(first);
	
	int opCode = WSUtil.getOPCode(first); 
	
	if (opCode == OP_CODE_PING || opCode == OP_CODE_PONG) {
	    wsMsg.isFIN = isFIN;
            wsMsg.opCode = opCode;
	    return 1;
	}
	
	if (length < 2) return 0;

        byte second = bytes[offset + 1];
	
	boolean isMask = WSUtil.getMask(second);

        int payloadBytes = 0;
        int payloadLength = WSUtil.getPayloadLength(second);

        int pos = 2;

        if (payloadLength == 126) {
            payloadBytes = 2;
        } else if (payloadLength == 127) {
            payloadBytes = 8;
        }

        if (length < pos + payloadBytes) return 0;
	
	if (payloadBytes > 0) {
            payloadLength = WSUtil.getLengthFromBytes(bytes, offset + pos, payloadBytes);
            pos += payloadBytes;
        }
	
	int maxMsgSize = wsMsg.getMaxMsgSize();
	
        if (payloadLength < 0 || (maxMsgSize != 0 && payloadLength > maxMsgSize)) {
	    throw new Exception("Invalid msg length: " + payloadLength);
	}
	
        if (isMask) {
            pos += 4;
            if (length < pos + payloadLength) return 0;
	    WSUtil.mask(bytes, offset + pos , payloadLength, bytes, offset + pos - 4);
        } else {
            if (length < pos + payloadLength) return 0;
        }

        wsMsg.addFrame(offset + pos, payloadLength);

        int msgLength = pos + payloadLength;

        if (isFIN) {
            wsMsg.isFIN = isFIN;
	    wsMsg.opCode = opCode;
            wsMsg.prepare(bytes);
	}

        return msgLength;

    }
}