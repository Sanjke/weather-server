package helper

import java.nio.ByteBuffer

class ByteHelper {

    static short bytesToShort(byte[] bytes) {
        byte[] reverted = new byte[2];
        reverted[0] = bytes[1];
        reverted[1] = bytes[0];
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
        buffer.put(reverted);
        buffer.flip();
        return buffer.getShort();
    }

}
