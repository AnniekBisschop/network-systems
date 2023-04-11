package com.nedap.university.client;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;

/*
* Datagram packets can be only bytes
* */

public class Client {

    private static final int PORT = 9090;
    public static void main(String[] args) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();

            // Get the IP address of the server we want to send data to
            InetAddress serverAddress = InetAddress.getByName("172.16.1.1");

            // send "Hello server" to server
            byte[] helloBuffer = "Hello server".getBytes();
            DatagramPacket helloPacket = new DatagramPacket(helloBuffer, helloBuffer.length, serverAddress, PORT);
            socket.send(helloPacket);

            // receive response from server
            byte[] receiveBuffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);
            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

            // display response and menu options to user
            System.out.println(response);


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
                        downloadFile(socket, serverAddress, in);
                        break;
                    case "3":
                        removeFile(socket, serverAddress, in);
                        break;
                    case "4":
                        replaceFile(socket, serverAddress, in);
                        break;
                    case "5":
                        showList(socket, serverAddress);
                        break;
                    case "6":
                        System.out.println("Exiting program...");
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

            byte[] uploadBuffer = uploadMessage.getBytes();
            DatagramPacket uploadPacket = new DatagramPacket(uploadBuffer, uploadBuffer.length, serverAddress, PORT);
            socket.send(uploadPacket);

            // receive response from server
            byte[] responseBuffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(responsePacket);
            String responseUpload = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (responseUpload.contains("Ready to receive file")) {
                System.out.println("Server is ready to receive file.");
            } else if (responseUpload.contains("File already exists")) {
                System.out.println("File already exists on server.");
                return;
            } else {
                System.err.println("Received unexpected response from server: " + responseUpload);
                return;
            }

// read file from local file system and send to server in packets
            byte[] fileBuffer = new byte[1024];
            FileInputStream fileInputStream = new FileInputStream(file);
            int bytesRead;
            int packetCount = 0;
            int sequenceNumber = 0; // initialize sequence number

            while ((bytesRead = fileInputStream.read(fileBuffer)) != -1) {
                packetCount++;
                // append sequence number to message
                String message = new String(fileBuffer, 0, bytesRead);
                byte[] messageBuffer = message.getBytes();

                // create a DatagramPacket with the buffer, the length of the data, and the destination address and port
                DatagramPacket packet = new DatagramPacket(messageBuffer, messageBuffer.length, serverAddress, PORT);

                // set the sequence number in the header of the packet
                packet.setData(String.valueOf(sequenceNumber).getBytes());

                int MAX_RETRIES = 3; // maximum number of retries
                int retryCount = 0; // retry counter
                boolean ackReceived = false;

                while (!ackReceived && retryCount < MAX_RETRIES) {
                    // send the packet
                    socket.send(packet);

                    // wait for an ACK from the server
                    byte[] ackBuffer = new byte[1024];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);

                    try {
                        socket.setSoTimeout(5000); // timeout 5 sec for retransmitting
                        socket.receive(ackPacket);

                        // extract the sequence number from the ACK packet
                        int ackSequenceNumber = Integer.parseInt(new String(ackPacket.getData(), 0, ackPacket.getLength()));

                        // check if the ACK sequence number matches the packet sequence number
                        if (ackSequenceNumber == sequenceNumber) {
                            ackReceived = true;
                            System.out.println("seq num: " + sequenceNumber);
                            System.out.println("ack received " + ackReceived);
                            sequenceNumber++; // increment sequence number
                        }
                    } catch (SocketTimeoutException e) {
                        retryCount++; // increment retry counter
                        System.out.println("Timeout waiting for ACK. Retrying packet...");
                        socket.send(packet); // resend the current packet
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                if (!ackReceived && retryCount == MAX_RETRIES) {
                    System.out.println("Max number of retries reached. Giving up on packet...");
                }
            }

            // send end of file marker to server
            String endMessage = "end";
            byte[] endBuffer = endMessage.getBytes();
            DatagramPacket endPacket = new DatagramPacket(endBuffer, endBuffer.length, serverAddress, PORT);
            socket.send(endPacket);


            // receive response from server indicating file upload success
            byte[] successBuffer = new byte[1024];
            DatagramPacket successPacket = new DatagramPacket(successBuffer, successBuffer.length);
            socket.receive(successPacket);
            String successResponse = new String(successPacket.getData(), 0, successPacket.getLength());
            if (successResponse.contains("File uploaded successfully")) {
                System.out.println("File uploaded successfully.");
            } else {
                System.err.println("Received unexpected response from server: " + successResponse);
            }

            System.out.println("File sent in " + packetCount + " packets.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void downloadFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in) {
        try {
            // send download request to server
            System.out.print("Enter name of file you want to download: ");
            String downloadMessage = "download " + in.readLine();
            byte[] downloadBuffer = downloadMessage.getBytes();
            DatagramPacket downloadPacket = new DatagramPacket(downloadBuffer, downloadBuffer.length, serverAddress, 9090);
            socket.send(downloadPacket);

            // receive response from server
            byte[] responseBuffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(responsePacket);
            String responseDownload = new String(responsePacket.getData(), 0, responsePacket.getLength());
            System.out.println("responseDownload" + responseDownload);
            if (responseDownload.contains("File not found")) {
                System.out.println("File not found on server.");
                return;
            } else if (responseDownload.contains("Ready to send file")) {
                System.out.println("Server is ready to send file.");
            } else {
                System.err.println("Received unexpected response from server: " + responseDownload);
                return;
            }

            // extract file name from server response
            String[] responseParts = responseDownload.split(" ");
            String fileName = responseParts[responseParts.length - 1];

            // receive file from server in packets and write to local file system
            String filePath = "src/main/java/com/nedap/university/download/" + fileName;
            System.out.println("filepath " + filePath);
            FileOutputStream fileOutputStream = new FileOutputStream(filePath);
            byte[] fileBuffer = new byte[1024];
            int bytesRead = 0;
            int packetCount = 0;
            while (true) {
                DatagramPacket filePacket = new DatagramPacket(fileBuffer, fileBuffer.length);
                socket.receive(filePacket);
                System.out.println("packet received");
                String packetData = new String(filePacket.getData(), 0, filePacket.getLength());
                if (packetData.equals("end")) {
                    System.out.println("received end packet");
                    System.out.println("file sent successfully");
                    break;
                }
                packetCount++;
                System.out.println("Packetcount: " + packetCount);
                fileOutputStream.write(filePacket.getData(), 0, filePacket.getLength());
                System.out.println("File received in " + packetCount + " packets and saved to " + filePath + ".");
            }
            fileOutputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void removeFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in) {
        try {
            // send remove request to server
            System.out.print("Enter name of file you want to remove: ");
            String fileName = in.readLine();
            String removeMessage = "remove " + fileName;

            byte[] removeBuffer = removeMessage.getBytes();
            DatagramPacket removePacket = new DatagramPacket(removeBuffer, removeBuffer.length, serverAddress, PORT);
            socket.send(removePacket);

            // receive response from server
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
    private static void replaceFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in) {
        try {
            // send replace request to server
            System.out.print("Enter name of file you want to replace: ");
            String fileName = in.readLine();
            String replaceMessage = "replace " + fileName;
            byte[] replaceBuffer = replaceMessage.getBytes();
            DatagramPacket replacePacket = new DatagramPacket(replaceBuffer, replaceBuffer.length, serverAddress, PORT);
            socket.send(replacePacket);

            // receive response from server
            byte[] responseBuffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(responsePacket);
            String responseReplace = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (responseReplace.contains("File not found")) {
                System.out.println("File not found on server.");
                return;
            } else if (responseReplace.contains("Ready to receive")) {
                System.out.println("upload function??");
            } else {
                System.err.println("Received unexpected response from server: " + responseReplace);
                return;
            }

            // read file from local file system and send to server in packets
            System.out.print("Enter path to new file: ");
            String filePath = in.readLine();
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("File not found.");
                return;
            }

            byte[] fileBuffer = new byte[1024];
            FileInputStream fileInputStream = new FileInputStream(file);
            int bytesRead = 0;
            int packetCount = 0;
            while ((bytesRead = fileInputStream.read(fileBuffer)) != -1) {
                packetCount++;
                DatagramPacket filePacket = new DatagramPacket(fileBuffer, bytesRead, serverAddress, PORT);
                socket.send(filePacket);
            }

            // send end of file marker to server
            String endMessage = "end";
            byte[] endBuffer = endMessage.getBytes();
            DatagramPacket endPacket = new DatagramPacket(endBuffer, endBuffer.length, serverAddress, PORT);
            socket.send(endPacket);

            // receive response from server indicating file replacement success
            byte[] successBuffer = new byte[1024];
            DatagramPacket successPacket = new DatagramPacket(successBuffer, successBuffer.length);
            socket.receive(successPacket);
            String successResponse = new String(successPacket.getData(), 0, successPacket.getLength());
            if (successResponse.contains("File replaced successfully")) {
                System.out.println("File replaced successfully.");
            } else {
                System.err.println("Received unexpected response from server: " + successResponse);
            }

            System.out.println("File sent in " + packetCount + " packets.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void showList(DatagramSocket socket, InetAddress serverAddress) {
        try {
            // send list request to server
            String listMessage = "list";
            byte[] listBuffer = listMessage.getBytes();
            DatagramPacket listPacket = new DatagramPacket(listBuffer, listBuffer.length, serverAddress, PORT);
            socket.send(listPacket);

            // receive list from server
            byte[] listBufferResponse = new byte[1024];
            DatagramPacket listPacketResponse = new DatagramPacket(listBufferResponse, listBufferResponse.length);
            socket.receive(listPacketResponse);
            String responseList = new String(listPacketResponse.getData(), 0, listPacketResponse.getLength());
            System.out.println("List of files on server:\n" + responseList);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void printMenu(){
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
