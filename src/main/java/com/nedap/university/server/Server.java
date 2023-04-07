package com.nedap.university.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

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
                        if (messageArray.length < 2) {
                            // log an error and send an error response to the client
                            System.err.println("Received invalid upload request from client " + receivePacket.getAddress() + ":" + receivePacket.getPort());
                            String errorResponse = "Invalid upload request";
                            byte[] responseBuffer = errorResponse.getBytes();
                            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
                            socket.send(responsePacket);
                            break;
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
                        while (true) {
                            socket.receive(dataPacket);

                            // if the data packet contains the end of file marker, break out of the loop
                            if (new String(dataPacket.getData(), 0, dataPacket.getLength()).equals("end")) {
                                break;
                            }

                            // write the entire contents of the data packet to the output file starting
                            // from the beginning of the byte array.
                            fileOutputStream.write(dataPacket.getData(), 0, dataPacket.getLength());
                        }
                        fileOutputStream.close();

                        // send a response to the client indicating the upload was successful
                        String uploadResponse = "File uploaded successfully";
                        byte[] responseBuffer = uploadResponse.getBytes();
                        DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
                        socket.send(responsePacket);
                        break;
                    case "list":
                        listAllFilesOnServer(socket, receivePacket);
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Stopped");
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
