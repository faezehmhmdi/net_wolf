import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Thread.sleep;

/**
 * Class Peer includes Receiving requests and sharing files.
 */

public class Peer {
    DatagramSocket socket;
    final HashMap<String, String> discoveredPeers = new HashMap<>(); // map from name -> ip:port
    final HashMap<String, Integer> requestPort = new HashMap<>(); // map from requested filename -> receiving port
    String name;
    int port;
    InetAddress inetAddress;
    boolean running;
    String directory;
    ArrayList<File> fileList = new ArrayList<>();
    private static final int CHUNK_SIZE = 2048;


    Peer(String name, InetAddress inetAddress, int port, String directory) throws SocketException {
        this.socket = new DatagramSocket(port, inetAddress);
        this.name = name;
        this.running = true;
        this.port = port;
        this.inetAddress = inetAddress;
        this.directory = directory;
        if (!new File(directory).exists()) {
            new File(directory).mkdir();
        }
        updateFileList();
    }

    public void startReceiver() {
        Thread thread = new Thread(() -> {
            byte[] buf = new byte[4096 * 16];
            DatagramPacket packet;
            String message, content, command;
            while (this.running) {
                packet = new DatagramPacket(buf, buf.length);
                try {
                    this.socket.receive(packet);
                    message = new String(packet.getData(), 0, packet.getLength());
                    //  System.out.println(message);
                    String[] command_content = message.split(" ", 2);
                    command = command_content[0];
                    content = command_content[1];
//                    System.out.println(content);
                    switch (command) {
                        case "Discovery":
                            // merge with our cluster
                            String[] nodes = content.split(" ");
                            for (int i = 0; i < nodes.length; i += 2) {
                                if (!nodes[i].equals(this.name)) { // if this is not us
                                    synchronized (this.discoveredPeers) {
                                        if (!this.discoveredPeers.containsKey(nodes[i])) {
//                                            System.out.println(this.name + " Found " + nodes[i] + " " + nodes[i + 1]);
                                            addNeighbour(nodes[i], nodes[i + 1]);
                                        }
                                    }
                                }
                            }
                            break;
                        case "Get":
                            for (File file : this.fileList) {
                                final var fileName = content.trim();
                                if (fileName.equals(file.getName())) {
//                                    System.out.println(this.name + " has " + fileName);
                                    int tcpPort = serverSock(file.getPath());
                                    // respond to requester
                                    byte[] resBuff = ("Response " + fileName + " " + tcpPort).getBytes();
                                    DatagramPacket response = new DatagramPacket(resBuff, resBuff.length, packet.getSocketAddress());
                                    this.socket.send(response);
                                    break;
                                }
                            }
                            break;
                        case "Response":
                            String[] namePort = content.split(" ");
                            this.requestPort.put(namePort[0], Integer.parseInt(namePort[1]));
                            synchronized (this.requestPort.get(namePort[0])) {
                                this.requestPort.get(namePort[0]).notifyAll();
                            }
                            break;
                        default:
                            System.err.println(message);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    public void startDiscovery() {
        Thread thread = new Thread(() -> {
            // Discovery N1 IP1:Port1 N2 IP2:Port2 ...
            String myName = "Discovery " + this.name + " " + this.inetAddress.getHostAddress() + ":" + this.port;
            StringBuilder message;
            byte[] buff;
            while (this.running) {
                message = new StringBuilder(myName + " ");
                synchronized (this.discoveredPeers) {
                    // get current neighbours
                    for (Map.Entry<String, String> neighbor_name_address : this.discoveredPeers.entrySet()) {
                        message.append(neighbor_name_address.getKey()).append(" ").append(neighbor_name_address.getValue()).append(" ");
                    }
                    // send discovery message to all
                    buff = message.toString().getBytes();
                    sendToNeighbors(buff);
                }
                try {
                    sleep(2000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            this.socket.close();
        });
        thread.start();
    }

    public void startShare(String filename) {
        Thread thread = new Thread(() -> {
            String message = "Get " + filename;
            byte[] buff = message.getBytes();
            synchronized (this.discoveredPeers) {
                sendToNeighbors(buff);
            }
            requestPort.put(filename, -1);
            try {
                synchronized (requestPort.get(filename)) {
                    requestPort.get(filename).wait(5000L); // wait 5 seconds as most
                }
                if (requestPort.get(filename) != -1) { // if a response is received
                    Socket socket = new Socket(inetAddress.getHostAddress(), requestPort.get(filename));
                    byte[] contents = new byte[CHUNK_SIZE];
                    FileOutputStream fos = new FileOutputStream(this.directory + "/" + filename);
                    BufferedOutputStream bos = new BufferedOutputStream(fos);
                    InputStream is = socket.getInputStream();
                    int bytesRead;
                    while ((bytesRead = is.read(contents)) != -1)
                        bos.write(contents, 0, bytesRead);
                    bos.flush();
                    socket.close();
                    System.out.println("File Received.");
                    updateFileList();
                } else {
                    System.out.println(filename + " not found.");
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();
    }

    private void sendToNeighbors(byte[] buff) {
        for (String neighbour_address : discoveredPeers.values()) {
            String[] address_port = neighbour_address.split(":");
            try {
                DatagramPacket req = new DatagramPacket(buff, buff.length, InetAddress.getByName(address_port[0]), Integer.parseInt(address_port[1]));
                this.socket.send(req);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public int serverSock(String filePath) throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        serverSocket.setSoTimeout(5000); // wait at most 5 seconds for acceptance
        Thread senderThread = new Thread(() -> {
            Socket socket;
            try {
                socket = serverSocket.accept();
                File file = new File(filePath);
                FileInputStream fis = new FileInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(fis);
                OutputStream os = socket.getOutputStream();
                byte[] contents;
                long fileLength = file.length();
                long current = 0;
                int chunkRead;
                contents = new byte[CHUNK_SIZE];
                while (current != fileLength) {
                    chunkRead = bis.read(contents);
                    os.write(contents, 0, chunkRead);
                    current += chunkRead;
                    System.out.println("Sending file ... " + (current * 100) / fileLength + " % ");
                }
                os.flush();
                socket.close();
                serverSocket.close();
//                System.out.println("File sent successfully!");
            } catch (SocketTimeoutException e) {
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        senderThread.start();
//        System.out.println(this.name + " Server Port " + serverSocket.getLocalPort());
        return serverSocket.getLocalPort();
    }


    public void addNeighbour(String name, String host) {
        discoveredPeers.put(name.trim(), host.trim());
    }

    public void updateFileList() {
        File dir = new File(this.directory);
        this.fileList.clear();
        final File[] files = dir.listFiles();
        if (files != null)
            Collections.addAll(this.fileList, files);
    }
}





