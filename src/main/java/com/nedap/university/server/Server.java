package com.nedap.university.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Server {

    private boolean keepAlive = true;

    public void start() {
        try {
            DatagramSocket socket = new DatagramSocket(9090); // server socket listening on port 9090

            byte[] receiveBuffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

            while (true) {
                // receive packet from client
                socket.receive(receivePacket);
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                String[] messageArray = message.split(" ", 2);

                switch (messageArray[0]) {
                    case "Hello":
                        System.out.println("Hello message received from " + receivePacket.getAddress());
                        // send a response with available options
                        String options = "What would you like to do?\n1. Upload\n2. Download";
                        byte[] sendBuffer = options.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
                        socket.send(sendPacket);
                        break;
                    case "upload":
                        // receive file from client and store in a folder
                        String fileName = messageArray[1];
                        File file = new File(fileName);
                        FileOutputStream fileOutputStream = new FileOutputStream(file);
                        byte[] buffer = new byte[1024];
                        DatagramPacket dataPacket = new DatagramPacket(buffer, buffer.length);
                        while (true) {
                            socket.receive(dataPacket);
                            if (new String(dataPacket.getData(), 0, dataPacket.getLength()).equals("end")) {
                                break;
                            }
                            fileOutputStream.write(dataPacket.getData(), 0, dataPacket.getLength());
                        }
                        fileOutputStream.close();
                        // send a response to the client indicating the upload was successful
                        String uploadResponse = "File uploaded successfully";
                        byte[] responseBuffer = uploadResponse.getBytes();
                        DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
                        socket.send(responsePacket);
                        break;
                    case "download":
                        // read file from a folder and send to the client in packets
                        String downloadFileName = messageArray[1];
                        File downloadFile = new File(downloadFileName);
                        FileInputStream fileInputStream = new FileInputStream(downloadFile);
                        buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                            DatagramPacket packet = new DatagramPacket(buffer, bytesRead, receivePacket.getAddress(), receivePacket.getPort());
                            socket.send(packet);
                        }
                        fileInputStream.close();
                        // send a response to the client indicating the download was successful
                        String downloadResponse = "File downloaded successfully";
                        responseBuffer = downloadResponse.getBytes();
                        responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
                        socket.send(responsePacket);
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Stopped");
    }

    public void stop() {
        keepAlive = false;
    }
}