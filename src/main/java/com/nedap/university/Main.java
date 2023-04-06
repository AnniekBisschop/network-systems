package com.nedap.university;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Main {

    private static boolean keepAlive = true;
    private static boolean running = false;

    private Main() {}

    public static void main(String[] args) {
        running = true;
        System.out.println("Hello, Nedap University!");

        initShutdownHook();

        try {
            DatagramSocket socket = new DatagramSocket(9090);
            byte[] sendData = new byte[1024]; //store outgoing data (buffer)
            byte[] receiveData = new byte[1024]; //store incoming data (buffer)

            while (keepAlive) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                String sentence = new String(receivePacket.getData());
                System.out.println("Received from client: " + sentence);
                String stringData = "Hello client, how are you doing today? Did you want to send anything?";
                sendData = stringData.getBytes();
                InetAddress clientIpAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientIpAddress, clientPort);
                socket.send(sendPacket);
                System.out.println("Sent to client: " + stringData);

                Thread.sleep(1000);
            }

            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Stopped");
        running = false;
    }




    private static void initShutdownHook() {
        final Thread shutdownThread = new Thread() {
            @Override
            public void run() {
                keepAlive = false;
                while (running) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(shutdownThread);
    }
}

