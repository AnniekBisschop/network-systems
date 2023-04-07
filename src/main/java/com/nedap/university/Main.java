package com.nedap.university;

import com.nedap.university.server.Server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class Main {

    private static boolean keepAlive = true;
    private static boolean running = false;

    private Main() {}

    public static void main(String[] args) throws IOException {
        System.out.println("Hello, Nedap University!");

        initShutdownHook();

        Server server = new Server();
        server.start();
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

