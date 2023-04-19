package com.nedap.university.client;

import com.nedap.university.Protocol;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;


/*
* Datagram packets can be only bytes
* */

public class Client {

    private static final int PORT = 9090;
    private static final int HEADER_SIZE = 8; // 4 bytes for seq, 4 bytes for ack
    private static final int PAYLOAD_SIZE = 1024;
    private static final int BUFFER_SIZE = PAYLOAD_SIZE + HEADER_SIZE;
    private static final String pathToDirectory = "/Users/anniek.bisschop/Networking/network-systems/src/main/java/com/nedap/university/download/";
    public static void main(String[] args) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();

            // Get the IP address of the server we want to send data to
            InetAddress serverAddress = InetAddress.getByName("localhost");

            if (connectToServer(socket, serverAddress)) {
                // connection established, continue with file transfer
                System.out.println("Welcome, You have successfully connected to the server.");
            }



            // receive response from server
            byte[] receiveBuffer = new byte[1024 + HEADER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);
            int seqNum = Protocol.getSeqNum(receivePacket.getData());
            int ackNum = Protocol.getAckNum(receivePacket.getData());

            // extract the data from the packet and convert it to a string
            byte[] data = new byte[receivePacket.getLength() - HEADER_SIZE];
            System.arraycopy(receivePacket.getData(), HEADER_SIZE, data, 0, data.length);
            String message = new String(data);
            System.out.println(message);


            // read user choice from console
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

            printMenu(); // Call the printMenu() method before the while loop.

            while (true) {
                String choice = in.readLine();

                switch (choice) {
                    case "1":
                        uploadFile(socket, serverAddress, in, receivePacket);
                        break;
                    case "2":
                        downloadFile(socket, serverAddress, in, receivePacket, seqNum);
                        break;
                    case "3":
                        removeFile(socket, serverAddress, in, receivePacket, seqNum);
                        break;
                    case "4":
                        replaceFile(socket, serverAddress, in, receivePacket, seqNum);
                        break;
                    case "5":
                        showList(socket, serverAddress, receivePacket, seqNum);
                        break;
                    case "6":
                        System.out.println("Exiting program...");
                        // TODO: MORE CODE FOR EXITING PROGRAM?
                        System.exit(0);
                        break;
                    default:
                        System.out.println("Invalid choice");
                        break;
                }

                printMenu(); // Call the printMenu() method after each iteration of the while loop.
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // close the socket after the user exits the program
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private static void sendHelloPacketToServer(DatagramSocket socket, InetAddress serverAddress){
        // create the header
        byte[] header = Protocol.createHeader(0,0);

        // create the data string and convert it to a byte array
        String dataString = "Hello";
        byte[] data = dataString.getBytes();

        // create a new byte array that combines the header and data
        byte[] packetData = new byte[header.length + data.length];
        System.arraycopy(header, 0, packetData, 0, header.length);
        System.arraycopy(data, 0, packetData, header.length, data.length);

        // create and send the packet
        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, serverAddress, PORT);
        try {
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("IOException occurred while sending packet to server: " + e.getMessage());
        }
    }


    private static void receiveAckFromServer(DatagramSocket socket, int expectedSeqNum) throws IOException {
        byte[] ackBuffer = new byte[HEADER_SIZE];
        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, HEADER_SIZE);
        socket.receive(ackPacket);
        byte[] header = Arrays.copyOfRange(ackPacket.getData(), 0, HEADER_SIZE);
        int receivedSeqNum = Protocol.getSeqNum(header);
        if (receivedSeqNum == expectedSeqNum) {
            System.out.println("Ack received for packet " + expectedSeqNum);
        } else {
            System.out.println("Unexpected ack received: expected " + expectedSeqNum + " but received " + receivedSeqNum);
        }
    }

    private static void uploadFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in, DatagramPacket receivePacket) throws IOException {
        try {
            // send upload request to server
            System.out.print("Enter path to file you want to upload: ");
            String filePath = in.readLine();
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("File not found.");
                return;
            }
            // calculate total number of packets to be sent
            int numPackets = (int) Math.ceil(file.length() / (double) PAYLOAD_SIZE);
            byte[] fileData = Files.readAllBytes(Path.of(filePath));
            System.out.println("number of Packets is: " + numPackets);
            String expectedHash = Protocol.getHash(fileData);


            byte[] header = Protocol.createHeader(0, 0);
            String message = "upload " + file.getName() + " " + numPackets + " " + expectedHash;
            commandRequestToServer(socket, serverAddress, header, message);

            int expectedSeqNum = Protocol.getSeqNum(header);
            boolean ackReceived = false;
            int maxRetries = 3;
            int numRetries = 0;

            while (!ackReceived && numRetries < maxRetries) {
                commandRequestToServer(socket, serverAddress, header, message);
                System.out.println("upload request sent");

                try {
                    socket.setSoTimeout(5000);
                    receiveAckFromServer(socket, expectedSeqNum);
                    System.out.println("upload ack packet received from server");
                    ackReceived = true;
                    socket.setSoTimeout(0);
                } catch (SocketTimeoutException e) {
                    numRetries++;
                    System.out.println("Timeout occurred, retrying...");
                }
            }

            if (ackReceived) {
                System.out.println("upload request acknowledged");
            } else {
                System.out.println("upload request failed after " + maxRetries + " attempts");
                System.out.println("Returning to main menu, please try again...");
                return; // exit the method and return to the main menu
            }



            // set the maximum size of each packet to 1024 bytes
            int maxPacketSize = 1024;
            int seqNum = 0;

            // create and send packets for each chunk of the file data
            for (int i = 0; i < fileData.length; i += maxPacketSize) {
                // Extract a portion of the file data as a new byte array starting from index i and up to a maximum of maxPacketSize bytes or less if the end of the file has been reached.
                byte[] chunkData = Arrays.copyOfRange(fileData, i, Math.min(i + maxPacketSize, fileData.length));
                DatagramPacket packet = Protocol.createResponsePacket(chunkData, socket, receivePacket, seqNum);
                // send the packet and wait for the response with the expected sequence number
                boolean receivedExpectedSeqNum = false;
                while (!receivedExpectedSeqNum) {
                    socket.send(packet);
                    // wait for the ack packet with the expected sequence number
                    DatagramPacket ackPacket = Protocol.receiveAck(socket, receivePacket, seqNum);

                    int receivedSeqNum = Protocol.getSeqNum(ackPacket.getData());
                    System.out.println("Ack received seqnum: " + receivedSeqNum);
                    // print the contents of the ACK packet
                    byte[] ackData = receivePacket.getData();
                    System.out.println("ACK data: " + new String(ackData, 0, receivePacket.getLength()));

                    if (receivedSeqNum == seqNum) {
                        receivedExpectedSeqNum = true;
                        seqNum++;
                    }
                }
            }

            // send final packet with end-of-file message
            byte[] eofMsg = "END_OF_FILE".getBytes();
            DatagramPacket eofPacket = Protocol.createResponsePacket(eofMsg, socket, receivePacket, seqNum);
            socket.send(eofPacket);
            System.out.println("Final packet sent with seqnum " + seqNum);

            Protocol.receiveAck(socket, receivePacket,seqNum);
            byte[] ackData = receivePacket.getData();
            System.out.println("ACK data: " + new String(ackData, 0, receivePacket.getLength()));
            System.out.println("End ack received");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
    }
    private static void downloadFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in, DatagramPacket receivePacket, int seqNum) throws IOException {
//        showList(socket,serverAddress,receivePacket,seqNum);
        System.out.println("Please enter a file that is on the list");
        // send download request to server
        System.out.print("Filename: ");
        String fileName = in.readLine();
        File file = new File(fileName);

        byte[] header = Protocol.createHeader(0,  0);
        String message = "download " + file.getName();
        commandRequestToServer(socket, serverAddress, header, message);

        // receive the response from the server
        Protocol.receiveAck(socket, receivePacket, seqNum);
        // print the contents of the ACK packet
        byte[] ackData = receivePacket.getData();
        System.out.println("ACK data: " + new String(ackData, 0, receivePacket.getLength()));

        byte[] receiveData = Protocol.receiveData(socket, header.length);
        String response = new String(receiveData);
        System.out.println("Received message: " + response.trim());

// split the response into parts using whitespace as the delimiter
        String[] parts = response.trim().split("\\s+");

// select the third element (index 2) from the parts array
        String amountPackages = parts[5];
        String hashFromServer = parts[6];
        System.out.println("amount packages " + amountPackages + " hash: " + hashFromServer);


//        Protocol.receiveAck(socket,receivePacket,seqNum);
//        // print the contents of the ACK packet
//        ackData = receivePacket.getData();
//        System.out.println("ACK data: " + new String(ackData, 0, receivePacket.getLength()));


        // create a File object to represent the downloaded file
        file = new File(pathToDirectory + fileName);
        System.out.println("na new File");
        // create a buffer to hold the incoming data
        byte[] buffer = new byte[BUFFER_SIZE + 8];
        int numPacketsReceived = 0;

        // create a FileOutputStream to write the data to a file
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        System.out.println("file outputstream");

        System.out.println("start receiving packets....");
        while (numPacketsReceived < Integer.parseInt(amountPackages)) {
            // create a DatagramPacket to receive the packet from the client
            DatagramPacket filePacket = new DatagramPacket(buffer, buffer.length);
            System.out.println("voor filepacket");
            socket.receive(filePacket);
            System.out.println("na filepacket");

            String packetData = new String(filePacket.getData(), 0, filePacket.getLength());
            if (packetData.contains("END_OF_FILE")) {
                // send an ACK to the client
                Protocol.sendAck(socket, receivePacket, Protocol.getSeqNum(filePacket.getData()));
                break;  // exit the loop to finish receiving the file
            }

            int packetSeqNum = Protocol.getSeqNum(filePacket.getData());
            numPacketsReceived++;

            // write the payload to the output file starting after the header
            fileOutputStream.write(filePacket.getData(), HEADER_SIZE, filePacket.getLength() - HEADER_SIZE);
            fileOutputStream.flush();
            // send an ACK to the client
            Protocol.sendAck(socket, receivePacket, packetSeqNum);
        }

        String filePath = file.getPath(); // get the path of the file
        File downloadedFile = new File(filePath);

        boolean hashesMatch = Protocol.checkFileHash(downloadedFile, hashFromServer);
        if (hashesMatch) {
            System.out.println("\u001B[32mThis file is safe to open, hashes match\u001B[0m");
        } else {
            System.err.println("This file could be corrupted");
        }

        fileOutputStream.close();

    }


private static void removeFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in, DatagramPacket receivePacket, int seqNum) {
    try {
        System.out.print("Enter name of file you want to remove: ");
        String fileName = in.readLine();

        // Send remove request to server
        byte[] header = Protocol.createHeader(1, 0);
        String message = "remove " + fileName;
        int expectedSeqNum = Protocol.getSeqNum(header);
        boolean ackReceived = false;
        int maxRetries = 3; // maximum number of times to retry sending the packet
        int numRetries = 0; // number of times the packet has been retried

        while (!ackReceived && numRetries < maxRetries) {
            commandRequestToServer(socket, serverAddress, header, message);
            System.out.println("remove request sent");
//
            // Receive ack from server with a timeout of 5 seconds
            try {
                socket.setSoTimeout(5000); // set the socket timeout to 5 seconds
                Protocol.receiveAck(socket,receivePacket,seqNum);
                ackReceived = true; // set the flag to true if the ack is received
                socket.setSoTimeout(0);
            } catch (SocketTimeoutException e) {
                numRetries++; // increment the retry count if the timeout occurs
                System.out.println("Timeout occurred, retrying...");
            }
        }

        if (ackReceived) {
            System.out.println("remove req acknowledged");
        } else {
            System.out.println("remove req failed after " + maxRetries + " attempts");
            System.out.println("Returning to main menu, please try again...");
            return; // exit the method and return to the main menu
        }
        // Receive response from server
        byte[] receiveData = Protocol.receiveData(socket, header.length);
        String response = new String(receiveData);
        System.out.println("Received message: " + response.trim());

    } catch (IOException e) {
        System.out.println("Error occurred while trying to remove file from server: " + e.getMessage());
    }
}
    private static void commandRequestToServer(DatagramSocket socket, InetAddress serverAddress, byte[] header, String message) throws IOException {
        byte[] commandBuffer = message.getBytes();
        byte[] command = new byte[header.length + commandBuffer.length];
        System.arraycopy(header, 0, command, 0, header.length);
        System.arraycopy(commandBuffer, 0,command, header.length, commandBuffer.length);
        DatagramPacket commandPacket = new DatagramPacket(command, command.length, serverAddress, PORT);
        socket.send(commandPacket);
    }


    private static void replaceFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in, DatagramPacket receivePacket, int seqNum) throws IOException {
        try {
            // send upload request to server
            System.out.print("Enter the file you want to replace on the server: ");
            String fileName = in.readLine();

            byte[] header = Protocol.createHeader(0, 0);
            String message = "replace " + fileName;
            commandRequestToServer(socket, serverAddress, header, message);

            int expectedSeqNum = Protocol.getSeqNum(header);
            boolean ackReceived = false;
            int maxRetries = 3;
            int numRetries = 0;

            while (!ackReceived && numRetries < maxRetries) {
                try {
                    commandRequestToServer(socket, serverAddress, header, message);
                    System.out.println("replace request sent");
                    receiveAckFromServer(socket, expectedSeqNum);
                    socket.setSoTimeout(5000);
                    System.out.println("replace ack packet received from server");
                    ackReceived = true;
                    socket.setSoTimeout(0);
                } catch (SocketTimeoutException e) {
                    numRetries++;
                    System.out.println("Timeout occurred, retrying...");
                }
            }

            if (ackReceived) {
                System.out.println("replace request acknowledged");
            } else {
                System.out.println("replace request failed after " + maxRetries + " attempts");
                System.out.println("Returning to main menu, please try again...");
                return; // exit the method and return to the main menu
            }

            //receive msg
            byte[] receiveData = Protocol.receiveData(socket, header.length);
            String response = new String(receiveData);
            System.out.println(response.trim() + "\nType yes or no");

            String replaceQuestion = in.readLine();

            if (replaceQuestion.equalsIgnoreCase("yes")) {
                String msg = "YES_DO_A_REPLACE";
                DatagramPacket responsePacket = Protocol.createResponsePacket(msg, socket, receivePacket, 1);
                socket.send(responsePacket);
                System.out.println("Enter the path to the file you want to replace");
            } else if (replaceQuestion.equalsIgnoreCase("no")) {
                System.out.println("No selected. Returning to main menu");
                return;
            }else{
                System.out.println("Invalid option. Returning to main menu");
                return;
            }

            uploadFile(socket, serverAddress, in, receivePacket);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



private static void showList(DatagramSocket socket, InetAddress serverAddress, DatagramPacket receivePacket, int seqNum) {
    try {
        // Send list request to server
        byte[] header = Protocol.createHeader(1,  0);
        String message = "list";
        commandRequestToServer(socket, serverAddress, header, message);
        System.out.println("list request send");

        // Receive ack from server
       Protocol.receiveAck(socket,receivePacket,seqNum);

        // Receive list from server
        byte[] listBufferResponse = new byte[1024];
        DatagramPacket listPacketResponse = new DatagramPacket(listBufferResponse, listBufferResponse.length);
        socket.receive(listPacketResponse);
        System.out.println("length packet " + listPacketResponse.getLength());

        // Send acknowledgment back to the server
        Protocol.sendAck(socket, receivePacket, seqNum);
        System.out.println("Ack sent with receivepacket: " + receivePacket.getLength() + "seqnum: " + seqNum);

        // Extract and print file list
        byte[] data = listPacketResponse.getData();
        byte[] responseData = Arrays.copyOfRange(data, HEADER_SIZE, data.length);
        String responseList = new String(responseData, 0, listPacketResponse.getLength() - HEADER_SIZE);

        System.out.println(responseList);


    } catch (IOException e) {
        e.printStackTrace();
    }
}

    public static boolean connectToServer(DatagramSocket socket, InetAddress serverAddress) {
        boolean ackReceived = false;
        int maxTries = 3; // set maximum number of tries to send the hello packet
        int tries = 0; // initialize the number of tries

        while (!ackReceived && tries < maxTries) {
            try {
                sendHelloPacketToServer(socket, serverAddress);
                if (tries > 0) {
                    System.out.println("Hello packet retransmitted...");
                }
                // set timeout for receiving acknowledgment from server
                socket.setSoTimeout(5000); // 5 seconds

                // receive acknowledgement from server
                byte[] receiveBuffer = new byte[HEADER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(receivePacket);
                int seqNum = Protocol.getSeqNum(receivePacket.getData());
                int ackNum = Protocol.getAckNum(receivePacket.getData());
                ackReceived = true;
                socket.setSoTimeout(0); // disable timeout
            } catch (SocketTimeoutException e) {
                System.out.println("Acknowledgement not received, trying again...");
                tries++;
                if (tries == maxTries) {
                    System.out.println("Please try to make a new connection to the server. Program stopped: " + e.getMessage());
                }
            } catch (IOException e) {
                System.out.println("Please try to make a new connection to the server. Program stopped.");
                System.out.println("IOException occurred while communicating with server: " + e.getMessage());
                System.exit(1);
            }
        }

        return ackReceived;
    }
private static void printMenu(){
        System.out.println("\nMenu Options:");
        System.out.println("1. Upload a file");
        System.out.println("2. Download a file");
        System.out.println("3. Remove a file");
        System.out.println("4. Replace a file");
        System.out.println("5. List available files");
        System.out.println("6. Exit");
        System.out.println("Please choose an option 1-6");
    }
}
