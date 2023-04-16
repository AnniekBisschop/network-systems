package com.nedap.university;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;

public class Protocol {

    private static final int HEADER_SIZE = 8; // 4 bytes for seq, 4 bytes for ack

    /**
     * This method takes in two integer parameters, seqNum and ackNum, and returns a byte array of size HEADER_SIZE,
     * which is a constant set to 8.
     * Create a header for a packet that includes the sequence number and acknowledgement number,
     * which are both 4 bytes long.
     */
    public static byte[] createHeader(int seqNum, int ackNum) {
        //The first line of the method creates a new byte array called header with a length of HEADER_SIZE.
        byte[] header = new byte[HEADER_SIZE];
        //set the first four bytes of the header array to the four bytes of the seqNum parameter, extract each byte.
        header[0] = (byte) ((seqNum >> 24) & 0xFF);
        header[1] = (byte) ((seqNum >> 16) & 0xFF);
        header[2] = (byte) ((seqNum >> 8) & 0xFF);
        header[3] = (byte) ((seqNum) & 0xFF);
        //set the next three bytes of the header array to the three bytes of the ackNum parameter
        header[4] = (byte) ((ackNum >> 24) & 0xFF);
        header[5] = (byte) ((ackNum >> 16) & 0xFF);
        header[6] = (byte) ((ackNum >> 8) & 0xFF);
        header[7] = (byte) ((ackNum) & 0xFF);
        return header;
    }

    public static int getSeqNum(byte[] header) {
        return (header[0] << 24) & 0xFF000000 |
                (header[1] << 16) & 0x00FF0000 |
                (header[2] << 8) & 0x0000FF00 |
                (header[3] << 0) & 0x000000FF;
        // ByteBuffer byteBuffer = ByteBuffer.wrap(header);
        //return byteBuffer.getInt();
    }

    // Extract the acknowledgement number from the packet header
    public static int getAckNum(byte[] header) {
        return  (header[4] << 24) & 0xFF000000 |
                (header[5] << 16) & 0x00FF0000 |
                (header[6] << 8) & 0x0000FF00 |
                (header[7] << 0) & 0x000000FF;
        //ByteBuffer byteBuffer = ByteBuffer.wrap(header);
        //byteBuffer.getInt(); // Skip over seqNum
        //return byteBuffer.getInt();
    }

//Different way to get seq and ack
//    private static int getSeqNum(byte[] header) {
//        ByteBuffer byteBuffer = ByteBuffer.wrap(header);
//        return byteBuffer.getInt();
//    }
//
//    private static int getAckNum(byte[] header) {
//        ByteBuffer byteBuffer = ByteBuffer.wrap(header);
//        byteBuffer.getInt(); // Skip over seqNum
//        return byteBuffer.getInt();
//    }

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


    //TODO IMPLEMENTATION FOR WAITFORACK
    public static void waitForAck(DatagramSocket socket, int expectedSeqNum) throws IOException {
        boolean ackReceived = false;
        while (!ackReceived) {
            byte[] ackBuffer = new byte[HEADER_SIZE];
            DatagramPacket ackReceivePacket = new DatagramPacket(ackBuffer, ackBuffer.length);
            socket.receive(ackReceivePacket);

            // Extract the sequence number from the acknowledgement header
            int seqNum = Protocol.getSeqNum(ackReceivePacket.getData());

            if (seqNum == expectedSeqNum) {
                System.out.println("Acknowledgement received with seqnum: " + seqNum);
                ackReceived = true;
            } else {
                System.out.println("Unexpected acknowledgement received with seqnum: " + seqNum);
            }
        }
    }

}
