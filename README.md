# Module 2 Final Assignment

###### Author
- Anniek Bisschop

## Overview
This is a network storage application.
- The client can upload and download files
- The client can allow you to remove or replace files.
- The client can list all available files
- There is support for files of any size and type.
- The user can control the client through a textual interface
- There is a hashing function be able to prove that the file you download from the server is exactly the same as the one on the server, and the other way around (data integrity)

## Summary
To transfer the files UDP is used with an implementation of the stop-and-wait ARQ protocol

## Setup
- JRE/JDK 17 installed has to be installed
    - Server class:
      - change the private static final String pathToDirectory to the desired path you want to store/read files
    - Client class:
      - change "localhost" in InetAddress serverAddress = InetAddress.getByName("localhost") to the desired IP address
      - create a download folder
      - change "pathToDirectory" in private static final String pathToDirectory = ""; to the desired path 
      
- If you have to login/configure the Pi, please scroll to Setup Pi

- Do a ./gradlew deploy
- When everything is finished, start the main in the client package
## Setup Pi

[setup.md](pi_setup/setup.md) contains the description on how to setup the Pi. Before reading any further, follow the steps in that guide to set it up correctly for this assignment.
If you run into problems with this step, immediately ask for help!

To connect to the Pi you can use any SSH client (`ssh pi@[IP address of pi]`).
The default credentials are:
 - Username: `pi`
 - Password: `raspberry` or your own password

You can shutdown the Pi with the following command:

- shutdown: `sudo shutdown -h now`





