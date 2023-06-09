package com.nedap.university.server;

import com.nedap.university.Protocol;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.util.Arrays;


public class Server {
    private static final int HEADER_SIZE = 8;
    private static final int PAYLOAD_SIZE = 1024;
    // Set maximum number of retransmissions
    private static final int MAX_RETRANSMITS = 100;
    // Set timeout value to 1 seconds
    private static int RETRANSMIT_TIMEOUT = 1000;

    private static final int BUFFER_SIZE = PAYLOAD_SIZE + HEADER_SIZE;
    private static final String pathToDirectory = "";
//


    public static void start() {
        try {
            // Create a DatagramSocket that listens on port 9090
            DatagramSocket socket = new DatagramSocket(9090);
            // Create a buffer to receive packets
            byte[] receiveBuffer = new byte[BUFFER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

            while (true) {
                // receive a packet from a client
                socket.receive(receivePacket);

                byte[] receivedData = receivePacket.getData();
                int seqNum = Protocol.getSeqNum(receivedData);
                int ackNum = Protocol.getAckNum(receivedData);

                // extract the data from the packet and split it into an array of strings
                byte[] data = new byte[receivePacket.getLength() - HEADER_SIZE];
                System.arraycopy(receivedData, HEADER_SIZE, data, 0, data.length);
                String message = new String(data);
                String[] messageArray = message.split(" ");
                // perform different actions based on the first string in the array
                switch (messageArray[0]) {
                    case "Hello":
                        sendWelcomeMessage(socket, receivePacket, seqNum, ackNum);
                        break;
                    case "upload":
                        uploadFileToServer(socket, receivePacket, messageArray, seqNum);
                        break;
                    case "download":
                        downloadFromServer(socket, receivePacket, messageArray);
                        break;
                    case "remove":
                        removeFileOnServer(socket, receivePacket, messageArray, seqNum);
                        break;
                    case "replace":
                        replaceFileOnServer(socket, receivePacket, messageArray, seqNum);
                        break;
                    case "list":
                        listAllFilesOnServer(socket, receivePacket, seqNum);
                        break;
                    default:
                        System.out.println("Something went wrong..");
                        break;
                }
            }
        } catch (Exception e) {
            System.out.println("Overhere?");
            e.printStackTrace();
        }

        System.out.println("Stopped");
    }

    private static void sendWelcomeMessage(DatagramSocket socket, DatagramPacket receivePacket, int seqNum, int ackNum) throws IOException {
        DatagramPacket responsePacket;
        System.out.println("Hello message received from " + receivePacket.getAddress());

        try {
            // send a welcome message
            System.out.println("Welcome message sent to client");
            responsePacket = Protocol.createResponsePacket("Welcome, You have successfully connected to the server.", socket, receivePacket, 1);
            socket.send(responsePacket);

            // Send an acknowledgement
            Protocol.sendAck(socket, receivePacket, seqNum);
        } catch (IOException e) {
            // Handle the exception
            System.err.println("Error sending welcome message: " + e.getMessage());
        }
    }

    public static void uploadFileToServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray, int seqNum) throws IOException {
        // log that the remove request has been received
        System.out.println("Received upload request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
        Protocol.sendAck(socket, receivePacket, seqNum);
        System.out.println("Filepath to upload: " + messageArray[1]);

        String fileNameToUpload = messageArray[1];
        File fileToUpload = new File(pathToDirectory, fileNameToUpload);
        System.out.println("file to upload: " + fileToUpload);
        String amountPackages = messageArray[2] + 1;
        String hashFromClient = messageArray[3];

        // create a buffer to hold the incoming data
        byte[] buffer = new byte[BUFFER_SIZE];
        int numPacketsReceived = 0;

        // create a FileOutputStream to write the data to a file
        FileOutputStream fileOutputStream = new FileOutputStream(fileToUpload);

        socket.receive(receivePacket);

        while (numPacketsReceived < Integer.parseInt(amountPackages)) {

            // create a DatagramPacket to receive the packet from the client
            DatagramPacket filePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(filePacket);
            // check if the packet contains the end-of-file message
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

        String filePath = fileToUpload.getPath(); // get the path of the file
        File uploadedFile = new File(filePath);

        boolean hashesMatch = Protocol.checkFileHash(uploadedFile, hashFromClient);
        if (hashesMatch) {
            System.out.println("\u001B[32mThis file is safe to store, hashes match\u001B[0m");
        } else {
            System.err.println("This file could be corrupted");
        }

        System.out.println("\u001B[32mFile upload successful\u001B[0m");

        fileOutputStream.close();

    }

    private static void downloadFromServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray) throws IOException, InterruptedException {
        DatagramPacket responsePacket;
        System.out.println("Received download request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());

        int seqNum = 0;
        Protocol.sendAck(socket, receivePacket, seqNum);
        String fileName = messageArray[1];
        File file = new File(pathToDirectory + fileName);

        if (!file.exists()) {
            // send a response to the client indicating that the file does not exist
            String message = "File does not exist";
            responsePacket = Protocol.createResponsePacket(message, socket, receivePacket, 1);
            socket.send(responsePacket);
        } else {
            // read the file into a byte array
            byte[] fileData = Files.readAllBytes(file.toPath());
            String expectedHash = Protocol.getHash(fileData);

            // set the maximum size of each packet to 1024 bytes
            int maxPacketSize = 1024;
            int numPackets = (int) Math.ceil(fileData.length / (double) maxPacketSize);

            // send a response to the client indicating that the server is ready to send the file
            String message = "Ready to send file " + fileName + " " + numPackets + " " + expectedHash;
            responsePacket = Protocol.createResponsePacket(message, socket, receivePacket, seqNum);
            socket.send(responsePacket);
            seqNum++;

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
                    int packetSeqNum = Protocol.getSeqNum(packet.getData());
                    // wait for the ack packet with the expected sequence number
                    try {
                        DatagramPacket ackPacket = Protocol.receiveAck(socket, receivePacket, seqNum);
                        int receivedSeqNum = Protocol.getSeqNum(ackPacket.getData());

                        if (receivedSeqNum == seqNum) {
                            receivedExpectedSeqNum = true;
                            seqNum++;
                            retransmitCounter = 0; // reset the retransmit counter
                        }
                    } catch (SocketTimeoutException e) {
                        // resend the packet if timeout occurs
                        System.out.println("Timeout occurred, resending packet");
                        retransmitCounter++; // increment the retransmit counter
                        if (retransmitCounter >= MAX_RETRANSMITS) {
                            // assume packet is lost and retransmit
                            System.out.println("Maximum retransmits exceeded, retransmitting packet");
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
        }
        System.out.println("\u001B[32mDownload complete\u001B[0m");
    }

    private static void removeFileOnServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray, int seqNum) throws IOException {
        DatagramPacket responsePacket;
        byte[] responseBuffer;
        // log that the remove request has been received
        System.out.println("Received remove request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
        Protocol.sendAck(socket, receivePacket, seqNum);
        System.out.println("File to remove: " + messageArray[1]);

        // remove file from server
        String fileNameToRemove = messageArray[1];
        File fileToRemove = new File(pathToDirectory, fileNameToRemove);
        System.out.println("file to remove: " + fileToRemove);
        if (fileToRemove.exists()) {
            try {
                Files.delete(fileToRemove.toPath());
                System.out.println("\u001B[32mFile removed successfully\u001B[0m");
                // send a response to the client indicating the remove was successful
                responsePacket = Protocol.createResponsePacket("File removed successfully", socket, receivePacket, 1);
                socket.send(responsePacket);
            } catch (IOException e) {
                // send a response to the client indicating the remove failed due to an IO error
                responsePacket = Protocol.createResponsePacket("Failed to remove file due to IO error", socket, receivePacket, 1);
                socket.send(responsePacket);
            }
        } else {
            // send a response to the client indicating the file was not found on the server
            responsePacket = Protocol.createResponsePacket("File not found on server", socket, receivePacket, 1);
            try {
                socket.send(responsePacket);
            } catch (IOException e) {
                System.err.println("Error sending response packet: " + e.getMessage());
            }
        }
    }

    private static void listAllFilesOnServer(DatagramSocket socket, DatagramPacket receivePacket, int seqNum) throws IOException {
        System.out.println("list message received from " + receivePacket.getAddress());
        DatagramPacket responsePacket;
        // Create a new File object representing the directory we want to list
        File directory = new File(pathToDirectory);
        // Get a list of files in the directory as an array of strings
        String[] fileList = directory.list();

        if (fileList != null) {
            boolean ackReceived = false;
            int maxTries = 3; // maximum number of times to try to send the packet
            int tries = 0; // initialize the number of tries

            while (!ackReceived && tries < maxTries) {
                try {
                    //Create the response message containing the file list
                    String fileString = String.join("\n", fileList);
                    String responseMessage = "Here are the files in the directory:\n" + fileString;
                    responsePacket = Protocol.createResponsePacket(responseMessage, socket, receivePacket, 1);
                    socket.send(responsePacket);
                    System.out.println("List sent");
                    byte[] responseData = responsePacket.getData();

                    // wait for acknowledgement from client
                    DatagramPacket ackPacket = Protocol.receiveAck(socket, receivePacket, seqNum);
                    seqNum = Protocol.getSeqNum(ackPacket.getData());

                    if (seqNum == 0) {
                        ackReceived = true;
                    } else {
                        System.out.println("Invalid acknowledgement received.");
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout waiting for acknowledgement. Retrying...");
                    tries++;
                    // continue the while loop to retry
                } catch (IOException e) {
                    System.out.println("IOException occurred while communicating with client: " + e.getMessage());
                    break;
                }
            }
            // If we have reached the maximum number of attempts without receiving an acknowledgement, display an error message
            if (tries == maxTries) {
                System.out.println("Failed to receive acknowledgement after " + maxTries + " attempts.");

            }
        }
    }
    private static void replaceFileOnServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray, int seqNum) throws IOException {
        DatagramPacket responsePacket;
        System.out.println("Received replace request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());

        Protocol.sendAck(socket, receivePacket, seqNum);
        String fileName = messageArray[1];
        File file = new File(pathToDirectory + fileName);

        if (!file.exists()) {
            // send a response to the client indicating that the file does not exist
            String message = "File does not exist";
            responsePacket = Protocol.createResponsePacket(message, socket, receivePacket, 1);
            socket.send(responsePacket);
        } else {
            // send a response to the client indicating that the file does not exist
            String message = "Are you sure you want to replace " + fileName + " ?";
            responsePacket = Protocol.createResponsePacket(message, socket, receivePacket, 1);
            socket.send(responsePacket);
        }

        byte[] buffer = new byte[BUFFER_SIZE + 8];
        socket.receive(receivePacket);

        DatagramPacket filePacket = new DatagramPacket(buffer, buffer.length);
        socket.receive(filePacket);

        String replacePacket = new String(filePacket.getData(), 0, filePacket.getLength());
        if (replacePacket.contains("YES_DO_A_REPLACE")) {
            file.delete();
            System.out.println(fileName + " file deleted");
        }

    }

}
