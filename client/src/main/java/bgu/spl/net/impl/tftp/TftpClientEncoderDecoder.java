package bgu.spl.net.impl.tftp;
import java.util.Arrays;

public class TftpClientEncoderDecoder {
    
    private byte[] bytes = new byte[1<<10]; //The byte array to save the message
    int len = 0; //The last byte

    public byte[] decodeNextByte(byte nextByte) {
        if ((len > 2 && nextByte == 0x0000)) { //If we reached the 0 at the end
            if (bytes[1] != 0x0004 && bytes[1] != 0x0003) { //Error or bcast
                return removePadding();
            }
        }
        pushByte(nextByte);
        if (len == 4 && bytes[1] == 0x0004) { //If ACK
            return removePadding();
        } 
        else if (len > 6 && bytes[1] == 0x0003) { //If DATA
            short dataLength = (short)(((short) bytes[2]) << 8 | (short)(bytes[3]) & 0x00ff);
            if (len == dataLength + 6||dataLength==0) {
                return removePadding();
            }
        }
        return null;
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }
        bytes[len++] = nextByte;
    }

    public byte[] encode(byte[] message) {
        return message;
    }
    public byte[] removePadding() {
        byte[] packet;
        packet = Arrays.copyOf(bytes, len);
        len = 0;
        bytes = new byte[1 << 10];
        return packet;
    }
    
}
