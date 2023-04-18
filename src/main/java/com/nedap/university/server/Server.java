package com.nedap.university.server;

import com.nedap.university.Protocol;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;


public class Server {

    private static final int HEADER_SIZE = 8;
    private static final int PAYLOAD_SIZE = 1024;
    private static final int BUFFER_SIZE = PAYLOAD_SIZE + HEADER_SIZE;
    //pi: "home/pi/data"
    private static final String pathToDirectory = "/Users/anniek.bisschop/Networking/network-systems/src/main/java/com/nedap/university/data/";




    public static void start() {
        try {
            // create a DatagramSocket that listens on port 9090
            DatagramSocket socket = new DatagramSocket(9090);

            // create a buffer to receive packets
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
                System.out.println("message for switch: " + message);
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
//                        replaceFileOnServer(socket, receivePacket, messageArray, seqNum);
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
            e.printStackTrace();
        }

        System.out.println("Stopped");
    }

    private static void sendWelcomeMessage(DatagramSocket socket, DatagramPacket receivePacket, int seqNum, int ackNum) throws IOException {
        DatagramPacket responsePacket;
        System.out.println("Hello message received from " + receivePacket.getAddress());
        // Send an acknowledgement
        Protocol.sendAck(socket, receivePacket, seqNum);
        // send a response with available options
        System.out.println("Menu options sent to client");
        responsePacket = Protocol.createResponsePacket("Welcome, You have successfully connected to the server.", socket, receivePacket, 1);
        socket.send(responsePacket);
    }

    public static void uploadFileToServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray, int seqNum) throws IOException {
        // log that the remove request has been received
        System.out.println("Received upload request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
        Protocol.sendAck(socket, receivePacket, seqNum);
        System.out.println("File to upload: " + messageArray[1]);

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
        System.out.println("start receiving packets....");
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
        // create a new File object based on the path
        File uploadedFile = new File(filePath);

        // compare the hashes of the two files
        byte[] fileData = Files.readAllBytes(uploadedFile.toPath());
        String expectedHash = Protocol.getHash(fileData);
        boolean hashesMatch = hashFromClient.equals(expectedHash);
        System.out.println("hashes match:" + hashesMatch);
        System.out.println("File upload successful");
        fileOutputStream.close();

    }
    private static void downloadFromServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray) throws IOException {
        DatagramPacket responsePacket;
        System.out.println("Received download request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());

        int seqNum = 0;
        Protocol.sendAck(socket, receivePacket, seqNum + 1);
        System.out.println("ACK sent for download req");
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
            // calculate total number of packets to be sent
            int numPackets = (int) Math.ceil(fileData.length / (double) PAYLOAD_SIZE);
            System.out.println("number of Packets is: " + numPackets);

            // send a response to the client indicating that the server is ready to send the file
            String message = "Ready to send file " + fileName + " " + numPackets + " " + expectedHash;
            System.out.println("ready to send file " + fileName + " numPackets:" + numPackets);
            responsePacket = Protocol.createResponsePacket(message, socket, receivePacket, 1);
            socket.send(responsePacket);
        }


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
                System.out.println("file removed successfully");
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
            socket.send(responsePacket);
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
            System.out.println("Listing files on server");

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
                    System.out.println("list sent to client");
                    // wait for acknowledgement from client
                    socket.setSoTimeout(2000); // set the timeout to 5 seconds waiting for client answer

                    DatagramPacket ackPacket = Protocol.receiveAck(socket, receivePacket, seqNum);
                    seqNum = Protocol.getSeqNum(ackPacket.getData());
                    System.out.println("acknowledgement for list received seqnum =" + seqNum );

                    if (seqNum == 1) {
                        System.out.println("Acknowledgement received.");
                        ackReceived = true;
                        socket.setSoTimeout(0);
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

    //
//    private static void replaceFileOnServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray, int seqNum) throws IOException {
//        if (messageArray.length < 2) {
//            // log an error and send an error response to the client
//            System.err.println("Received invalid replace request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
//            String errorResponse = "Invalid replace request";
//            byte[] responseBuffer = errorResponse.getBytes();
//            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
//            socket.send(responsePacket);
//            return;
//        }
//
//        // log that the replace request has been received
//        System.out.println("Received replace request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
//        String fileName = messageArray[1];
//        File file = new File("/home/pi/data/" + fileName);
//
//        if (!file.exists()) {
//            // send a response to the client indicating that the file does not exist
//            String errorResponse = "File does not exist";
//            byte[] responseBuffer = errorResponse.getBytes();
//            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
//            socket.send(responsePacket);
//            return;
//        }
//
//        // send a response to the client indicating that the server is ready to receive the new file contents
//        String replaceResponse = "Ready to receive new file contents";
//        byte[] responseBuffer = replaceResponse.getBytes();
//        DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
//        socket.send(responsePacket);
//
//        // receive new file contents from the client and replace the contents of the existing file
//        FileOutputStream fileOutputStream = new FileOutputStream(file);
//        byte[] buffer = new byte[1024];
//        DatagramPacket dataPacket = new DatagramPacket(buffer, buffer.length);
//        while (true) {
//            socket.receive(dataPacket);
//
//            // if the data packet contains the end of file marker, break out of the loop
//            if (new String(dataPacket.getData(), 0, dataPacket.getLength()).equals("end")) {
//                break;
//            }
//
//            // write the entire contents of the data packet to the output file starting
//            // from the beginning of the byte array.
//            fileOutputStream.write(dataPacket.getData(), 0, dataPacket.getLength());
//            fileOutputStream.flush();
//        }
//        fileOutputStream.close();
//
//        // send a response to the client indicating the replace was successful
//        String replaceSuccess = "File replaced successfully";
//        byte[] successBuffer = replaceSuccess.getBytes();
//        DatagramPacket successPacket = new DatagramPacket(successBuffer, successBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
//        socket.send(successPacket);
//    }

}
