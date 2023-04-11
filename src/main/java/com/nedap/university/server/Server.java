package com.nedap.university.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Server {

    private boolean keepAlive = true;

    public void start() {
        try {
            // create a DatagramSocket that listens on port 9090
            DatagramSocket socket = new DatagramSocket(9090);

            // create a buffer to receive packets
            byte[] receiveBuffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

            while (true) {
                // receive a packet from a client
                socket.receive(receivePacket);
                // extract the data from the packet and split it into an array of strings
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                String[] messageArray = message.split(" ");

                // perform different actions based on the first string in the array
                switch (messageArray[0]) {
                    case "Hello":
                        System.out.println("Hello message received from " + receivePacket.getAddress());

                        // send a response with available options
                        String options = "What would you like to do?\n1. Upload\n2. List available files";
                        byte[] sendBuffer = options.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
                        socket.send(sendPacket);
                        break;
                    case "upload":
                        uploadFileToServer(socket, receivePacket, messageArray);
                        break;
                    case "download":
                        downloadFromServer(socket, receivePacket, messageArray);
                        break;
                    case "remove":
                        removeFileOnServer(socket, receivePacket, messageArray);
                        break;
                    case "replace":
                        replaceFileOnServer(socket, receivePacket, messageArray);
                        break;
                    case "list":
                        listAllFilesOnServer(socket, receivePacket);
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

    private static void uploadFileToServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray) throws IOException {
        if (messageArray.length < 2) {
            // log an error and send an error response to the client
            System.err.println("Received invalid upload request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
            String errorResponse = "Invalid upload request";
            byte[] responseBuffer = errorResponse.getBytes();
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
            socket.send(responsePacket);
            return;
        }

        // log that the upload request has been received
        System.out.println("Received upload request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
        System.out.println(messageArray[1]);

        // receive file from client and store in a folder
        String fileName = messageArray[1];
        File file = new File("/home/pi/data/" + fileName);
        if (file.exists()) {
            // send a response to the client indicating that the file already exists
            String errorResponse = "File already exists";
            byte[] responseBuffer = errorResponse.getBytes();
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
            socket.send(responsePacket);
        } else {
            // send a response to the client indicating that the server is ready to receive the file
            String uploadResponse = "Ready to receive file";
            byte[] responseBuffer = uploadResponse.getBytes();
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
            socket.send(responsePacket);
        }


        FileOutputStream fileOutputStream = new FileOutputStream(file);
        // create a buffer to hold incoming data packets and a new DatagramPacket to receive data packets from the socket
        byte[] buffer = new byte[1024];
        DatagramPacket dataPacket = new DatagramPacket(buffer, buffer.length);

// initialize sequence number and expected sequence number
        int sequenceNumber = 0;
        int expectedSequenceNumber = 0;
        int numSeqThreeReceived = 0; // counter for number of times sequence number 3 is received

        List<Integer> sequenceNumbers = new ArrayList<>();
        while (true) {
            // receive a data packet
            socket.receive(dataPacket);

            // if the data packet contains the end of file marker, break out of the loop
            if (new String(dataPacket.getData(), 0, dataPacket.getLength()).equals("end")) {
                break;
            }

            // extract the sequence number from the header of the packet
            sequenceNumber = Integer.parseInt(new String(dataPacket.getData(), 0, dataPacket.getLength()));
            sequenceNumbers.add(sequenceNumber);
//            // check if the sequence number is the expected value
//            if (sequenceNumber == expectedSequenceNumber) {
//                // send an ack to the client with the sequence number in the header
//                byte[] ackData = String.valueOf(sequenceNumber).getBytes();
//                DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, receivePacket.getAddress(), receivePacket.getPort());
//                socket.send(ackPacket);
//                System.out.println("Ack sent to client");
//                // increment the expected sequence number
//                expectedSequenceNumber++;
//            }
            //FIXME: test ack not received

            // check if the sequence number is the expected value
            if (sequenceNumber == expectedSequenceNumber) {
                // send an ack to the client with the sequence number in the header
                if (sequenceNumber == 3) { // if sequence number is 3, only send ACK on the third occurrence
                    numSeqThreeReceived++;
                    System.out.println("new packet with 3 received");
                    if (numSeqThreeReceived == 3) {
                        byte[] ackData = String.valueOf(sequenceNumber).getBytes();
                        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, receivePacket.getAddress(), receivePacket.getPort());
                        socket.send(ackPacket);
                        System.out.println("Ack sent to client");
                        numSeqThreeReceived = 0; // reset counter after sending ACK
                    } else {
                        System.out.println("Received sequence number 3, but no ACK will be sent.");
                    }
                } else { // for all other sequence numbers, send ACK as usual
                    byte[] ackData = String.valueOf(sequenceNumber).getBytes();
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, receivePacket.getAddress(), receivePacket.getPort());
                    socket.send(ackPacket);
                    System.out.println("Ack sent to client");
                }
                // increment the expected sequence number
                expectedSequenceNumber++;
            }
                // increment the expected sequence number


            // write the entire contents of the data packet to the output file starting
            // from the beginning of the byte array.
            fileOutputStream.write(dataPacket.getData(), 0, dataPacket.getLength());
            fileOutputStream.flush();
        }
        fileOutputStream.close();
        System.out.println("Sequence numbers:");
        for (int seqNum : sequenceNumbers) {
            System.out.println("Sequence number:" + seqNum);
        }
        // send a response to the client indicating the upload was successful
        String uploadResponse = "File uploaded successfully";
        byte[] responseBuffer = uploadResponse.getBytes();
        DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
        socket.send(responsePacket);
    }
    private static void downloadFromServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray) throws IOException {
        if (messageArray.length < 2) {
            // log an error and send an error response to the client
            System.err.println("Received invalid download request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
            String errorResponse = "Invalid download request";
            byte[] responseBuffer = errorResponse.getBytes();
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
            socket.send(responsePacket);
            return;
        }

        // log that the download request has been received
        System.out.println("Received download request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
        String fileName = messageArray[1];
        File file = new File("/home/pi/data/" + fileName);

        if (!file.exists()) {
            // send a response to the client indicating that the file does not exist
            String errorResponse = "File does not exist";
            byte[] responseBuffer = errorResponse.getBytes();
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
            socket.send(responsePacket);
        } else {
            // send a response to the client indicating that the server is ready to send the file
            String uploadResponse = "Ready to send file " + fileName;
            System.out.println("ready to send file " + fileName);
            byte[] responseBuffer = uploadResponse.getBytes();
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
            socket.send(responsePacket);

            // open the file and read it in chunks of 1024 bytes
            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] fileBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(fileBuffer)) > 0) {
                byte[] chunkBuffer = Arrays.copyOfRange(fileBuffer, 0, bytesRead);
                DatagramPacket filePacket = new DatagramPacket(chunkBuffer, bytesRead, receivePacket.getAddress(), receivePacket.getPort());
                socket.send(filePacket);
            }

            System.out.println("File sent successfully");

            // send "end" message to client
            byte[] endBuffer = "end".getBytes();
            DatagramPacket endPacket = new DatagramPacket(endBuffer, endBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
            socket.send(endPacket);

            fileInputStream.close();
        }
    }
    private static void removeFileOnServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray) throws IOException {
        DatagramPacket responsePacket;
        byte[] responseBuffer;
        if (messageArray.length < 2) {
            // log an error and send an error response to the client
            System.err.println("Received invalid remove request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
            String errorResponse = "Invalid remove request";
            responseBuffer = errorResponse.getBytes();
            responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
            socket.send(responsePacket);
            return;
        }

        // log that the remove request has been received
        System.out.println("Received remove request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
        System.out.println(messageArray[1]);

        // remove file from server
        String fileNameToRemove = messageArray[1];
        File fileToRemove = new File("home/pi/data", fileNameToRemove);
        if (fileToRemove.exists()) {
            FileInputStream fileInputStream = null;
            try {
                fileInputStream = new FileInputStream(fileToRemove);
                fileInputStream.close();
                if (fileToRemove.delete()) {
                    // send a response to the client indicating the remove was successful
                    String removeResponse = "File removed successfully";
                    responseBuffer = removeResponse.getBytes();
                    responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
                    socket.send(responsePacket);
                } else {
                    // send a response to the client indicating the remove failed
                    String errorResponse = "Failed to remove file";
                    responseBuffer = errorResponse.getBytes();
                    responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
                    socket.send(responsePacket);
                }
            } catch (IOException e) {
                // send a response to the client indicating the remove failed due to an IO error
                String errorResponse = "Failed to remove file due to IO error";
                responseBuffer = errorResponse.getBytes();
                responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
                socket.send(responsePacket);
            } finally {
                try {
                    if (fileInputStream != null) {
                        fileInputStream.close();
                    }
                } catch (IOException e) {
                    // log the error but don't send a response to the client
                    System.err.println("Failed to close file input stream: " + e.getMessage());
                }
            }
        } else {
            // send a response to the client indicating the file does not exist
            String errorResponse = "File does not exist";
            responseBuffer = errorResponse.getBytes();
            responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
            socket.send(responsePacket);
        }
    }

    private static void replaceFileOnServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray) throws IOException {
        if (messageArray.length < 2) {
            // log an error and send an error response to the client
            System.err.println("Received invalid replace request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
            String errorResponse = "Invalid replace request";
            byte[] responseBuffer = errorResponse.getBytes();
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
            socket.send(responsePacket);
            return;
        }

        // log that the replace request has been received
        System.out.println("Received replace request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
        String fileName = messageArray[1];
        File file = new File("/home/pi/data/" + fileName);

        if (!file.exists()) {
            // send a response to the client indicating that the file does not exist
            String errorResponse = "File does not exist";
            byte[] responseBuffer = errorResponse.getBytes();
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
            socket.send(responsePacket);
            return;
        }

        // send a response to the client indicating that the server is ready to receive the new file contents
        String replaceResponse = "Ready to receive new file contents";
        byte[] responseBuffer = replaceResponse.getBytes();
        DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
        socket.send(responsePacket);

        // receive new file contents from the client and replace the contents of the existing file
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        byte[] buffer = new byte[1024];
        DatagramPacket dataPacket = new DatagramPacket(buffer, buffer.length);
        while (true) {
            socket.receive(dataPacket);

            // if the data packet contains the end of file marker, break out of the loop
            if (new String(dataPacket.getData(), 0, dataPacket.getLength()).equals("end")) {
                break;
            }

            // write the entire contents of the data packet to the output file starting
            // from the beginning of the byte array.
            fileOutputStream.write(dataPacket.getData(), 0, dataPacket.getLength());
            fileOutputStream.flush();
        }
        fileOutputStream.close();

        // send a response to the client indicating the replace was successful
        String replaceSuccess = "File replaced successfully";
        byte[] successBuffer = replaceSuccess.getBytes();
        DatagramPacket successPacket = new DatagramPacket(successBuffer, successBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
        socket.send(successPacket);
    }

    private static void listAllFilesOnServer(DatagramSocket socket, DatagramPacket receivePacket) throws IOException {
        // Create a new File object representing the directory we want to list
        File directory = new File("/home/pi/data");

        // Get a list of files in the directory as an array of strings
        String[] fileList = directory.list();

        if (fileList != null) {
            System.out.println("Listing files on server");
            // Concatenate all the filenames into a single string with newline characters separating them
            String fileString = String.join("\n", fileList);

            byte[] fileBytes = fileString.getBytes();
            DatagramPacket filePacket = new DatagramPacket(fileBytes, fileBytes.length, receivePacket.getAddress(), receivePacket.getPort());
            socket.send(filePacket);
        }
    }

    public void stop() {
        keepAlive = false;
    }
}
