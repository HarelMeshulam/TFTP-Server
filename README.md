<h1 align="center">Extended TFTP (Trivial File Transfer Protocol) Server and Client</h1>
<h2 align="left">Overview</h2>
This project implements an extended version of the Trivial File Transfer Protocol (TFTP), allowing multiple users to upload and download files from the server. The server and client communicate using a binary communication protocol over TCP. The server supports various commands for file operations such as upload, download, delete, and directory listing. Additionally, it facilitates message passing between clients and broadcasts announcements to all connected clients.

<h2 align="left">Features</h2>
- Thread-Per-Client (TPC) server architecture. <br />
- Support for login, file upload, download, delete, and directory listing. <br />
- Bidirectional message passing between clients. <br />
* Broadcasting announcements to all connected clients. <br />
* Error handling for various scenarios. <br />

<h2 align="left">Project Structure</h2>
Server: Contains the implementation of the TFTP server. <br />
Client: Contains the implementation of the TFTP client. <br />
Common: Contains common utilities and classes shared between the server and client, including the TFTP encoder/decoder. <br />
Examples: Provides examples of how to use the server and client. <br />

<h2 align="left">Getting Started</h2>
<h3 align="left">Prerequisites</h3>

Java JDK (11 or higher)
Maven

<h3 align="left">Building the Project</h3>
1. Clone this repository. <br />
2. Navigate to the root directory of the project. <br />
3. Run mvn compile to compile the project. <br />

<h3 align="left">Running the Server</h3>
1. Navigate to the server directory. <br />
2. Execute the following command: <br />
```
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpServer" -Dexec.args="<port>"
```
Replace <port> with the desired port number for the server.
Running the Client
Navigate to the client directory.
Execute the following command:
bash
Copy code
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpClient" -Dexec.args="<ip> <port>"
Replace <ip> with the IP address of the server and <port> with the port number of the server.
Implementation Details
The server uses the Thread-Per-Client (TPC) pattern to handle client connections.
Communication between the server and client is based on a binary protocol.
Error handling is implemented for various scenarios, ensuring robustness and reliability.
