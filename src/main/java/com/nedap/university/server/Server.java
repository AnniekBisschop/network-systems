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

    private static final int BUFFER_SIZE = 1024;
    private static final int HEADER_SIZE = 8; // 4 bytes for seq, 4 bytes for ack
    private static final byte[] ACK = {0, 1};

    private boolean keepAlive = true;

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

                // Send an acknowledgement for the received sequence number
                byte[] header = createHeader(seqNum, seqNum + 1);
                DatagramPacket ackPacket = new DatagramPacket(header, header.length, receivePacket.getSocketAddress());
                socket.send(ackPacket);
                System.out.println("ackPacket send" + ackPacket);
                // extract the data from the packet and split it into an array of strings
                byte[] data = new byte[receivePacket.getLength() - HEADER_SIZE];
                System.arraycopy(receivedData, HEADER_SIZE, data, 0, data.length);
                String message = new String(data);
                String[] messageArray = message.split(" ");
                System.out.println("message for switch: " + message);
                // perform different actions based on the first string in the array
                switch (messageArray[0]) {
                    case "Hello":
                        System.out.println("Hello message received from " + receivePacket.getAddress());

                        // send a response with available options
                        String options = "Welcome, You have successfully connected to the server.\n What would you like to do?";
                        byte[] sendBuffer = options.getBytes();
                        header = createHeader(seqNum + 1, seqNum);
                        byte[] sendData = new byte[sendBuffer.length + HEADER_SIZE];
                        System.arraycopy(header, 0, sendData, 0, HEADER_SIZE);
                        System.arraycopy(sendBuffer, 0, sendData, HEADER_SIZE, sendBuffer.length);
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, receivePacket.getAddress(), receivePacket.getPort());
                        socket.send(sendPacket);
                        System.out.println("Menu options send to client");
                        break;
                    case "upload":
                        uploadFileToServer(socket, receivePacket, messageArray, seqNum);
                        break;
                    case "download":
                        downloadFromServer(socket, receivePacket, messageArray, seqNum);
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
            e.printStackTrace();
        }

        System.out.println("Stopped");
    }

    /**
     * This method takes in two integer parameters, seqNum and ackNum, and returns a byte array of size HEADER_SIZE,
     * which is a constant set to 8.
     * Create a header for a packet that includes the sequence number and acknowledgement number,
     * which are both 4 bytes long.
     * */
    private byte[] createHeader(int seqNum, int ackNum) {
        //The first line of the method creates a new byte array called header with a length of HEADER_SIZE.
        byte[] header = new byte[HEADER_SIZE];
        //set the first four bytes of the header array to the four bytes of the seqNum parameter, extract each byte.
        header[0] = (byte) ((seqNum >> 24) & 0xFF);
        header[1] = (byte) ((seqNum >> 16) & 0xFF);
        header[2] = (byte) ((seqNum >> 8) & 0xFF);
        header[3] = (byte) ((seqNum) & 0xFF);
        //set the next three bytes of the header array to the three bytes of the ackNum parameter
        header[4] = (byte) ((ackNum >> 24) & 0xFF);
        header[5] = (byte) ((ackNum >> 16) & 0xFF);
        header[6] = (byte) ((ackNum >> 8) & 0xFF);
        header[7] = (byte) ((ackNum) & 0xFF);
        return header;
    }


    private static void uploadFileToServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray, int seqNum) throws IOException {
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
           // check if the sequence number is the expected value
            if (sequenceNumber == expectedSequenceNumber) {
                // send an ack to the client with the sequence number in the header
                byte[] ackData = String.valueOf(sequenceNumber).getBytes();
                DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, receivePacket.getAddress(), receivePacket.getPort());
                socket.send(ackPacket);
                System.out.println("Ack sent to client");
                // increment the expected sequence number
                expectedSequenceNumber++;
            }



            // write the entire contents of the data packet to the output file starting
            // from the beginning of the byte array.
            fileOutputStream.write(dataPacket.getData(), 0, dataPacket.getLength());
            fileOutputStream.flush();
        }
        fileOutputStream.close();
        System.out.println("Sequence numbers:");
//        for (int seqNum : sequenceNumbers) {
//            System.out.println("Sequence number:" + seqNum);
//        }
        // send a response to the client indicating the upload was successful
        String uploadResponse = "File uploaded successfully";
        byte[] responseBuffer = uploadResponse.getBytes();
        DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
        socket.send(responsePacket);
    }
    private static void downloadFromServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray, int seqNum) throws IOException {
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
    private static void removeFileOnServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray, int seqNum) throws IOException {
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

    private static void replaceFileOnServer(DatagramSocket socket, DatagramPacket receivePacket, String[] messageArray, int seqNum) throws IOException {
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

    private static void listAllFilesOnServer(DatagramSocket socket, DatagramPacket receivePacket, int seqNum) throws IOException {
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
