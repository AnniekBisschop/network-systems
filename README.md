# Module 2 Final Assignment

###### Nedap University 7.0 2022–2024

###### Authors
- Stijn Wopereis
- Tessa Gerritse

###### Previous Authors
* NU 7.0:
    - Thomas Burghout
    - Hendrik van der Linde
* NU 6.0:
	- Jelte Zeilstra
	- Wout Slakhorst
* Original Authors:
	 - @silvanverschuur
 	- @erikhuizinga
 	- @stefan-dijkstra-nedap

## Introduction

Time flies, and we already arrived at the end of the second module of the Nedap University.
In the last few weeks you learned all the ins and outs about the fascinating subject of networking.
This assignment gives you the chance to combine your programming skills from the previous module with all this cool 
networking knowledge!

## Summary

During the module you learned about networks, layers and different protocols.
Where TCP guarantees reliable transfer of information, UDP does not.
For this assignment you will create a wireless storage medium, think of it like a simple NAS, with which you should achieve reliable file transfer using the UDP protocol.

The assignment should be performed individually.
Discussion about the assignment is allowed, but you should always be able to defend your programming and design choices.
You've got about 2 weeks to finish the assignment and demonstrate your final results.

To be able to demonstrate your skills, you will be provided with a Raspberry Pi 4 starter kit.
The Raspberry Pi will have to be installed with the Raspberry Pi OS Lite image (a minimal Linux distro without GUI). Other installation is done according to the [setup guide](pi_setup/setup.md).

The Wi-Fi network should be used to communicate between your laptop and the Raspberry Pis.

## Requirements

The application you will be making consists of two parts:

 0. a storage application (server) on the Raspberry Pi
 0. a client application which connects to the Pi, running from a laptop

##### Minimal requirements

 - You must be able to upload and download files from the client to the Raspberry Pi Server.
 - The client must allow you to remove or replace files.
 - Your applications support files of any size and type.
 - The user can control the client through a textual interface.
 - To keep it interesting, use UDP combined with an ARQ protocol. You are not allowed to use TCP/IP.
 - In case of an unreliable network, the file transfer should eventually be successful. The protocol must be fault-tolerant.
 - The client must be able to ask for and list all available files on the Raspberry Pi.
 - Any errors or failures must be handled gracefully.
 - You must be able to prove that the file you download from the server is exactly the same as the one on the server, and the other way around (data integrity).
 - *Have fun!*

##### Extra requirements
 - Your client should be able to show statistics about total transmission time, download speed, packet loss, retransmissions, etc.
 - Your client should be able to discover the server on a local network without knowing its IP address.
 - You should be able to pause and resume downloads at any time.
 - The server should be able to transfer several files at the same time. Under what circumstances can this improve or deteriorate the total transfer speed / quality?


##### Stretch goals (bonus)
 - Compressed file transfer.
 Hint: transmission speeds can improve when using compression.
 However, strong compression algorithms might not be quick for some files, resulting in lower total throughput.
 - Mesh network support.
 Download a file from a Raspberry Pi out of range of the Wi-Fi from your laptop.
 Any (unknown) number of intermediate nodes should provide and maintain the connection.
 Hint: it is possible to simulate a Raspberry Pi out of range by blacklisting a Pi from your computer.
 - Encrypted file transfer.
 Prove this by transferring a text file and creating a Wireshark dump in which none of the text is visible, unless decrypted.
 Also show that decryption of this dump actually results in the original file.
 - Security.
 The server should not allow a client to just list any file on its disk.
 Make sure the client is 'sandboxed' on the file server.
 - Disk space management.
 Imagine up-/downloading multiple large files.
 At some point you might reach your client's or server's disk space limits.
 Manage this.
 - Server statistics: your server could keep track of stats per client, file, session, etc. in some human-readable format.

## Deliverables

 - During development you should work with version control in Git.
 - Keep your code in a repository via GitHub.
 - The repo must be accessible to the coaches.
 - Tag your final version on GitHub.
 - Pro tip: try working with branches and pull requests!
 - A `git clone` of your project on your final revision must be runnable out of the box
For example, a Gradle or Maven command that needs to be run is acceptable.
Missing dependencies, compilation errors, etc. are not!
Assume nothing about the coaches' machines, except that they have JRE/JDK 17 installed.
 - The project should have the following content and structure:
   - There is a README file in the project root with instructions how to install and start the applications (clients and servers). You can replace this file.
   - Think about which directories and files are necessary, and any pre-conditions that should be met for successful installation.
   - After reading this file, someone unfamiliar with the project should be able to install and execute the application and transfer files without any problem.
   Hint: to verify your README contains all the required steps, checkout your project into a new directory on a different client and server than your own and follow the instructions yourself.
 - Any necessary but non-standard predefined classes and libraries should be included in JARs.
 - Typical causes that make the installation and compilation procedure fail are names and paths or hardcoded URLs, so make sure to not use any.
 Test this before submitting your project.
 - A report and presentation, see below.

Email your deliverables to nu8coaches@nedap.com, i.e. the report and the link to the final version on GitHub.

### Report

This assignment is a lot of work, therefore we do not want you to focus too much on the meta part of the process.
You do have to write a report.
The purpose of your report should be to explain the protocol you designed and how you got to the values of the several parameters.
The report should also include the baseline statistics for transferring the files listed in `example_files`. The statistics should include total transmission time, packet loss, retransmissions for both up- and download. You're allowed to use the best setup possible (distance, absence of walls, etc).

## Presentation & Tournament

On Friday the 21st of April 2023 you'll present your report to the coaches and other candidates and we will host a tournament!

In your presentation, focus on the protocol and other cool technicalities.
You'll get 10 minutes for the presentation and 5 minutes for questions.

Afterwards, you and your applications will compete against the other competitors' applications.
The competitor that can transmit a large file without errors in unstable network conditions, which will be provided by the coaches, in the least amount of time wins! Unstable conditions will be artificial.
There will be a grand prize for the winner.

## Evaluation

The minimal requirements as outlined in this report are required to achieve a positive evaluation that will let you pass the project.
If your implementation exceeds the minimum requirements the evaluation will be even better!
The coaches will, together, review your applications, report, presentation and tournament results on the final tournament day.

### Criteria

The list of criteria below will be used to evaluate your project.
Use it to keep track of and judge your own progress.

##### Code

 - The application has all the required components and functionality.
 - There is a README file with installation and execution instructions.
 - The application compiles and executes without errors.
 - The application has been sufficiently documented with Javadoc.
 - The implementation of large and/or complex methods has been documented internally.
 - The application layout/design is understandable and accessible.
 - Bytecode of predefined, external, non-standard Java classes is submitted with the code (jars), or as Gradle dependencies.
 - Dependencies should be clearly motivated in your report.

##### Programming Style

- Names of classes, variables and methods are well-chosen, understandable and possibly self-explanatory.
- All code is efficiently and neatly implemented.
- The program is easily maintainable.
 Signs of high maintainability include, but are not limited to:
   - Abstraction
   - Correct use of constants
   - Logical variable names
   - No magic numbers
   - No code smells
 - Exception mechanisms are used appropriately.
 - Concurrency constructs are used properly.

##### Testing

 - Appropriate unit tests are provided.
 - Appropriate system tests are provided.
 - Sufficient test coverage is reached.
 - Tests are well documented, or better: the tests are self-documenting (a testable code base with good tests can be self-documenting).
 - All classes in the system have been tested by unit testing.
 - All tests pass.

---

# Raspberry Pi Usage Guide

## Image

You will be provided with a Raspberry Pi 4 starter kit.
[setup.md](pi_setup/setup.md) contains the description on how to setup the Pi. Before reading any further, follow the steps in that guide to set it up correctly for this assignment.
If you run into problems with this step, immediately ask for help!

To connect to the Pi you can use any SSH client (`ssh pi@[IP address of pi]`).
The default credentials are:
 - Username: `pi`
 - Password: `raspberry`

It's recommended to shutdown the Pi before disconnecting power
Just unplugging power may cause a corrupted filesystem.
You can shutdown the Pi with the following command:

`sudo shutdown -h now`

## Networking

Besides the physical ethernet port, the Raspberry Pi is configured to start a Wi-Fi Access point on startup.
This network can be used to connect the Pi to your laptop without the need of switches/cables.

**Important:** you may need to disable the Nedap security policy of you computer's VPN client.

### Physical ethernet

The physical adapter is configured to use DHCP to obtain an IP address.

### Wi-Fi

The name of the Wi-Fi network is unique for each participant.
The raspberry Pi is configured to start a DHCP server on the Wi-Fi interface.
Your computer will automatically get an IP address from the Pi (range 172.16.1.2–172.16.1.100).

 - SSID: `nu-pi-$name`
 - Security: `WPA2`
 - Wi-Fi password: `nedap1234`
 - Static IP address: `172.16.1.1`

We recommend you to change the Wi-Fi password once you're connected to the Pi. This can be done by editing the following file:

`sudo nano /etc/hostapd/hostapd.conf`

Replace the password on the following line:

`wpa_passphrase=nedap1234`

## Java project setup

The `nedap/nu-module-2` repo on GitHub contains a sample setup of a Gradle project in the repo root.
It contains a task to automatically send a JAR to the Raspberry Pi over SSH: `./gradlew deploy` (on unix/linux systems).
You can get the status of your deployed application from the Pi using the `status` task.


Also see `pi_setup/network_hacking.md` for instructions on packet loss and network delays.
