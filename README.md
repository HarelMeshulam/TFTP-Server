<h1 align="center">Extended TFTP (Trivial File Transfer Protocol) Server and Client</h1>
<h2 align="left">Overview</h2>
This project implements an extended version of the Trivial File Transfer Protocol (TFTP), allowing multiple users to upload and download files from the server. The server and client communicate using a binary communication protocol over TCP. The server supports various commands for file operations such as upload, download, delete, and directory listing. Additionally, it facilitates message passing between clients and broadcasts announcements to all connected clients.

<h2 align="left">Features</h2>
<ul>
  <li>Thread-Per-Client (TPC) server architecture.</li>
  <li>Support for login, file upload, download, delete, and directory listing.</li>
  <li>Bidirectional message passing between clients.</li>
  <li>Broadcasting announcements to all connected clients.</li>
  <li>Error handling for various scenarios.</li>
</ul>

<h2 align="left">Project Structure</h2>
<ul>
  <li>Server: Contains the implementation of the TFTP server.</li>
  <li>Client: Contains the implementation of the TFTP client.</li>
  <li>Common: Contains common utilities and classes shared between the server and client, including the TFTP encoder/decoder.</li>
</ul>

<h2 align="left">Getting Started</h2>
<h3 align="left">Prerequisites</h3>

<ul>
  <li>Java JDK (11 or higher).</li>
  <li>Maven.</li>
</ul>



<h3 align="left">Building the Project</h3>
1. Clone this repository. <br />
2. Navigate to the root directory of the project. <br />
3. Run mvn compile to compile the project. <br />

<h3 align="left">Running the Server</h3>
1. Navigate to the server directory. <br />
2. Execute the following command: <br />
<code>mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpServer" -Dexec.args="&ltport>"</code> <br />
Replace <port> with the desired port number for the server.


<h3 align="left">Running the Client</h3>
1. Navigate to the client directory. <br />
2. Execute the following command: <br />
<code>mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpClient" -Dexec.args="&ltip>&ltport>"</code> <br />
Replace <ip> with the IP address of the server and <port> with the port number of the server.

<h2 align="left">Implementation Details</h2>
<ul>
  <li>The server uses the Thread-Per-Client (TPC) pattern to handle client connections.</li>
  <li>Communication between the server and client is based on a binary protocol.</li>
  <li>Error handling is implemented for various scenarios, ensuring robustness and reliability.</li>
</ul>
