package com.nedap.university.client;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/*
* Datagram packets can be only bytes
* */

public class Client {

    private static final int PORT = 9090;
    private static final int HEADER_SIZE = 8; // 4 bytes for seq, 4 bytes for ack

    public static void main(String[] args) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();

            // Get the IP address of the server we want to send data to
            InetAddress serverAddress = InetAddress.getByName("localhost");

            boolean ackReceived = false;
            int maxTries = 3; // set maximum number of tries to send the hello packet
            int tries = 0; // initialize the number of tries

            while (!ackReceived && tries < maxTries) {
                try {
                    sendHelloPacketToServer(socket, serverAddress);
                    if (tries > 0) {
                        System.out.println("Hello packet retransmitted...");
                    }
                    // set timeout for receiving acknowledgment from server
                    socket.setSoTimeout(5000); // 5 seconds

                    // receive acknowledgement from server
                    byte[] receiveBuffer = new byte[HEADER_SIZE];
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    socket.receive(receivePacket);
                    System.out.println("acknowledgement from server received: " + receivePacket);
                    int seqNum = getSeqNum(receivePacket.getData());
                    int ackNum = getAckNum(receivePacket.getData());
                    System.out.println("seqnum" + seqNum);
                    System.out.println("acknum" + ackNum);
                    ackReceived = true;
                } catch (SocketTimeoutException e) {
                    System.out.println("Acknowledgement not received, trying again...");
                    tries++;
                    if(tries == maxTries){
                        System.out.println("Please try to make a new connection to the server. Program stopped: " +e.getMessage());
                    }
                } catch (IOException e) {
                    System.out.println("Please try to make a new connection to the server. Program stopped.");
                    System.out.println("IOException occurred while communicating with server: " + e.getMessage());
                    System.exit(1);
                }
            }

            // receive response from server
            byte[] receiveBuffer = new byte[1024 + HEADER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);
            System.out.println("response from server received: " + receivePacket);
            int seqNum = getSeqNum(receivePacket.getData());
            int ackNum = getAckNum(receivePacket.getData());
            System.out.println("seqnum" + seqNum);
            System.out.println("acknum" + ackNum);

            // extract the data from the packet and convert it to a string
            byte[] data = new byte[receivePacket.getLength() - HEADER_SIZE];
            System.arraycopy(receivePacket.getData(), HEADER_SIZE, data, 0, data.length);
            String message = new String(data);
            System.out.println(message);


            // read user choice from console
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

            printMenu(); // Call the printMenu() method before the while loop.

            while (true) {
                String choice = in.readLine();

                switch (choice) {
                    case "1":
                        uploadFile(socket, serverAddress, in);
                        break;
                    case "2":
//                        downloadFile(socket, serverAddress, in);
                        break;
                    case "3":
                        removeFile(socket, serverAddress, in);
                        break;
                    case "4":
//                        replaceFile(socket, serverAddress, in);
                        break;
                    case "5":
                        showList(socket, serverAddress);
                        break;
                    case "6":
                        System.out.println("Exiting program...");
                        // TODO: MORE CODE FOR EXITING PROGRAM?
                        System.exit(0);
                        break;
                    default:
                        System.out.println("Invalid choice");
                        break;
                }

                printMenu(); // Call the printMenu() method after each iteration of the while loop.
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // close the socket after the user exits the program
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private static void sendHelloPacketToServer(DatagramSocket socket, InetAddress serverAddress){
        // create the header
        byte[] header = createHeader(0,0);

        // create the data string and convert it to a byte array
        String dataString = "Hello";
        byte[] data = dataString.getBytes();

        // create a new byte array that combines the header and data
        byte[] packetData = new byte[header.length + data.length];
        System.arraycopy(header, 0, packetData, 0, header.length);
        System.arraycopy(data, 0, packetData, header.length, data.length);

        // create and send the packet
        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, serverAddress, PORT);
        try {
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("IOException occurred while sending packet to server: " + e.getMessage());
        }
    }

    private static byte[] createHeader(int seqNum, int ackNum) {
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
    private static int getSeqNum(byte[] header) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(header);
        return byteBuffer.getInt();
    }

    private static int getAckNum(byte[] header) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(header);
        byteBuffer.getInt(); // Skip over seqNum
        return byteBuffer.getInt();
    }

    private static void uploadFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in) throws IOException {
        try {
            // send upload request to server
            System.out.print("Enter path to file you want to upload: ");
            String filePath = in.readLine();
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("File not found.");
                return;
            }
            String uploadMessage = "upload " + file.getName();

            // create the header
            byte[] header = createHeader(0,0);

            sendUploadRequest(socket, serverAddress, uploadMessage, header);

            // receive response from server to start uploading file data
            byte[] receiveBuffer = new byte[HEADER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);
            System.out.println("seqnum" + getSeqNum(receivePacket.getData()));
            System.out.println("acknum" + getAckNum(receivePacket.getData()));

            // read the file data and send it to the server in packets
            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] fileBuffer = new byte[1024];
            int bytesRead;
            int packetCount = 0;
            int seqNum = 0;
            int expectedSeqNum = 0; // initialize expectedSeqNum
            int ackNum = 1;
            while ((bytesRead = fileInputStream.read(fileBuffer)) != -1) {
                packetCount++;
                byte[] packetData = new byte[HEADER_SIZE + bytesRead];
                ByteBuffer byteBuffer = ByteBuffer.wrap(packetData);
                byteBuffer.putInt(seqNum);
                byteBuffer.putInt(ackNum);
                System.arraycopy(fileBuffer, 0, packetData, HEADER_SIZE, bytesRead);

                DatagramPacket packet = new DatagramPacket(packetData, packetData.length, serverAddress, PORT);
                socket.send(packet);

                // wait for acknowledgement from server before sending next packet
                System.out.println("waiting for ack");
                System.out.println("packetcount" + packetCount);
                receiveBuffer = new byte[HEADER_SIZE];
                receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(receivePacket);
                ackNum = getAckNum(receivePacket.getData());

                // check if received ackNum matches expectedSeqNum
                if (ackNum != expectedSeqNum) {
                    System.out.println("Unexpected sequence number received: " + ackNum + " Expected sequence number is " + expectedSeqNum);
                    // resend the packet with the same sequence number and ack number
                    packet.setData(packetData);
                    socket.send(packet);
                }

                seqNum += bytesRead;
                expectedSeqNum = seqNum; // update expectedSeqNum to the next expected sequence number
                System.out.println("seqnum" + seqNum);
                System.out.println("acknum" + ackNum);
            }
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void sendUploadRequest(DatagramSocket socket, InetAddress serverAddress, String uploadMessage, byte[] header) throws IOException {
        // send upload request packet to server
        byte[] uploadBuffer = uploadMessage.getBytes();
        byte[] uploadPacketData = new byte[header.length + uploadBuffer.length];
        System.arraycopy(header, 0, uploadPacketData, 0, header.length);
        System.arraycopy(uploadBuffer, 0, uploadPacketData, header.length, uploadBuffer.length);

        DatagramPacket uploadPacket = new DatagramPacket(uploadPacketData, uploadPacketData.length, serverAddress, PORT);
        socket.send(uploadPacket);
        System.out.println("upload packet send");
    }



//    private static void downloadFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in) {
//        try {
//            // send download request to server
//            System.out.print("Enter name of file you want to download: ");
//            String downloadMessage = "download " + in.readLine();
//            byte[] downloadBuffer = downloadMessage.getBytes();
//            DatagramPacket downloadPacket = new DatagramPacket(downloadBuffer, downloadBuffer.length, serverAddress, 9090);
//            socket.send(downloadPacket);
//
//            // receive response from server
//            byte[] responseBuffer = new byte[1024];
//            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
//            socket.receive(responsePacket);
//            String responseDownload = new String(responsePacket.getData(), 0, responsePacket.getLength());
//            System.out.println("responseDownload" + responseDownload);
//            if (responseDownload.contains("File not found")) {
//                System.out.println("File not found on server.");
//                return;
//            } else if (responseDownload.contains("Ready to send file")) {
//                System.out.println("Server is ready to send file.");
//            } else {
//                System.err.println("Received unexpected response from server: " + responseDownload);
//                return;
//            }
//
//            // extract file name from server response
//            String[] responseParts = responseDownload.split(" ");
//            String fileName = responseParts[responseParts.length - 1];
//
//            // receive file from server in packets and write to local file system
//            String filePath = "src/main/java/com/nedap/university/download/" + fileName;
//            System.out.println("filepath " + filePath);
//            FileOutputStream fileOutputStream = new FileOutputStream(filePath);
//            byte[] fileBuffer = new byte[1024];
//            int bytesRead = 0;
//            int packetCount = 0;
//            while (true) {
//                DatagramPacket filePacket = new DatagramPacket(fileBuffer, fileBuffer.length);
//                socket.receive(filePacket);
//                System.out.println("packet received");
//                String packetData = new String(filePacket.getData(), 0, filePacket.getLength());
//                if (packetData.equals("end")) {
//                    System.out.println("received end packet");
//                    System.out.println("file sent successfully");
//                    break;
//                }
//                packetCount++;
//                System.out.println("Packetcount: " + packetCount);
//                fileOutputStream.write(filePacket.getData(), 0, filePacket.getLength());
//                System.out.println("File received in " + packetCount + " packets and saved to " + filePath + ".");
//            }
//            fileOutputStream.close();
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//
private static void removeFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in) {
    try {

        // send remove request to server
        System.out.print("Enter name of file you want to remove: ");
        String fileName = in.readLine();

        // Send remove request to server
        byte[] header = createHeader(1, 0);
        String message = "remove " + fileName;
        boolean ackReceived = false;
        int maxRetries = 3; // maximum number of times to retry sending the packet
        int numRetries = 0; // number of times the packet has been retried

        while (!ackReceived && numRetries < maxRetries) {
            commandRequestToServer(socket, serverAddress, header, message);
            System.out.println("remove req send");

            // Receive ack from server with a timeout of 5 seconds
            try {
                socket.setSoTimeout(5000); // set the socket timeout to 5 seconds
                receiveAckFromServer(socket);
                ackReceived = true; // set the flag to true if the ack is received
            } catch (SocketTimeoutException e) {
                numRetries++; // increment the retry count if the timeout occurs
                System.out.println("Timeout occurred, retrying...");
            }
        }

        if (ackReceived) {
            System.out.println("remove req acknowledged");
        } else {
            System.out.println("remove req failed after " + maxRetries + " attempts");
            System.out.println("Returning to main menu, please try again...");
            return; // exit the method and return to the main menu
        }


        // Receive response from server
        byte[] responseBuffer = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
        socket.receive(responsePacket);
        String responseRemove = new String(responsePacket.getData(), 0, responsePacket.getLength());
        if (responseRemove.contains("File removed successfully")) {
            System.out.println("File removed successfully");
        } else if (responseRemove.contains("File not found")) {
            System.out.println("File not found on server.");
        } else {
            System.err.println("Received unexpected response from server: " + responseRemove);
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
}


    private static void receiveAckFromServer(DatagramSocket socket) throws IOException {
        byte[] ackBuffer = new byte[HEADER_SIZE];
        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, HEADER_SIZE);
        socket.receive(ackPacket);
        System.out.println("Ack received");
    }

    private static void commandRequestToServer(DatagramSocket socket, InetAddress serverAddress, byte[] header, String message) throws IOException {
        byte[] commandBuffer = message.getBytes();
        byte[] command = new byte[header.length + commandBuffer.length];
        System.arraycopy(header, 0, command, 0, header.length);
        System.arraycopy(commandBuffer, 0,command, header.length, commandBuffer.length);
        DatagramPacket commandPacket = new DatagramPacket(command, command.length, serverAddress, PORT);
        socket.send(commandPacket);
    }
//    private static void replaceFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in) {
//        try {
//            // send replace request to server
//            System.out.print("Enter name of file you want to replace: ");
//            String fileName = in.readLine();
//            String replaceMessage = "replace " + fileName;
//            byte[] replaceBuffer = replaceMessage.getBytes();
//            DatagramPacket replacePacket = new DatagramPacket(replaceBuffer, replaceBuffer.length, serverAddress, PORT);
//            socket.send(replacePacket);
//
//            // receive response from server
//            byte[] responseBuffer = new byte[1024];
//            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
//            socket.receive(responsePacket);
//            String responseReplace = new String(responsePacket.getData(), 0, responsePacket.getLength());
//            if (responseReplace.contains("File not found")) {
//                System.out.println("File not found on server.");
//                return;
//            } else if (responseReplace.contains("Ready to receive")) {
//                System.out.println("upload function??");
//            } else {
//                System.err.println("Received unexpected response from server: " + responseReplace);
//                return;
//            }
//
//            // read file from local file system and send to server in packets
//            System.out.print("Enter path to new file: ");
//            String filePath = in.readLine();
//            File file = new File(filePath);
//            if (!file.exists()) {
//                System.out.println("File not found.");
//                return;
//            }
//
//            byte[] fileBuffer = new byte[1024];
//            FileInputStream fileInputStream = new FileInputStream(file);
//            int bytesRead = 0;
//            int packetCount = 0;
//            while ((bytesRead = fileInputStream.read(fileBuffer)) != -1) {
//                packetCount++;
//                DatagramPacket filePacket = new DatagramPacket(fileBuffer, bytesRead, serverAddress, PORT);
//                socket.send(filePacket);
//            }
//
//            // send end of file marker to server
//            String endMessage = "end";
//            byte[] endBuffer = endMessage.getBytes();
//            DatagramPacket endPacket = new DatagramPacket(endBuffer, endBuffer.length, serverAddress, PORT);
//            socket.send(endPacket);
//
//            // receive response from server indicating file replacement success
//            byte[] successBuffer = new byte[1024];
//            DatagramPacket successPacket = new DatagramPacket(successBuffer, successBuffer.length);
//            socket.receive(successPacket);
//            String successResponse = new String(successPacket.getData(), 0, successPacket.getLength());
//            if (successResponse.contains("File replaced successfully")) {
//                System.out.println("File replaced successfully.");
//            } else {
//                System.err.println("Received unexpected response from server: " + successResponse);
//            }
//
//            System.out.println("File sent in " + packetCount + " packets.");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

private static void showList(DatagramSocket socket, InetAddress serverAddress) {
    try {
        // Send list request to server
        byte[] header = createHeader(1, 0);
        String listMessage = "list";
        byte[] listBuffer = listMessage.getBytes();
        byte[] request = new byte[header.length + listBuffer.length];
        System.arraycopy(header, 0, request, 0, header.length);
        System.arraycopy(listBuffer, 0, request, header.length, listBuffer.length);
        DatagramPacket listPacket = new DatagramPacket(request, request.length, serverAddress, PORT);
        socket.send(listPacket);
        System.out.println("list req send");

        // Receive ack from server
        byte[] ackBuffer = new byte[HEADER_SIZE];
        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, HEADER_SIZE);
        socket.receive(ackPacket);
        System.out.println("Ack received");

        // Receive list from server
        byte[] listBufferResponse = new byte[1024];
        DatagramPacket listPacketResponse = new DatagramPacket(listBufferResponse, listBufferResponse.length);
        socket.receive(listPacketResponse);
        System.out.println("length packet " + listPacketResponse.getLength());

        // Send acknowledgment back to the server
        byte[] ackResponse = createHeader(2, 0); // Acknowledgment header
        DatagramPacket ackPacketResponse = new DatagramPacket(ackResponse, ackResponse.length, serverAddress, PORT);
        socket.send(ackPacketResponse);
        System.out.println("Ack sent");

        // Extract and print file list
        byte[] data = listPacketResponse.getData();
        byte[] responseData = Arrays.copyOfRange(data, HEADER_SIZE, data.length);
        String responseList = new String(responseData, 0, listPacketResponse.getLength() - HEADER_SIZE);

        System.out.println("List received:");
        System.out.println(responseList);

    } catch (IOException e) {
        e.printStackTrace();
    }
}


private static void printMenu(){
        System.out.println("\nMenu Options:");
        System.out.println("1. Upload a file");
        System.out.println("2. Download a file");
        System.out.println("3. Remove a file");
        System.out.println("4. Replace a file");
        System.out.println("5. List available files");
        System.out.println("6. Exit");
        System.out.println("Please choose an option 1-6");
    }
}
