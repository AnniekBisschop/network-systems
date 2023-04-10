package com.nedap.university.client;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

/*
* Datagram packets can be only bytes
* */

public class Client {
    public static void main(String[] args) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();

            // Get the IP address of the server we want to send data to
            InetAddress serverAddress = InetAddress.getByName("172.16.1.1");

            // send "Hello server" to server
            byte[] helloBuffer = "Hello server".getBytes();
            DatagramPacket helloPacket = new DatagramPacket(helloBuffer, helloBuffer.length, serverAddress, 9090);
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
                        showList(socket, serverAddress);
                        break;
                    case "3":
                        removeFile(socket, serverAddress, in);
                        break;
                    case "4":
                        System.out.println("Exiting program...");
                        // Add any additional cleanup or exit code here, if necessary.
                        System.exit(0);
                        break;
                    case "5":
                        downloadFile(socket, serverAddress, in);
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

    private static void removeFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in) {
        try {
            // send remove request to server
            System.out.print("Enter name of file you want to remove: ");
            String fileName = in.readLine();
            String removeMessage = "remove " + fileName;

            byte[] removeBuffer = removeMessage.getBytes();
            DatagramPacket removePacket = new DatagramPacket(removeBuffer, removeBuffer.length, serverAddress, 9090);
            socket.send(removePacket);

            // receive response from server
            byte[] responseBuffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(responsePacket);
            String responseRemove = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (responseRemove.equals("File removed successfully")) {
                System.out.println("File removed successfully");
            } else if (responseRemove.equals("File not found")) {
                System.out.println("File not found on server.");
            } else {
                System.err.println("Received unexpected response from server: " + responseRemove);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void showList(DatagramSocket socket, InetAddress serverAddress) {
        try {
            // send list request to server
            String listMessage = "list";
            byte[] listBuffer = listMessage.getBytes();
            DatagramPacket listPacket = new DatagramPacket(listBuffer, listBuffer.length, serverAddress, 9090);
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

    private static void uploadFile(DatagramSocket socket, InetAddress serverAddress, BufferedReader in) {
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
            DatagramPacket uploadPacket = new DatagramPacket(uploadBuffer, uploadBuffer.length, serverAddress, 9090);
            socket.send(uploadPacket);

            // receive response from server
            byte[] responseBuffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(responsePacket);
            String responseUpload = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (responseUpload.equals("Ready to receive file")) {
                System.out.println("Server is ready to receive file.");
            } else if (responseUpload.equals("File already exists")) {
                System.out.println("File already exists on server.");
                return;
            } else {
                System.err.println("Received unexpected response from server: " + responseUpload);
                return;
            }

            // read file from local file system and send to server in packets
            byte[] fileBuffer = new byte[1024];
            FileInputStream fileInputStream = new FileInputStream(file);
            int bytesRead = 0;
            int packetCount = 0;
            while ((bytesRead = fileInputStream.read(fileBuffer)) != -1) {
                packetCount++;
                DatagramPacket filePacket = new DatagramPacket(fileBuffer, bytesRead, serverAddress, 9090);
                socket.send(filePacket);
            }

            // send end of file marker to server
            String endMessage = "end";
            byte[] endBuffer = endMessage.getBytes();
            DatagramPacket endPacket = new DatagramPacket(endBuffer, endBuffer.length, serverAddress, 9090);
            socket.send(endPacket);

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

    public static void printMenu(){
        System.out.println("\nMenu Options:");
        System.out.println("1. Upload a file");
        System.out.println("2. Show list of files");
        System.out.println("3. Remove a file");
        System.out.println("4. Exit");
        System.out.println("5. Download a file");
    }
}
