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
    private static final String pathToDirectory = "";
    // Set maximum number of retransmissions
    private static final int MAX_RETRANSMITS = 100;
    // Set timeout value to 1 seconds
    private static final int RETRANSMIT_TIMEOUT = 1000;
    public static void main(String[] args) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();

            // Get the IP address of the server we want to send data to
            InetAddress serverAddress = InetAddress.getByName("localhost");
            //172.16.1.1

            if (connectToServer(socket, serverAddress)) {
                // connection established, continue with file transfer
                System.out.println("Welcome, You have successfully connected to the server.");
            }

            // receive response from server
            byte[] receiveBuffer = new byte[1024 + HEADER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);
            int seqNum = Protocol.getSeqNum(receivePacket.getData());

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

            printMenu();

            while (true) {
                String choice = in.readLine();

                switch (choice) {
                    case "1" -> uploadFile(socket, serverAddress, in, receivePacket);
                    case "2" -> downloadFile(socket, serverAddress, in, receivePacket, seqNum);
                    case "3" -> removeFile(socket, serverAddress, in, receivePacket, seqNum);
                    case "4" -> replaceFile(socket, serverAddress, in, receivePacket, seqNum);
                    case "5" -> showList(socket, serverAddress, receivePacket, seqNum);
                    case "6" -> {
                        System.out.println("Exiting program...");
                        in.close();
                        socket.close();
                        System.exit(0);
                    }
                    default -> System.out.println("Invalid choice");
                }

                printMenu();
            }

        } catch (IOException e) {
            System.err.println("An IO error occurred: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
        } finally {
            // close the socket after the user exits the program
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
    private static void sendHelloPacketToServer(DatagramSocket socket, InetAddress serverAddress) {
        // create the header
        byte[] header = Protocol.createHeader(0, 0);

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
            // ACK received for the expected packet
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

                try {
                    receiveAckFromServer(socket, expectedSeqNum);
                    ackReceived = true;
                } catch (SocketTimeoutException e) {
                    numRetries++;
                    System.out.println("Timeout occurred, retrying...");
                }
            }

            if (ackReceived) {
                //upload request acknowledged
            } else {
                System.out.println("upload request failed after " + maxRetries + " attempts");
                System.out.println("Returning to main menu, please try again...");
                return; // exit the method and return to the main menu
            }
            // set the maximum size of each packet to 1024 bytes
            int maxPacketSize = 1024;
            int seqNum = 0;

            // create and send packets for each chunk of the file data
            boolean packetTransmissionFailed = false;
            for (int i = 0; i < fileData.length; i += maxPacketSize) {
                if (packetTransmissionFailed) {
                    break;
                }
                // Extract a portion of the file data as a new byte array starting from index i and up to a maximum of maxPacketSize bytes or less if the end of the file has been reached.
                byte[] chunkData = Arrays.copyOfRange(fileData, i, Math.min(i + maxPacketSize, fileData.length));
                DatagramPacket packet = Protocol.createResponsePacket(chunkData, socket, receivePacket, seqNum);
                // send the packet and wait for the response with the expected sequence number
                boolean receivedExpectedSeqNum = false;
                long startTime = System.currentTimeMillis();// get the start time
                int retransmitCounter = 0; // initialize the retransmit counter
                int overallTimeout = MAX_RETRANSMITS * RETRANSMIT_TIMEOUT;
                while (!receivedExpectedSeqNum && !packetTransmissionFailed) {
                    socket.send(packet);
                    try {
                        // wait for the ack packet with the expected sequence number
                        DatagramPacket ackPacket = Protocol.receiveAck(socket, receivePacket, seqNum);
                        int receivedSeqNum = Protocol.getSeqNum(ackPacket.getData());
                        if (receivedSeqNum == seqNum) {
                            receivedExpectedSeqNum = true;
                            seqNum++;
                            retransmitCounter = 0;
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout occurred, resending packet");
                        retransmitCounter++; // increment the retransmit counter
                        if (retransmitCounter >= MAX_RETRANSMITS) {
                            // assume packet is lost and retransmit
                            System.out.println("Maximum retransmits exceeded");
                            packetTransmissionFailed = true;
                        }
                    }
                    // check if the maximum time limit has been exceeded
                    if (System.currentTimeMillis() - startTime >= overallTimeout) {
                        System.out.println("Maximum time limit exceeded, giving up");
                        packetTransmissionFailed = true;
                        break;
                    }
                }
            }

            // send final packet with end-of-file message
            byte[] eofMsg = "END_OF_FILE".getBytes();
            DatagramPacket eofPacket = Protocol.createResponsePacket(eofMsg, socket, receivePacket, seqNum);
            socket.send(eofPacket);
            System.out.println("\u001B[32mFile upload successful\u001B[0m");

            Protocol.receiveAck(socket, receivePacket, seqNum);
            byte[] ackData = receivePacket.getData();


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void downloadFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in, DatagramPacket receivePacket, int seqNum) throws IOException {

        showList(socket, serverAddress, receivePacket, seqNum);

        System.out.println("Please enter a file that is on the list");
        // send download request to server
        System.out.print("Filename: ");
        String fileName = in.readLine();
        File file = new File(fileName);

        byte[] header = Protocol.createHeader(0, 0);
        String message = "download " + file.getName();
        commandRequestToServer(socket, serverAddress, header, message);

        // receive the response from the server
        Protocol.receiveAck(socket, receivePacket, seqNum);
        // print the contents of the ACK packet
        byte[] ackData = receivePacket.getData();

        byte[] receiveData = Protocol.receiveData(socket, header.length);
        String response = new String(receiveData);


        // split the response into parts using whitespace as the delimiter
        String[] parts = response.trim().split("\\s+");

        // select the third element (index 2) from the parts array
        String amountPackages = parts[5];
        String hashFromServer = parts[6];

//        Protocol.receiveAck(socket,receivePacket,seqNum);
//        // print the contents of the ACK packet
//        ackData = receivePacket.getData();
//        System.out.println("ACK data: " + new String(ackData, 0, receivePacket.getLength()));


        // create a File object to represent the downloaded file
        file = new File(pathToDirectory + fileName);

        // create a buffer to hold the incoming data
        byte[] buffer = new byte[BUFFER_SIZE + 8];
        int numPacketsReceived = 0;

        // create a FileOutputStream to write the data to a file
        FileOutputStream fileOutputStream = new FileOutputStream(file);


        System.out.println("start downloading....");
        while (numPacketsReceived < Integer.parseInt(amountPackages) + 1) {
            // create a DatagramPacket to receive the packet from the client
            DatagramPacket filePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(filePacket);


            String packetData = new String(filePacket.getData(), 0, filePacket.getLength());
            if (packetData.contains("END_OF_FILE")) {
                // send an ACK to the client
                int packetSeqNum = Protocol.getSeqNum(filePacket.getData());
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
            System.out.println("\u001B[32mThis file is downloaded and safe to open, hashes match\u001B[0m");
        } else {
            System.err.println("This file could be corrupted");
        }

        fileOutputStream.close();

    }


    private static void removeFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in, DatagramPacket receivePacket, int seqNum) {
        showList(socket, serverAddress, receivePacket, seqNum);
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
                // Receive ack from server with a timeout of 5 seconds
                try {
                    Protocol.receiveAck(socket, receivePacket, seqNum);
                    ackReceived = true; // set the flag to true if the ack is received
                } catch (SocketTimeoutException e) {
                    numRetries++; // increment the retry count if the timeout occurs
                    System.out.println("Timeout occurred, retrying...");
                }
            }

            if (!ackReceived) {
                System.out.println("remove req failed after " + maxRetries + " attempts");
                System.out.println("Returning to main menu, please try again...");
                return; // exit the method and return to the main menu
            }
            // Receive response from server
            byte[] receiveData = Protocol.receiveData(socket, header.length);
            String response = new String(receiveData);
            System.out.println("\u001B[32m" + response.trim() + "\u001B[0m");

        } catch (IOException e) {
            System.out.println("Error occurred while trying to remove file from server: " + e.getMessage());
        }
    }

    private static void commandRequestToServer(DatagramSocket socket, InetAddress serverAddress, byte[] header, String message) throws IOException {
        byte[] commandBuffer = message.getBytes();
        byte[] command = new byte[header.length + commandBuffer.length];
        System.arraycopy(header, 0, command, 0, header.length);
        System.arraycopy(commandBuffer, 0, command, header.length, commandBuffer.length);
        DatagramPacket commandPacket = new DatagramPacket(command, command.length, serverAddress, PORT);
        socket.send(commandPacket);
    }


    private static void replaceFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in, DatagramPacket receivePacket, int seqNum) throws IOException {
        showList(socket, serverAddress, receivePacket, seqNum);
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
                    System.out.println("replace ack packet received from server");
                    ackReceived = true;
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
            } else {
                System.out.println("Invalid option. Returning to main menu");
                return;
            }

            uploadFile(socket, serverAddress, in, receivePacket);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private static void showList(DatagramSocket socket, InetAddress serverAddress, DatagramPacket receivePacket, int seqNum) {
        boolean ackReceived = false;
        int maxTries = 3; // set maximum number of tries to send the list request
        int tries = 0; // initialize the number of tries
        while (!ackReceived && tries < maxTries) {
            try {

                byte[] header = Protocol.createHeader(1, 0);
                String message = "list";
                commandRequestToServer(socket, serverAddress, header, message);

                byte[] responseData = receivePacket.getData();

                ackReceived = true;
            } catch (IOException e) {
                System.out.println("Timeout waiting for acknowledgement. Retrying...");
                tries++;
            }
        }
        // Receive list from server
        try {
            byte[] listBufferResponse = new byte[1024 + 8];
            DatagramPacket listPacketResponse = new DatagramPacket(listBufferResponse, listBufferResponse.length);
            socket.receive(listPacketResponse);
            // Send acknowledgment back to the server
            Protocol.sendAck(socket, receivePacket, seqNum);

            // Extract and print file list
            byte[] data = listPacketResponse.getData();
            byte[] responseData = Arrays.copyOfRange(data, HEADER_SIZE, data.length);
            String responseList = new String(responseData, 0, listPacketResponse.getLength() - HEADER_SIZE);

            System.out.println(responseList);
        } catch (Exception e) {
            System.out.println("A problem occurred while communicating with server, please try again or stop the program");
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
                // receive acknowledgement from server
                byte[] receiveBuffer = new byte[HEADER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(receivePacket);
                int seqNum = Protocol.getSeqNum(receivePacket.getData());
                int ackNum = Protocol.getAckNum(receivePacket.getData());
                ackReceived = true;
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

    private static void printMenu() {
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
