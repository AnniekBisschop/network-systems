package com.nedap.university;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.file.Files;
import java.util.Arrays;

public class Protocol {

    private static final int HEADER_SIZE = 8; // 4 bytes for seq, 4 bytes for ack
    /**
     Creates a header byte array using the given sequence number and acknowledgement number.
     @param seqNum the sequence number to use in the header
     @param ackNum the acknowledgement number to use in the header
     @return a byte array representing the header with the given sequence and acknowledgement numbers
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

    /**
     Extracts the sequence number from the given header byte array.
     @param header the header byte array to extract the sequence number from
     @return the sequence number extracted from the header byte array
     */
    public static int getSeqNum(byte[] header) {
        return (header[0] << 24) & 0xFF000000 |
                (header[1] << 16) & 0x00FF0000 |
                (header[2] << 8) & 0x0000FF00 |
                (header[3] << 0) & 0x000000FF;
    }
    /**
     Extracts the acknowledgement number from the given header byte array.
     @param header the header byte array to extract the acknowledgement number from
     @return the acknowledgement number extracted from the header byte array
     */
    public static int getAckNum(byte[] header) {
        return  (header[4] << 24) & 0xFF000000 |
                (header[5] << 16) & 0x00FF0000 |
                (header[6] << 8) & 0x0000FF00 |
                (header[7] << 0) & 0x000000FF;
    }

    /**
     Creates a response packet containing the given message as data and header information.
     @param message the message to include in the response packet
     @param socket the DatagramSocket to send the response packet on
     @param receivePacket the packet received that triggered this response
     @param seqNum the sequence number to use for the response packet
     @return the DatagramPacket representing the response packet
     */
    public static DatagramPacket createResponsePacket(String message, DatagramSocket socket, DatagramPacket receivePacket, int seqNum) {
        byte[] header = createHeader(seqNum, 0);
        byte[] responseBuffer = message.getBytes();
        byte[] response = new byte[header.length + responseBuffer.length];
        System.arraycopy(header, 0, response, 0, header.length);
        System.arraycopy(responseBuffer, 0, response, header.length, responseBuffer.length);
        return new DatagramPacket(response, response.length, receivePacket.getAddress(), receivePacket.getPort());
    }

    /**
     Creates a response packet containing the given data and header information.
     @param data the data to include in the response packet
     @param socket the DatagramSocket to send the response packet on
     @param receivePacket the packet received that triggered this response
     @param seqNum the sequence number to use for the response packet
     @return the DatagramPacket representing the response packet
     */
    public static DatagramPacket createResponsePacket(byte[] data, DatagramSocket socket, DatagramPacket receivePacket, int seqNum) {
        byte[] header = createHeader(seqNum++, 0);
        byte[] response = new byte[header.length + data.length];
        System.arraycopy(header, 0, response, 0, header.length);
        System.arraycopy(data, 0, response, header.length, data.length);
        return new DatagramPacket(response, response.length, receivePacket.getAddress(), receivePacket.getPort());
    }
    /**
     Sends an acknowledgement packet on the specified socket for the given sequence number.
     @param socket the DatagramSocket to send the acknowledgement on
     @param receivePacket the packet to acknowledge
     @param seqNum the sequence number to acknowledge
     @throws IOException if there is an error sending the acknowledgement
     */
    public static void sendAck(DatagramSocket socket, DatagramPacket receivePacket, int seqNum) throws IOException {
        byte[] ackHeader = createHeader(seqNum, seqNum + 1);
        DatagramPacket ackPacket = new DatagramPacket(ackHeader, ackHeader.length, receivePacket.getAddress(), receivePacket.getPort());
        socket.send(ackPacket);
    }
    /**
     Receives an acknowledgement packet on the specified socket.
     @param socket the DatagramSocket to receive the acknowledgement on
     @param receivePacket the packet to expect the acknowledgement for
     @param seqNum the sequence number of the packet to expect the acknowledgement for
     @return the DatagramPacket representing the received acknowledgement
     @throws IOException if there is an error receiving the acknowledgement
     */
    public static DatagramPacket receiveAck(DatagramSocket socket, DatagramPacket receivePacket, int seqNum) throws IOException {
        byte[] ackBuffer = new byte[HEADER_SIZE];
        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, HEADER_SIZE);
        socket.receive(ackPacket);
        return ackPacket;
    }
   /**
    Receives a datagram packet on the specified socket and extracts the message data from it.
    @param socket the DatagramSocket to receive the packet on
    @param headerLength the length of the header to skip when extracting the message data
    @return a byte array containing the extracted message data
    @throws IOException if there is an error receiving the packet
    */
    public static byte[] receiveData(DatagramSocket socket, int headerLength) throws IOException {
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);
        byte[] responseData = receivePacket.getData();
        int messageLength = responseData.length - headerLength;
        byte[] messageData = new byte[messageLength];
        System.arraycopy(responseData, headerLength, messageData, 0, messageLength);
        return messageData;
    }

    /**
     Calculates the hash of the given byte array using the default hashing algorithm.
     @param data the byte array to calculate the hash of
     @return a string representation of the calculated hash value
     */
    public static String getHash(byte[] data){
        int hash = Arrays.hashCode(data);
        String hashFunction = String.valueOf(hash);
        return hashFunction;
    }

    /**
     Checks the hash of a given file against an expected hash value.
     @param file the file to check the hash of
     @param expectedHash the expected hash value to compare against
     @return true if the hash of the file matches the expected hash, false otherwise
     @throws IOException if there is an error reading the file
     */
    public static boolean checkFileHash(File file, String expectedHash) throws IOException {
        byte[] fileData = Files.readAllBytes(file.toPath());
        String actualHash = Protocol.getHash(fileData);
        return actualHash.equals(expectedHash);
    }

}
