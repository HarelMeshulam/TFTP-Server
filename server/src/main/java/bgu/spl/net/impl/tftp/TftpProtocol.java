package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;

import bgu.spl.net.impl.tftp.*;

/*
class holder {
    static public ConcurrentHashMap<Integer, Boolean> idsLogin = new ConcurrentHashMap<>();
    static public ConcurrentHashMap<Integer, String> idsUserName = new ConcurrentHashMap<>();
}
 */

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    private boolean shouldTerminate = false;
    private int connectionId;
    private ConnectionsImp<byte[]> connections;
    byte[] fileToSend;
    byte[] fileToSave;
    String fileNameToSave;
    public ConnectionHandler<byte[]> handler;
    private boolean shouldSendError = false;
    private short minErrorCode = (short)(8); //The largest value of error code

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.shouldTerminate = false;
        this.connectionId = connectionId;
        this.connections = (ConnectionsImp<byte[]>) connections;
        connections.connect(connectionId, this.connections.idToHandler.get(connectionId));
    }

    @Override
    public void process(byte[] message) {
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
        if (message[1] == 0x0001) { //Read Request
            String filename = new String(message, 2, packetLength(message, 2), StandardCharsets.UTF_8); // Get the file name from the packet
            File file = new File("Files/", filename); //The file
            
            //Check the file doesn't exists
            if (!file.exists()) {
                System.out.println("File not found: " + filename);
                shouldSendError = true;
                short currentError = (short)(1);
                if (currentError < minErrorCode) {
                    minErrorCode = currentError;
                }
            } 

            //If the file exists and the user is not logged in
            else if (!isUserLoggedIn()) {
                shouldSendError = true;
                short currentError = (short)(6);
                if (currentError < minErrorCode) {
                    minErrorCode = currentError;
                }
            }

            //If the file exists and the user is logged in
            else {
                System.out.println("File found: " + filename);

                byte[] byteArray = new byte[(int) file.length()]; //Array of bytes which will hold the file
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.read(byteArray); //Convert the file into bytes
                } 
                catch (IOException e) {}
                fileToSend = byteArray;
                connections.send(connectionId, startSending(fileToSend, (short)1));
            }

            //If there is an error to send, send the mimimum code of the error
            if (shouldSendError) {
                byte[] error = buildError(minErrorCode); //Create ERROR packet
                connections.send(connectionId, error); //Send to the client the ERROR packet
                shouldSendError = false; //Reset
                minErrorCode = (short)(8); //Reset
            }
        }
 //--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
        else if (message[1] == 0x0002) { //Write Request
            String filename = new String(message, 2, packetLength(message, 2), StandardCharsets.UTF_8); // Get the file name from the packet
            fileNameToSave = filename;
            
            //Check the file exists
            if (new File("Files/", filename).exists()) {
                shouldSendError = true;
                short currentError = (short)(5);
                if (currentError < minErrorCode) {
                    minErrorCode = currentError;
                }
            } 

            //If the file doesn't exists and the user is not logged in
            else if (!isUserLoggedIn()) {
                shouldSendError = true;
                short currentError = (short)(6);
                if (currentError < minErrorCode) {
                    minErrorCode = currentError;
                }
            }

            //If the file doesn't exists and the user is logged in
            else {
                byte[] ack = buildACK((short)(0)); //Acknoledges the request
                connections.send(connectionId, ack);
                startRecieve();
            }

            //If there is an error to send, send the mimimum code of the error
            if (shouldSendError) {
                byte[] error = buildError(minErrorCode); //Create ERROR packet
                connections.send(connectionId, error); //Send to the client the ERROR packet
                shouldSendError = false; //Reset
                minErrorCode = (short)(8); //Reset
            }
        }
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
        else if (message[1] == 0x0003) { //Data
            byte[] packetSizeBytes = new byte[2];
            packetSizeBytes[0] = message[2];
            packetSizeBytes[1] = message[3];
            short packetSize = (short)(((short)packetSizeBytes[0]) << 8 | (short)(packetSizeBytes[1]) & 0x00ff);
            byte[] blockNumberBytes = new byte[2];
            blockNumberBytes[0] = message[4];
            blockNumberBytes[1] = message[5];
            short blockNumber = (short)(((short)blockNumberBytes[0]) << 8 | (short) (blockNumberBytes[1]) & 0x00ff);
            if(packetSize!=1&message[6]!=0)
            {
                int len = fileToSave.length;
                fileToSave = Arrays.copyOf(fileToSave, fileToSave.length + packetSize);
                for (int i = 6; i < (int)(packetSize) + 6; i++) {
                    fileToSave[len + i - 6] = message[i];
                }
            }
            System.out.println("Recived DATA packet with block number " + blockNumber + " and length " + packetSize);

            byte[] ack = buildACK((short)(blockNumber)); //Create ACK packet
            connections.send(connectionId, ack); //Send to the client the ACK packet
            System.out.println("Sent ACK packet with block number " + blockNumber);

            if ((int)(packetSize) < 512) {
                try {
                    FileOutputStream os = new FileOutputStream("Files/" + fileNameToSave);
                    try {
                        os.write(fileToSave);
                        BCAST((byte) 0x0001, fileNameToSave);
                    } catch (IOException e) {}
                    try {
                        os.close();
                    } catch (IOException e) {}

                } catch (FileNotFoundException e) {}
            }
        }
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
        else if (message[1] == 0x0004) { //ACK
            //Ack the data recieved from the client
            //Once we get ACK packet that means we cand send the rest of the data to the client
            short packetNum = (short)(((short) message[2]) << 8 | (short)(message[3]) & 0x00ff);
            System.out.println("Recived ACK packet with block number " + packetNum);
            packetNum++;
            if (isThereMoreToSend(fileToSend, packetNum)) {
                connections.send(connectionId, startSending(fileToSend, packetNum));
            }
        }
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
        else if (message[1] == 0x0006) { //Directory Request
            if (isUserLoggedIn()) {
                File file = new File("Files/");
                File[] directoryListing = file.listFiles(); //List of the files in the server

                //Check how many bytes in the directory
                int directoryBytesLength = 0;
                String lastFileName = "";
                if (directoryListing != null) {
                    for (File currentFile : directoryListing) { // For each file in the server
                        directoryBytesLength += currentFile.getName().length();
                        lastFileName = currentFile.getName();
                    }
                }

                //Create the DATA packet
                byte[] byteArray = new byte[directoryBytesLength + (int)(directoryListing.length) - 1]; //The byte array which will hold all the files names
                int readToThisIndex = 0; //The index of the array which we read the file to
                int currentFileNameLength = 0;;
                if (directoryListing != null) {
                    for (File currentFile : directoryListing) { //For each file in the server
                        currentFileNameLength = currentFile.getName().length();
                        for (int i = 0; i < currentFileNameLength; i++){
                            byteArray[readToThisIndex + i] = (byte)(currentFile.getName().charAt(i));
                        } 
                        if (!currentFile.getName().equals(lastFileName)) {
                            readToThisIndex += currentFileNameLength;
                            byteArray[readToThisIndex] = (byte)(0); //Add 0 after the file name
                            readToThisIndex++;
                        }
                    }

                }
                fileToSend = byteArray;
                connections.send(connectionId, startSending(fileToSend, (short)(1)));
            } 
            else {
                byte[] error = buildError((short)(6)); //Create ERROR packet
                connections.send(connectionId, error); //Send to the client the ERROR packet
            }
        }
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
        else if (message[1] == 0x0007) { //Login Request
            String userName = new String(message, 2, packetLength(message, 2), StandardCharsets.UTF_8); // Get the username from the packet
            if (!this.connections.idsUserName.contains(userName)) { //If this is a new user name
                this.connections.idsUserName.put(connectionId, userName); //Add the user name the the hash map
                System.out.println(userName + " Logged in");
                byte[] ack = buildACK((short)(0)); //Create ACK packet with block number 0
                connections.send(connectionId, ack); //Send to the client the ACK packet
            } 
            else {
                byte[] error = buildError((short)(7)); //Create ERROR packet
                connections.send(connectionId, error); //Send to the client the ERROR packet
            }
        }
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
        else if (message[1] == 0x0008) { //Deletion Request
            String filename = new String(message, 2, packetLength(message, 2), StandardCharsets.UTF_8); // Get the  file name from the packet
            File file = new File("Files/", filename); // The file
            
            //Check the file doesn't exists
            if (!file.exists()) {
                shouldSendError = true;
                short currentError = (short)(1);
                if (currentError < minErrorCode) {
                    minErrorCode = currentError;
                }
            } 

            //If the file exists and the user is not logged in
            else if (!isUserLoggedIn()) {
                shouldSendError = true;
                short currentError = (short)(6);
                if (currentError < minErrorCode) {
                    minErrorCode = currentError;
                }
            }

            //If the file exists and the user is logged in
            else {
                file.delete(); //Delete the file
                byte[] ack = buildACK((short)(0)); //Create ACK packet with block number 0
                connections.send(connectionId, ack); //Send to the client the ACK packet
                BCAST((byte) 0x0000, filename);
                System.out.println("Recived DELETE packet to file " + filename);
            }

            //If there is an error to send, send the mimimum code of the error
            if (shouldSendError) {
                byte[] error = buildError(minErrorCode); //Create ERROR packet
                connections.send(connectionId, error); //Send to the client the ERROR packet
                shouldSendError = false; //Reset
                minErrorCode = (short)(8); //Reset
            }
        }
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
        // case 0x0009: { //Broadcast Request
        // Apperently server doesn't recive broadcast
        // }
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
        else if (message[1] == 0x000A) { //Disconect
            if (this.connections.idsUserName.containsKey(connectionId)) { //If the user is logged in
                this.connections.idsUserName.remove(connectionId); //Remove the user from logged in users
                byte[] ack = buildACK((short)(0)); //Create ACK packet with block number 0
                connections.send(connectionId, ack); //Send to the client the ACK packet
                this.connections.disconnect(this.connectionId);
            } 
            else {
                byte[] error = buildError((short)(6)); //Create ERROR packet
                connections.send(connectionId, error); //Send to the client the ERROR packet
            }
            shouldTerminate = true;
        }
        // --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
        else {
            byte[] error = buildError((short)(4)); //Create ERROR packet
            connections.send(connectionId, error); //Send to the client the ERROR packet
        }
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

    private void startRecieve() {
        fileToSave = new byte[0];
    }

    private void BCAST(byte situation, String fileName) {
        byte[] response;
        if (situation == 0x0000) {
            response = (fileName).getBytes();
        } 
        else {
            response = (fileName).getBytes();
        }
        short op = 9;
        byte[] result = new byte[response.length + 4];
        byte[] opcode = new byte[] {(byte)(op >> 8), (byte)(op & 0xff)}; //Array of bytes with the op code
        result[0] = opcode[0];
        result[1] = opcode[1];
        result[2] = situation;
        for (int i = 0; i < response.length; i++) {
            result[i + 3] = response[i];
        }
        result[result.length - 1] = (byte)(0);
        for (Integer i : this.connections.idsLogin.keySet()) {
            if (this.connections.idsUserName.containsKey(i)) {
                connections.send(i, result);
            }
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private int packetLength(byte[] message, int point) { //Returns the packet length no including the op and the 0 at the end
        int res = 0; //Counter starts with 0
        for (int i = point; i < message.length; i++) { //Go go through the packet
            if (message[i] != 0x0000) { //If byte is not a 0
                res++;
            } 
            else { //If the byte is a 0
                return res;
            }
        }
        return res;
    }

    private boolean isUserLoggedIn() { //Returns true iff the user is logged in
        return this.connections.idsUserName.containsKey(connectionId);
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

    private byte[] buildError(short errorCode) { //Builds an ERROR packet
        short op = 5;
        byte[] opBytes = new byte[] {(byte)(op >> 8), (byte)(op & 0xff) }; // Array of bytes with the op code
        byte[] errorCodeBytes = new byte[] {(byte)(errorCode >> 8), (byte)(errorCode & 0xff) }; // Array of byes with the error code
        // We can add the error message
        String errormsg = "Undefined error";
        if (errorCode == 1) {
            errormsg = "File doesn't exist";
        } 
        else if (errorCode == 2) {
            errormsg = "file is not allowed to be written/read/deleted";
        } 
        else if (errorCode == 3) {
            errormsg = "Not enough room for the file";
        } 
        else if (errorCode == 4) {
            errormsg = "Unknown op code";
        } 
        else if (errorCode == 5) {
            errormsg = "File already exist";
        } 
        else if (errorCode == 6) {
            errormsg = "Log in before making any requests";
        } 
        else if (errorCode == 7) {
            errormsg = "This username is already connected";
        }
        byte[] messageByte = errormsg.getBytes();
        byte[] errorPacket = new byte[5 + messageByte.length]; // Array of bytes with the full packet
        //Marge op and error code into the ERROR packet
        errorPacket[0] = opBytes[0];
        errorPacket[1] = opBytes[1];
        errorPacket[2] = errorCodeBytes[0];
        errorPacket[3] = errorCodeBytes[1];
        for (int j = 0; j < messageByte.length; j++) {
            errorPacket[j + 4] = messageByte[j];
        }
        errorPacket[errorPacket.length - 1] = (byte)(0);
        System.out.println("Sent ERROR packet with code " + (errorCode) + " and message " + errormsg);
        return errorPacket;
        
    }

    public void setHandler(ConnectionHandler<byte[]> handler) {
        this.handler = handler;
    }

}
