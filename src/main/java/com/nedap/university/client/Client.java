package com.nedap.university.client;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

/*
* Datagram packets can be only bytes
* */

public class Client {
    public static void main(String[] args) {
        try{
            // Create a datagram socket on a randomly assigned available port
            DatagramSocket clientSocket = new DatagramSocket(0);
            //1024 is common practise
            byte[] sendData = new byte[1024]; //store outgoing data (buffer)
            byte[] receiveData = new byte[1024]; //store incoming data (buffer)
            // Calculate the maximum amount of data that can be sent in a UDP datagram packet
            // which is 65535 bytes minus 20 bytes for the IP header and 8 bytes for the UDP header
            // This leaves a maximum data size of 65508 bytes

            // Get the IP address of the server we want to send data to
            InetAddress serverAddress = InetAddress.getByName("172.16.1.1");

            // Create a string to send to the server
            String stringSendData = "Hello Server";

            // Convert the string to bytes and store in the send data buffer
            sendData = stringSendData.getBytes();

            // Create a datagram packet with the send data buffer, length, server address, and port number
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress,9090);

            // Send the datagram packet to the server
            clientSocket.send(sendPacket);

            // Create a datagram packet to receive data from the server
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            // Wait for a datagram packet to be received from the server
            clientSocket.receive(receivePacket);

            // Get the data from the receive packet buffer
            receiveData = receivePacket.getData();

            // Convert the received data to a string
            String stringReceivedData = new String(receiveData);

            // Print the received data to the console
            System.out.println("From server: " + stringReceivedData);

            // Close the client socket
            clientSocket.close();

        }catch (Exception e){
            System.out.println(e.toString());
        }
    }
}
