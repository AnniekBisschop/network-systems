package com.nedap.university;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Protocol {

    private static final int HEADER_SIZE = 8; // 4 bytes for seq, 4 bytes for ack

    /**
     * This method takes in two integer parameters, seqNum and ackNum, and returns a byte array of size HEADER_SIZE,
     * which is a constant set to 8.
     * Create a header for a packet that includes the sequence number and acknowledgement number,
     * which are both 4 bytes long.
     */
    public static byte[] createHeader(int seqNum, int ackNum) {
        byte[] header = new byte[HEADER_SIZE];
        header[0] = (byte) ((seqNum >> 24) & 0xFF);
        header[1] = (byte) ((seqNum >> 16) & 0xFF);
        header[2] = (byte) ((seqNum >> 8) & 0xFF);
        header[3] = (byte) ((seqNum) & 0xFF);
        header[4] = (byte) ((ackNum >> 24) & 0xFF);
        header[5] = (byte) ((ackNum >> 16) & 0xFF);
        header[6] = (byte) ((ackNum >> 8) & 0xFF);
        header[7] = (byte) ((ackNum) & 0xFF);
        return header;
    }

    public static DatagramPacket createResponsePacket(String message, DatagramSocket socket, DatagramPacket receivePacket, int seqNum) {
        byte[] header = createHeader(seqNum, 0);
        byte[] responseBuffer = message.getBytes();
        byte[] response = new byte[header.length + responseBuffer.length];
        System.arraycopy(header, 0, response, 0, header.length);
        System.arraycopy(responseBuffer, 0, response, header.length, responseBuffer.length);
        return new DatagramPacket(response, response.length, receivePacket.getAddress(), receivePacket.getPort());
    }

    public static void sendServerAck(DatagramSocket socket, DatagramPacket receivePacket, int seqNum) throws IOException {
        // Create a header with the sequence number and acknowledgement number
        byte[] ackHeader = createHeader(seqNum, seqNum + 1);

        // Create a DatagramPacket object that only includes the header
        DatagramPacket ackPacket = new DatagramPacket(ackHeader, ackHeader.length, receivePacket.getAddress(), receivePacket.getPort());

        // Send the acknowledgement packet
        socket.send(ackPacket);
        System.out.println("Ack sent with seqnum: " + seqNum);
    }

}
