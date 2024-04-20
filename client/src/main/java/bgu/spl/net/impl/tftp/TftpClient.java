package bgu.spl.net.impl.tftp;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;
import java.util.List;
import java.util.LinkedList;

public class TftpClient {

    private final String serverIp;
    private final int serverPort;
    private Socket socket;
    private BufferedOutputStream out;
    private BufferedInputStream in;
    private TftpClientEncoderDecoder encdec;
    private TftpClientProtocol protocol;
    private Thread keyboard;
    private Thread listen;
    

    public TftpClient(String serverIp, int serverPort) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.encdec = new TftpClientEncoderDecoder();
        this.protocol = new TftpClientProtocol();

    }

    public void connect() throws IOException {
        socket = new Socket(serverIp, serverPort);
        in = new BufferedInputStream(socket.getInputStream());
        out = new BufferedOutputStream(socket.getOutputStream());
        System.out.println("Connection successful");
    }

    public void start() {
        System.out.println("Client thread started");
        listen= new Thread(this::listenToServer, "Listening-Thread");
        keyboard=new Thread(this::readFromKeyboard, "Keyboard-Thread");
        listen.start();
        keyboard.start();
    }


    private void listenToServer() {
       try { 
            int read;
            while (!protocol.shouldTerminate && (read = in.read()) >= 0) {
                byte[] nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    nextMessage = protocol.process(nextMessage);
                    if (nextMessage != null) {
                        if (nextMessage[1] == 0x0003 || nextMessage[1] ==  0x0004) { //Sends out the response to the client if necessary, in the case of receiving a DATA/ACK packet, send an ACK/DATA packet respectively.
                            send(nextMessage);                                       //DATA packets are meant to be created by the keyboard thread and inputted into protocol's packetsToSend in order.
                        } 
                        else { //Error or bcast, need a synchronized print method.
                            String tobePrinted = new String(nextMessage);
                            print(tobePrinted); //Print needs to be implemented as a synchronized printing method, and should be used by the keyboard thread whenever it's printing.
                        }  
                    }    
                }
            }
            if(protocol.shouldTerminate){
                try{
                    socket.close();
                }
                catch(IOException e){}
            }
        }
        catch (IOException e) {e.printStackTrace();}
        System.out.println("Listening thread closing.");
    }
    private void readFromKeyboard() {
        Scanner inputScanner = new Scanner(System.in);
        protocol.shouldTerminate = false;
        while (!protocol.disc) {
            String line = inputScanner.nextLine();
            String[] parts = line.split(" ", 2);
            byte opcode;
            byte[] packet;
            if(parts[0].compareTo("RRQ") == 0 && parts.length == 2) {
                opcode = 1;
                protocol.fileNameToSave = parts[1];
                byte[] filename = parts[1].getBytes();
                if (!new File(parts[1]).exists()) {
                    packet = new byte[2 + filename.length + 1];
                    packet[0] = 0;
                    packet[1] = opcode;
                    System.arraycopy(filename, 0, packet, 2, filename.length);
                    packet[packet.length - 1] = 0;
                    protocol.fileToReceive = new byte[0];
                    protocol.sending = false;
                    protocol.readOrDir = true;
                    send(packet);
                    try {synchronized (protocol.keyboardLock) {protocol.keyboardLock.wait();}}
                    catch (InterruptedException e) {e.printStackTrace();}
                }
                else {
                    print("Already have that file");
                }
            }
            else if(parts[0].compareTo("WRQ") == 0 && parts.length == 2) {
                opcode = 2;
                byte[] filename = parts[1].getBytes();
                File file = new File(parts[1]);
                if (file.exists()) {
                    byte[] byteArray = new byte[(int) file.length()];
                    try (FileInputStream fis = new FileInputStream(file)) {
                        fis.read(byteArray); //Convert the file into bytes
                    } 
                    catch (IOException e) {}
                    protocol.fileToSend = byteArray;
                    protocol.sending = true;
                    packet = new byte[2 + filename.length + 1];
                    packet[0] = 0;
                    packet[1] = opcode;
                    System.arraycopy(filename, 0, packet, 2, filename.length);
                    packet[packet.length - 1] = 0;
                    send(packet);
                    try {synchronized (protocol.keyboardLock) {protocol.keyboardLock.wait();}}
                    catch (InterruptedException e) {e.printStackTrace();}
                }
                else {
                    print("File doesn't exist.");
                }
            }
            else if(parts[0].compareTo("DIRQ") == 0) {
                opcode = 6;
                packet = new byte[2];
                packet[0] = 0;
                packet[1] = opcode;
                protocol.readOrDir = false;
                protocol.fileToReceive = new byte[0];
                send(packet);
                try {synchronized (protocol.keyboardLock) {protocol.keyboardLock.wait();}}
                catch (InterruptedException e) {e.printStackTrace();}
            }
            else if(parts[0].compareTo("LOGRQ") == 0 && parts.length == 2) {
                opcode = 7;
                byte[] username = parts[1].getBytes();
                packet = new byte[2 + username.length + 1];
                packet[0] = 0;
                packet[1] = opcode;
                System.arraycopy(username, 0, packet, 2, username.length);
                packet[packet.length - 1] = 0;
                protocol.sending = false;
                send(packet);
                try {synchronized (protocol.keyboardLock) {protocol.keyboardLock.wait();}}
                catch (InterruptedException e) {e.printStackTrace();}
            }
            else if(parts[0].compareTo("DELRQ") == 0 && parts.length == 2) {
                opcode = 8;
                byte[] filename = parts[1].getBytes();
                packet = new byte[2 + filename.length + 1];
                packet[0] = 0;
                packet[1] = opcode;
                System.arraycopy(filename, 0, packet, 2, filename.length);
                packet[packet.length - 1] = 0;
                protocol.sending =  false;
                send(packet);
                try {synchronized (protocol.keyboardLock) {protocol.keyboardLock.wait();}}
                catch (InterruptedException e) {e.printStackTrace();}
            }
            else if(parts[0].compareTo("DISC")  == 0) {
                packet = new byte[2];
                packet[0] = 0;
                packet[1] = 0x000a;
                protocol.disc = true;
                send(packet);
            }
            else {
                System.out.println("Invalid command.");
            }
        }
        inputScanner.close();
        System.out.println("Keyboard thread closing.");
    }


    public static void main(String[] args) {
        TftpClient client = new TftpClient(args[0], Integer.parseInt(args[1]));
        try {
            System.out.println("Main started");
            client.connect();
            client.start();
        } catch (IOException e) {}
    }

    public synchronized void send(byte[] message) { //Synchronized in case the keyboard thread is printing and the listening thread received a bcast.
        try {
            out.write(message);
            out.flush();
        }
        catch (IOException exception) {exception.printStackTrace();}
    }

    public synchronized void print (String msg) {
        System.out.println(msg);
    }
}
