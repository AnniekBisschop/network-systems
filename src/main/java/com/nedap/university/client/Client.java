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
            System.out.print("Enter your choice: no. 1 or 2\n");

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
                        System.out.println(uploadMessage);
                        byte[] uploadBuffer = uploadMessage.getBytes();
                        DatagramPacket uploadPacket = new DatagramPacket(uploadBuffer, uploadBuffer.length, serverAddress, 9090);
                        socket.send(uploadPacket);

                        // read file from local file system and send to server in packets
//                        byte[] fileBuffer = new byte[1024];
//                        FileInputStream fileInputStream = new FileInputStream(file);
//                        int bytesRead = 0;
//                        int packetCount = 0;
//                        while ((bytesRead = fileInputStream.read(fileBuffer)) != -1) {
//                            packetCount++;
//                            DatagramPacket filePacket = new DatagramPacket(fileBuffer, bytesRead, InetAddress.getLocalHost(), 12345);
//                            socket.send(filePacket);
//                        }
//                        System.out.println("File sent in " + packetCount + " packets.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }


                    break;

                case "2":
                    // send download request to server
                    System.out.print("Enter file name: ");
                    System.out.print("format: download <filename> ");
                    String downloadFileName = in.readLine();
                    String downloadMessage = "download " + downloadFileName;
                    byte[] downloadBuffer = downloadMessage.getBytes();
                    DatagramPacket downloadPacket = new DatagramPacket(downloadBuffer, downloadBuffer.length, InetAddress.getLocalHost(), 12345);
                    socket.send(downloadPacket);

                    // receive file from server and save to local file system
                    FileOutputStream fileOutputStream = new FileOutputStream(downloadFileName);
                    byte[] packetBuffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(packetBuffer, packetBuffer.length);
                    while (true) {
                        socket.receive(packet);
                        byte[] data = packet.getData();
                        if (data[0] == 'd' && data[1] == 'o' && data[2] == 'n' && data[3] == 'e') {
                            break;
                        }
                        fileOutputStream.write(data, 0, packet.getLength());
                    }
                    fileOutputStream.close();
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
