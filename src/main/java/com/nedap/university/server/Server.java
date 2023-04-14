package com.nedap.university.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Server {

    private static final int BUFFER_SIZE = 1024;
    private static final int HEADER_SIZE = 8; // 4 bytes for seq, 4 bytes for ack
    private static final byte[] ACK = {0, 1};

    //pi: "home/pi/data"
    private static final String pathToDirectory = "/Users/anniek.bisschop/Networking/network-systems/src/main/java/com/nedap/university/data/";
    private static final int PAYLOAD_SIZE = 1024;


    public void start() {
        try {
            // create a DatagramSocket that listens on port 9090
            DatagramSocket socket = new DatagramSocket(9090);

            // create a buffer to receive packets
            byte[] receiveBuffer = new byte[BUFFER_SIZE + HEADER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

            while (true) {
                // receive a packet from a client
                socket.receive(receivePacket);

                byte[] receivedData = receivePacket.getData();

                // Extract the sequence number from the packet header
                int seqNum = (receivedData[0] << 24) & 0xFF000000 |
                        (receivedData[1] << 16) & 0x00FF0000 |
                        (receivedData[2] << 8) & 0x0000FF00 |
                        (receivedData[3] << 0) & 0x000000FF;
                System.out.println("Seq num " + seqNum);
                // Extract the acknowledgement number from the packet header
                int ackNum = (receivedData[4] << 24) & 0xFF000000 |
                        (receivedData[5] << 16) & 0x00FF0000 |
                        (receivedData[6] << 8) & 0x0000FF00 |
                        (receivedData[7] << 0) & 0x000000FF;


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
//                        downloadFromServer(socket, receivePacket, messageArray, seqNum);
                        break;
                    case "remove":
                        removeFileOnServer(socket, receivePacket, messageArray, seqNum);
                        break;
                    case "replace":
//                        replaceFileOnServer(socket, receivePacket, messageArray, seqNum);
                        break;
                    case "list":
                        System.out.println("in list case");
                        listAllFilesOnServer(socket, receivePacket, seqNum);
                        System.out.println("function finished");
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
        sendServerAck(socket,receivePacket,seqNum);
        // send a response with available options
        System.out.println("Menu options sent to client");
        responsePacket = createResponsePacket("Welcome, You have successfully connected to the server.", socket, receivePacket, 1);
        socket.send(responsePacket);
    }

    /**
     * This method takes in two integer parameters, seqNum and ackNum, and returns a byte array of size HEADER_SIZE,
     * which is a constant set to 8.
     * Create a header for a packet that includes the sequence number and acknowledgement number,
     * which are both 4 bytes long.
     * */
    private static byte[] createHeader(int seqNum, int ackNum) {
        byte[] header = new byte[HEADER_SIZE];
        header[0] = (byte) ((seqNum >> 24) & 0xFF);
        header[1] = (byte) ((seqNum >> 16) & 0xFF);
        header[2] = (byte) ((seqNum >> 8) & 0xFF);
        header[3] = (byte) ((seqNum) & 0xFF);
        header[4] = (byte) ((ackNum >> 24) & 0xFF);
        header[5] = (byte) ((ackNum >> 16) & 0xFF);
        header[6] = (byte) ((ackNum >> 8) & 0xFF);
        header[7] = (byte) ((ackNum) & 0xFF);
        return header;
    }
    private static DatagramPacket createResponsePacket(String message, DatagramSocket socket, DatagramPacket receivePacket, int seqNum) {
        byte[] header = createHeader(seqNum, 0);
        byte[] responseBuffer = message.getBytes();
        byte[] response = new byte[header.length + responseBuffer.length];
        System.arraycopy(header, 0, response, 0, header.length);
        System.arraycopy(responseBuffer, 0, response, header.length, responseBuffer.length);
        return new DatagramPacket(response, response.length, receivePacket.getAddress(), receivePacket.getPort());
    }
    private static int getSeqNum(byte[] header) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(header);
        return byteBuffer.getInt();
    }

    private static int getAckNum(byte[] header) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(header);
        byteBuffer.getInt(); // Skip over seqNum
        return byteBuffer.getInt();
    }
    public static void uploadFileToServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray, int seqNum) throws IOException {
        // log that the remove request has been received
        System.out.println("Received upload request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
        sendServerAck(socket, receivePacket, seqNum);
        System.out.println("File to upload: " + messageArray[1]);

        // remove file from server
        String fileNameToUpload = messageArray[1];
        File fileToUpload = new File(pathToDirectory, fileNameToUpload);
        System.out.println("file to upload: " + fileToUpload);
        String amountPackages = messageArray[2];
        System.out.println("packages to receive: " + amountPackages);

        socket.receive(receivePacket);

        // create a buffer to hold the incoming data
        byte[] buffer = new byte[PAYLOAD_SIZE];
        int numPacketsReceived = 0;

// create a FileOutputStream to write the data to a file
        FileOutputStream fileOutputStream = new FileOutputStream(fileToUpload);

        while (numPacketsReceived < Integer.parseInt(amountPackages)) {
            // create a DatagramPacket to receive the packet from the client
            DatagramPacket filePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(filePacket);
            int packetSeqNum = getSeqNum(filePacket.getData());
            numPacketsReceived++;
            System.out.println("Packet received: " + numPacketsReceived + ", seqnum: " + packetSeqNum);
            // send an ACK to the client
            sendServerAck(socket, receivePacket, packetSeqNum);
            System.out.println("with seqNum " + packetSeqNum);

            // write the payload to the file (excluding the header)
            byte[] payload = new byte[filePacket.getLength() - HEADER_SIZE];
            System.arraycopy(filePacket.getData(), HEADER_SIZE, payload, 0, payload.length);
            int endIndex = payload.length - 1;
            while (endIndex >= 0 && payload[endIndex] == 0) { // find index of last non-zero byte
                endIndex--;
            }
            byte[] trimmedPayload = Arrays.copyOfRange(payload, 0, endIndex + 1); // copy only non-zero bytes
            fileOutputStream.write(trimmedPayload, 0, trimmedPayload.length);
            fileOutputStream.flush();
        }

// close the FileOutputStream and print a message
        System.out.println("File upload successful");
        fileOutputStream.close();
    }

    private static void sendServerAck(DatagramSocket socket, DatagramPacket receivePacket, int seqNum) throws IOException {
        // Send an acknowledgement
        byte[] ackHeader = createHeader(seqNum, seqNum + 1);
        DatagramPacket ackPacket = new DatagramPacket(ackHeader, ackHeader.length, receivePacket.getAddress(), receivePacket.getPort());
        socket.send(ackPacket);
        System.out.println("ackPacket sent");
    }
    private static void removeFileOnServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray, int seqNum) throws IOException {
        DatagramPacket responsePacket;
        byte[] responseBuffer;
        // log that the remove request has been received
        System.out.println("Received remove request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
        sendServerAck(socket, receivePacket,seqNum);
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
                responsePacket = createResponsePacket("File removed successfully", socket, receivePacket, 1);
                socket.send(responsePacket);
            } catch (IOException e) {
                // send a response to the client indicating the remove failed due to an IO error
                responsePacket = createResponsePacket("Failed to remove file due to IO error", socket, receivePacket, 1);
                socket.send(responsePacket);
            }
        } else {
            // send a response to the client indicating the file was not found on the server
            responsePacket = createResponsePacket("File not found on server", socket, receivePacket, 1);
            socket.send(responsePacket);
        }
    }
    private static void listAllFilesOnServer(DatagramSocket socket, DatagramPacket receivePacket, int seqNum) throws IOException {
        System.out.println("list message received from " + receivePacket.getAddress());

        // Create a new File object representing the directory we want to list
        File directory = new File(pathToDirectory);
        // Get a list of files in the directory as an array of strings
        String[] fileList = directory.list();

        if (fileList != null) {
            System.out.println("Listing files on server");

            boolean ackReceived = false;
            int maxTries = 3; // set maximum number of tries to send the hello packet
            int tries = 0; // initialize the number of tries

            while (!ackReceived && tries < maxTries) {
                try {
                    if (tries > 0) {
                        System.out.println("packet retransmitted...");
                    }

                    byte[] ackHeader = createHeader(seqNum + 1, seqNum);
                    DatagramPacket ackPacket = new DatagramPacket(ackHeader, HEADER_SIZE, receivePacket.getAddress(), receivePacket.getPort());
                    socket.send(ackPacket);

                    // Create the response message containing the file list
                    String fileString = String.join("\n", fileList);
                    String responseMessage = "Here are the files in the directory:\n" + fileString;
                    byte[] responseMessageBytes = responseMessage.getBytes();
                    byte[] responseHeader = createHeader(seqNum + 2, seqNum + 1);
                    byte[] responseData = new byte[HEADER_SIZE + responseMessageBytes.length];
                    System.arraycopy(responseHeader, 0, responseData, 0, HEADER_SIZE);
                    System.arraycopy(responseMessageBytes, 0, responseData, HEADER_SIZE, responseMessageBytes.length);
                    DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, receivePacket.getAddress(), receivePacket.getPort());
                    socket.send(responsePacket);
                    System.out.println("Packet length: " + responsePacket.getLength());
                    System.out.println("list send to client");

                    //wait for ack client
                    byte[] ackBuffer = new byte[HEADER_SIZE];
                    DatagramPacket ackReceivePacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                    socket.receive(ackReceivePacket);
                    System.out.println("acknowledgement for list received");
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
        }}



    //    private static void uploadFileToServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray, int seqNum) throws IOException {
//        if (messageArray.length < 2) {
//            // log an error and send an error response to the client
//            System.err.println("Received invalid upload request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
//            String errorResponse = "Invalid upload request";
//            byte[] responseBuffer = errorResponse.getBytes();
//            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
//            socket.send(responsePacket);
//            return;
//        }
//
//        // log that the upload request has been received
//        System.out.println("Received upload request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
//        System.out.println(messageArray[1]);
//
//        // receive file from client and store in a folder
//        String fileName = messageArray[1];
//        File file = new File("/home/pi/data/" + fileName);
//        if (file.exists()) {
//            // send a response to the client indicating that the file already exists
//            String errorResponse = "File already exists";
//            byte[] responseBuffer = errorResponse.getBytes();
//            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
//            socket.send(responsePacket);
//        } else {
//            // send a response to the client indicating that the server is ready to receive the file
//            String uploadResponse = "Ready to receive file";
//            byte[] responseBuffer = uploadResponse.getBytes();
//            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
//            socket.send(responsePacket);
//        }
//
//
//        FileOutputStream fileOutputStream = new FileOutputStream(file);
//        // create a buffer to hold incoming data packets and a new DatagramPacket to receive data packets from the socket
//        byte[] buffer = new byte[1024];
//        DatagramPacket dataPacket = new DatagramPacket(buffer, buffer.length);
//
//// initialize sequence number and expected sequence number
//        int sequenceNumber = 0;
//        int expectedSequenceNumber = 0;
//        int numSeqThreeReceived = 0; // counter for number of times sequence number 3 is received
//
//        List<Integer> sequenceNumbers = new ArrayList<>();
//        while (true) {
//            // receive a data packet
//            socket.receive(dataPacket);
//
//            // if the data packet contains the end of file marker, break out of the loop
//            if (new String(dataPacket.getData(), 0, dataPacket.getLength()).equals("end")) {
//                break;
//            }
//
//            // extract the sequence number from the header of the packet
//            sequenceNumber = Integer.parseInt(new String(dataPacket.getData(), 0, dataPacket.getLength()));
//            sequenceNumbers.add(sequenceNumber);
//           // check if the sequence number is the expected value
//            if (sequenceNumber == expectedSequenceNumber) {
//                // send an ack to the client with the sequence number in the header
//                byte[] ackData = String.valueOf(sequenceNumber).getBytes();
//                DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, receivePacket.getAddress(), receivePacket.getPort());
//                socket.send(ackPacket);
//                System.out.println("Ack sent to client");
//                // increment the expected sequence number
//                expectedSequenceNumber++;
//            }
//
//
//
//            // write the entire contents of the data packet to the output file starting
//            // from the beginning of the byte array.
//            fileOutputStream.write(dataPacket.getData(), 0, dataPacket.getLength());
//            fileOutputStream.flush();
//        }
//        fileOutputStream.close();
//
//
//    }
//    private static void downloadFromServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray, int seqNum) throws IOException {
//        if (messageArray.length < 2) {
//            // log an error and send an error response to the client
//            System.err.println("Received invalid download request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
//            String errorResponse = "Invalid download request";
//            byte[] responseBuffer = errorResponse.getBytes();
//            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
//            socket.send(responsePacket);
//            return;
//        }
//
//        // log that the download request has been received
//        System.out.println("Received download request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
//        String fileName = messageArray[1];
//        File file = new File("/home/pi/data/" + fileName);
//
//        if (!file.exists()) {
//            // send a response to the client indicating that the file does not exist
//            String errorResponse = "File does not exist";
//            byte[] responseBuffer = errorResponse.getBytes();
//            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
//            socket.send(responsePacket);
//        } else {
//            // send a response to the client indicating that the server is ready to send the file
//            String uploadResponse = "Ready to send file " + fileName;
//            System.out.println("ready to send file " + fileName);
//            byte[] responseBuffer = uploadResponse.getBytes();
//            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
//            socket.send(responsePacket);
//
//            // open the file and read it in chunks of 1024 bytes
//            FileInputStream fileInputStream = new FileInputStream(file);
//            byte[] fileBuffer = new byte[1024];
//            int bytesRead;
//            while ((bytesRead = fileInputStream.read(fileBuffer)) > 0) {
//                byte[] chunkBuffer = Arrays.copyOfRange(fileBuffer, 0, bytesRead);
//                DatagramPacket filePacket = new DatagramPacket(chunkBuffer, bytesRead, receivePacket.getAddress(), receivePacket.getPort());
//                socket.send(filePacket);
//            }
//
//            System.out.println("File sent successfully");
//
//            // send "end" message to client
//            byte[] endBuffer = "end".getBytes();
//            DatagramPacket endPacket = new DatagramPacket(endBuffer, endBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
//            socket.send(endPacket);
//
//            fileInputStream.close();
//        }
//    }



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
