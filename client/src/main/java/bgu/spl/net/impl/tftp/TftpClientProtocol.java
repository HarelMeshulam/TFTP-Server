package bgu.spl.net.impl.tftp;



import java.util.List;
import java.util.Arrays;
import java.util.LinkedList;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class TftpClientProtocol {

    protected int currentAwaitingBlock = 0;
    protected boolean readOrDir;
    byte[] fileToSend;
    byte[] fileToReceive;
    protected Object keyboardLock = new Object();
    String fileNameToSave;
    boolean sending=false;
    protected boolean disc=false;
    protected volatile boolean shouldTerminate = false;
    

    public byte[] process(byte[] message){

        if(message[1] == 0x0003) { //DATA
            short packetSize = (short)(((short) message[2]) << 8 | (short)(message[3] & 0x00ff));
            System.out.println("PacketSize is " + packetSize + " message length is " + message.length);
            int oldLength = fileToReceive.length;
            fileToReceive = Arrays.copyOf(fileToReceive, oldLength + packetSize);
            for (int i = 0; i < packetSize; i++) {
                fileToReceive[oldLength + i] = message[i + 6];
            }
            //Send ack packet with blockNumber
            short blockNumber = (short)(((short) message[4]) << 8 | (short)(message[5] & 0x00ff));
            byte[] ack = buildACK((short)blockNumber);
            System.out.println("Sending ACK " + blockNumber);
            //Write file
            if (packetSize < 512) {
                if (readOrDir) { //Read request (when client sends a WRQ, keyboard thread sets this boolean to true)
                                 //Keyboard thread creates an empty file on and sets it as the currentFile when receiving a WRQ.
                    try {
                        FileOutputStream os = new FileOutputStream(fileNameToSave);
                        try {
                            os.write(fileToReceive);
                            System.out.println("Wrote it");
                        } catch (IOException e) {}
                        try {
                            os.close();
                        } catch (IOException e) {}

                    } catch (FileNotFoundException e) {}
                }
                else { //DIRQ
                    //Construct the string
                    //So long as we don't remove bytes from the bytebuffer, it is guaranteed to contain our data.
                    for (int i = 0; i < fileToReceive.length; i++) {
                        if (fileToReceive[i] == (byte) 0) {
                            fileToReceive[i] = 10; //Converts 0 to \n
                        }
                    }
                    String directoryList = new String(fileToReceive);
                    System.out.println(directoryList);
                }
                fileToReceive = new byte[0];
                synchronized (keyboardLock) {
                    keyboardLock.notifyAll();
                } //All data packets received and handled, user can input new commands. 
            }
            return ack;
        }
        else if (message[1] == 0x0004) { //ACK
            short blockNumber = (short)(((short) message[2]) << 8 | (short)(message[3] & 0x00ff));
            System.out.println("ACK packet with block number " + blockNumber + " received while awaiting block no. " + currentAwaitingBlock);
            if (blockNumber == currentAwaitingBlock) {
                if (sending && !disc) {
                    if(isThereMoreToSend(fileToSend, (short)(currentAwaitingBlock + 1))) {
                        currentAwaitingBlock++;
                        return startSending(fileToSend,(short)currentAwaitingBlock);
                    }
                    else {
                        sending = false;
                        currentAwaitingBlock = 0;
                        synchronized (keyboardLock) {
                            keyboardLock.notifyAll();
                        }
                    }
                }
                else if(!disc) {
                    currentAwaitingBlock = 0;
                    synchronized (keyboardLock) {
                        keyboardLock.notifyAll();
                    }
                }
                else{
                    shouldTerminate=true;
                }
            }
            
            //Maybe else to nofity the keyboard and saying we got the wrong packet
        }
        else if(message[1] == 0x0005) { //ERROR
            short errorType = (short)(((short) message[2]) << 8 | (short)(message[3] & 0x00ff));
            String errorMsg = "Error " + errorType + " ";
            byte[] errorData = new byte[message.length - 4];
            for (int i = 0; i < errorData.length; i++) {
                errorData[i] = message[i + 4];
            }
            errorMsg += new String(errorData);
            if (errorType == 1) { //Downloading file from the server failed, hence need to delete the empty file.
                fileToReceive = new byte[0];
            }
            else if(errorType == 5) { //Uploading an existing file to the serve
                fileToSend = new byte[0];
            }
            synchronized(keyboardLock) {
                keyboardLock.notifyAll();
            }
            if(disc) {
                shouldTerminate = true;
            }
            return errorMsg.getBytes();
        }
        else if(message[1] == 0x0009) { //BCAST
            byte[] filename = new byte[message.length-3];
            for (int i = 0; i < filename.length; i++) {
                filename[i] = message[i + 3];
            }
            String prefix = "BCAST ";
            if (message[2] == 0) {
                prefix += "deleted ";
            }                    
            else {
                prefix += "added ";
            }
            prefix += new String(filename);
            return prefix.getBytes();
        }
        return null;
    }
    private boolean isThereMoreToSend(byte[] byteArray, short packetNum) {
        int remain = byteArray.length - ((packetNum - 1) * 512);
        return remain >= 0;
    }

    private byte[] startSending(byte[] byteArray, short packetNum) {
        byte[] packetNumberToSendBytes = new byte[] {(byte)((packetNum >> 8) & 0xFF), (byte)(packetNum & 0xFF)}; //Packet number to send
        byte[] response = new byte[6];
        packetNum = (short)(packetNum - (short)(1));
        int remain = byteArray.length - (packetNum * 512);
        
        if (remain > 512) {
            //Convert 512 into bytes
            short maxPacetSize = 512;
            int startIndex = 512 * (packetNum);
            response = new byte[518];
            System.arraycopy(byteArray, startIndex, response, 6, 512);
            byte[] maxPacetSizeBytes = new byte[]{(byte)(maxPacetSize >> 8), (byte)(maxPacetSize & 0xff) }; //Array of bytes with the op code
            
            //Create the start of the packet
            response[0] = (byte)(0);
            response[1] = (byte)(3);
            response[2] = maxPacetSizeBytes[0];
            response[3] = maxPacetSizeBytes[1];
            response[4] = packetNumberToSendBytes[0];
            response[5] = packetNumberToSendBytes[1];

            System.out.println("Sent DATA packet number " + (packetNum + 1) + " and length " + maxPacetSize);
        } 
        else if (remain <= 512 & remain > 0) {
            response = new byte[remain + 6];
            int startIndex = 512 * (packetNum);

            //Convert whats left into bytes
            short packetSize = (short)(byteArray.length - (packetNum * 512));
            byte[] pacetSizeBytes = new byte[] {(byte)(packetSize >> 8), (byte)(packetSize & 0xff) };
            System.arraycopy(byteArray, startIndex, response, 6, remain);
            
            //Create the start of the packet
            response[0] = (byte)(0);
            response[1] = (byte)(3);
            response[2] = pacetSizeBytes[0];
            response[3] = pacetSizeBytes[1];
            response[4] = packetNumberToSendBytes[0];
            response[5] = packetNumberToSendBytes[1];
            System.out.println("Sent DATA packet number " + (packetNum + 1) + " and length " + packetSize);
        } 
        else if (remain == 0) {
            response = new byte[7];
            response[0] = (byte)(0);
            response[1] = (byte)(3);
            response[2] = (byte)(0);
            response[3] = (byte)(1);
            response[4] = packetNumberToSendBytes[0];
            response[5] = packetNumberToSendBytes[1];
            response[6] = (byte)(0);
            System.out.println("Sent DATA packet number " + (packetNum + 1) + " and length 0");
        }
        return response;
    }
    private byte[] buildACK(short blockNumber) { // Builds an ACK packet
        short op = 4;
        byte[] opBytes = new byte[] { (byte)(op >> 8), (byte)(op & 0xff) }; // Array of bytes with the op code
        byte[] blockNumberBytes = new byte[] {(byte)(blockNumber >> 8), (byte)(blockNumber & 0xff)}; // Array of byes with the block number
        byte[] ack = new byte[4]; // Array of bytes with the full packet

        //Marge op and block number into the ACK packet
        ack[0] = opBytes[0];
        ack[1] = opBytes[1];
        ack[2] = blockNumberBytes[0];
        ack[3] = blockNumberBytes[1];
        return ack;
    }
}
