package com.nedap.university.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Server {
    private DatagramSocket socket;
    private boolean keepAlive = true;

    public void start() {
        try {
            // Create a new DatagramSocket object with port number 9090
            socket = new DatagramSocket(9090);
            // Create two byte arrays for storing incoming and outgoing data
            byte[] sendData = new byte[1024]; //store outgoing data (buffer)
            byte[] receiveData = new byte[1024]; //store incoming data (buffer)

            // Loop to continuously receive and send data
            while (keepAlive) {
                // Create a DatagramPacket object to receive incoming data
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket); // Wait for incoming data
                // Convert the received data to a string
                String sentence = new String(receivePacket.getData());
                // Print the received data to the console
                System.out.println("Received from client: " + sentence);
                // Prepare the data to be sent back to the client
                String stringData = "Hello client, how are you doing today? Did you want to send anything?";
                sendData = stringData.getBytes();
                InetAddress clientIpAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();
                // Create a DatagramPacket object to send data back to the client
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientIpAddress, clientPort);
                socket.send(sendPacket);
                // Print the data to the console
                System.out.println("Sent to client: " + stringData);

                Thread.sleep(1000);
            }

            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Stopped");
    }

    public void stop() {
        keepAlive = false;
    }
}