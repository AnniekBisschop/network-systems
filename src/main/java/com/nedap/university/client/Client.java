package com.nedap.university.client;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

/*
* Datagram packets can be only bytes
* */

public class Client {
    public static void main(String[] args) {
        try {
            DatagramSocket socket = new DatagramSocket(); // client socket
// Get the IP address of the server we want to send data to
            InetAddress serverAddress = InetAddress.getByName("172.16.1.1");
            // send "Hello server" to server
            byte[] helloBuffer = "Hello server".getBytes();
            DatagramPacket helloPacket = new DatagramPacket(helloBuffer, helloBuffer.length, serverAddress, 9090);
            socket.send(helloPacket);

            // receive response from server
            byte[] receiveBuffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);
            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

            // display response and menu options to user
            System.out.println(response);
            System.out.print("Enter your choice: no. 1 , 2 , 3\n");

            // read user choice from console
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            String choice = in.readLine();

            switch (choice) {
                case "1":
                    try {
                        // send upload request to server
                        System.out.print("Enter path to file you want to upload: ");
                        String filePath = in.readLine();
                        File file = new File(filePath);
                        if (!file.exists()) {
                            System.out.println("File not found.");
                            break;
                        }
                        String uploadMessage = "upload " + file.getName();

                        byte[] uploadBuffer = uploadMessage.getBytes();
                        DatagramPacket uploadPacket = new DatagramPacket(uploadBuffer, uploadBuffer.length, serverAddress, 9090);
                        socket.send(uploadPacket);

                        // receive response from server
                        byte[] responseBuffer = new byte[1024];
                        DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
                        socket.receive(responsePacket);
                        String responseUpload = new String(responsePacket.getData(), 0, responsePacket.getLength());
                        if (responseUpload.equals("Ready to receive file")) {
                            System.out.println("Server is ready to receive file.");
                        } else if (responseUpload.equals("File already exists")) {
                            System.out.println("File already exists on server.");
                            break;
                        } else {
                            System.err.println("Received unexpected response from server: " + responseUpload);
                            break;
                        }

                        // read file from local file system and send to server in packets
                        byte[] fileBuffer = new byte[1024];
                        FileInputStream fileInputStream = new FileInputStream(file);
                        int bytesRead = 0;
                        int packetCount = 0;
                        while ((bytesRead = fileInputStream.read(fileBuffer)) != -1) {
                            packetCount++;
                            DatagramPacket filePacket = new DatagramPacket(fileBuffer, bytesRead, serverAddress, 9090);
                            socket.send(filePacket);
                        }

                        // send end of file marker to server
                        String endMessage = "end";
                        byte[] endBuffer = endMessage.getBytes();
                        DatagramPacket endPacket = new DatagramPacket(endBuffer, endBuffer.length, serverAddress, 9090);
                        socket.send(endPacket);

                        System.out.println("File sent in " + packetCount + " packets.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "2":
                    try {
                        // send list request to server
                        String listMessage = "list";
                        byte[] listBuffer = listMessage.getBytes();
                        DatagramPacket listPacket = new DatagramPacket(listBuffer, listBuffer.length, serverAddress, 9090);
                        socket.send(listPacket);

                        // receive list from server
                        byte[] listBufferResponse = new byte[1024];
                        DatagramPacket listPacketResponse = new DatagramPacket(listBufferResponse, listBufferResponse.length);
                        socket.receive(listPacketResponse);
                        String responseList = new String(listPacketResponse.getData(), 0, listPacketResponse.getLength());
                        System.out.println("List of files on server:\n" + responseList);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "3":
                    try {
                        // send remove request to server
                        System.out.print("Enter name of file you want to remove: ");
                        String fileName = in.readLine();
                        String removeMessage = "remove " + fileName;

                        byte[] removeBuffer = removeMessage.getBytes();
                        DatagramPacket removePacket = new DatagramPacket(removeBuffer, removeBuffer.length, serverAddress, 9090);
                        socket.send(removePacket);

                        // receive response from server
                        byte[] responseBuffer = new byte[1024];
                        DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
                        socket.receive(responsePacket);
                        String responseRemove = new String(responsePacket.getData(), 0, responsePacket.getLength());
                        if (responseRemove.equals("File removed successfully")) {
                            System.out.println("File removed successfully");
                        } else if (responseRemove.equals("File not found")) {
                            System.out.println("File not found on server.");
                        } else {
                            System.err.println("Received unexpected response from server: " + responseRemove);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    System.out.println("Invalid choice");
                    break;
            }
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
