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
            //random portnumber "pick random available port"
            DatagramSocket clientSocket = new DatagramSocket(0);
            //1024 is common practise
            byte[] sendData = new byte[1024]; //store outgoing data (buffer)
            byte[] receiveData = new byte[1024]; //store incoming data (buffer)
            //amount of data = 65535 - 20  (IP header) - 8 (UDP header) = 65508 bytes

            InetAddress serverAddress = InetAddress.getByName("172.16.1.1");

            String stringSendData = "Hello Server";
            sendData = stringSendData.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress,9090);
            clientSocket.send(sendPacket);

            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);
            receiveData = receivePacket.getData();
            String stringReceivedData = new String(receiveData);
            System.out.println("From server: " + stringReceivedData);

            clientSocket.close();

        }catch (Exception e){
            System.out.println(e.toString());
        }
    }
}
