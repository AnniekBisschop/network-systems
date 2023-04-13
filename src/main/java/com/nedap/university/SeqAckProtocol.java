package com.nedap.university;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.io.IOException;

public class SeqAckProtocol {
    public static final int SEQ_MODULO = 256;
    public static final int HEADER_SIZE = 8;
    private DatagramSocket socket;
    private InetAddress address;
    private int port;
    private int seqNum;
    private int ackNum;
    private int timeout;
    private final int PACKET_SIZE = 1024;


    public SeqAckProtocol(DatagramSocket socket, InetAddress address, int port, int timeout) throws IOException {
        this.socket = socket;
        this.address = address;
        this.port = port;
        this.seqNum = 0;
        this.ackNum = 0;
        this.socket.setSoTimeout(timeout);
    }

    public void sendPacket(int seqNum, byte[] data, boolean fin) throws IOException {
        byte[] header = createHeader(seqNum, fin);
        byte[] packetData = concatenateByteArrays(header, data);
        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, address, port);
        socket.send(packet);
        receiveAck();
    }

    public void receive() throws IOException {
        byte[] buffer = new byte[PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
    }

    public boolean receivePacket(int expectedSeqNum, byte[] buffer) throws IOException {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet);
        } catch (SocketTimeoutException e) {
            return false;
        }
        byte[] packetData = packet.getData();
        int receivedSeqNum = getSeqNum(packetData);
        boolean fin = getFinFlag(packetData);
        if (receivedSeqNum != expectedSeqNum) {
            return false;
        }
        byte[] data = new byte[packet.getLength() - PACKET_SIZE];
        System.arraycopy(packetData, PACKET_SIZE, data, 0, data.length);
        ackNum = receivedSeqNum;
        sendAck();
        return fin;
    }

    private void receiveAck() throws IOException {
        byte[] buffer = new byte[PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        byte[] packetData = packet.getData();
        int receivedAckNum = getAckNum(packetData);
        if (receivedAckNum != seqNum) {
            receiveAck();
        }
    }

    private void sendAck() throws IOException {
        byte[] header = createHeader(ackNum, false);
        DatagramPacket packet = new DatagramPacket(header, header.length, address, port);
        socket.send(packet);
    }

    private byte[] createHeader(int seqNum, boolean fin) {
        byte[] header = new byte[8];
        header[0] = (byte) (seqNum >> 24);
        header[1] = (byte) (seqNum >> 16);
        header[2] = (byte) (seqNum >> 8);
        header[3] = (byte) (seqNum);
        header[4] = (byte) (fin ? 1 : 0);
        header[5] = 0;
        header[6] = 0;
        header[7] = 0;
        return header;
    }

    public int getSeqNum(byte[] packetData) {
        return (packetData[0] << 24) |
                ((packetData[1] & 0xFF) << 16) |
                ((packetData[2] & 0xFF) << 8) |
                (packetData[3] & 0xFF);
    }

    public boolean getFinFlag(byte[] packetData) {
        return packetData[4] == 1;
    }

private int getAckNum(byte[] packetData) {
        return (packetData[5] << 24) |
        ((packetData[6] & 0xFF) << 16) |
        ((packetData[7] & 0xFF) << 8) |
        (packetData[8] & 0xFF);
        }

        // Methode om twee byte arrays
        private byte[] concatenateByteArrays(byte[] a, byte[] b) {
            byte[] result = new byte[a.length + b.length];
            System.arraycopy(a, 0, result, 0, a.length);
            System.arraycopy(b, 0, result, a.length, b.length);
            return result;
        }

    }
