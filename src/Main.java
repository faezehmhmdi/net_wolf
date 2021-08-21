import java.io.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * Class Main includes creating peers, loading neighbours, handling the user inputs
 */
public class Main {
    static ArrayList<Peer> createdPeers = new ArrayList<>();
    static int numberOfPeers = 3;

    public static void main(String[] args) throws IOException, InterruptedException {
        /*
        Creating peers
         */
        for (int i = 0; i < numberOfPeers; i++) {
            Peer peer = new Peer("N" + (i + 1), InetAddress.getByName("127.0.0.1"), 2000 + i + 1, "./N" + (i + 1));
            createdPeers.add(peer);
        }
        /*
        Loading neighbours
         */
        for (int i = 0; i < numberOfPeers; i++) {
            File file = new File("./n" + (i + 1) + ".txt");
            if (!file.exists())
                continue;
            FileReader fileReader = new FileReader(file);
            Scanner scanner = new Scanner(fileReader);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String name = line.substring(0, line.indexOf(" "));
                String host = line.substring(line.indexOf(" "));
                createdPeers.get(i).addNeighbour(name, host);
            }
        }
//        for (int i = 0; i < createdPeers.size(); i++) {
//            System.out.println("N" + (i + 1) + " neighbours:");
//            System.out.println(createdPeers.get(i).discoveredPeers);
//        }

        /*
        Starting discovery thread
         */
        dis();
        Scanner sc = new Scanner(System.in);
        while (true) {
            String input = sc.nextLine();
            if (input.contains("get")) {
                String sub = input.replace("get ", "");
                String index = sub.substring(0, sub.indexOf(" "));
                String filename = sub.substring(sub.indexOf(" ") + 1);
                //wait for discovery
                Thread.sleep(4500L);
                createdPeers.get(Integer.parseInt(index) - 1).startShare(filename);
//                System.out.println("Now " + createdPeers.get(Integer.parseInt(index) - 1).getName() + " is requesting " + filename);

            }
            if (input.contains("ls")) {
                String index = input.substring(input.indexOf(" ") + 1);
                System.out.println(createdPeers.get(Integer.parseInt(index) - 1).discoveredPeers.keySet());

            }
            if (input.contains("create")) {
                Peer peer = new Peer("N" + (createdPeers.size() + 1), InetAddress.getByName("127.0.0.1"), 2000 + (createdPeers.size() + 1), "./N" + (createdPeers.size() + 1));
                File file = new File("./n" + (createdPeers.size() + 1) + ".txt");
                FileOutputStream fos = new FileOutputStream(file);
                String s = createdPeers.get(0).name + " " + createdPeers.get(0).inetAddress.getHostAddress() + ":" + createdPeers.get(0).port;
                byte[] b = s.getBytes();
                fos.write(b);
                fos.close();
                FileReader fileReader = new FileReader(file);
                Scanner scanner = new Scanner(fileReader);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    String name = line.substring(0, line.indexOf(" "));
                    String host = line.substring(line.indexOf(" "));
                    peer.addNeighbour(name, host);
                }
                createdPeers.add(peer);
                peer.startReceiver();
                peer.startDiscovery();
                System.out.println(createdPeers.size());
            }
        }
    }

    public static void dis() {
        for (int i = 0; i < numberOfPeers; i++) {
            createdPeers.get(i).startReceiver();
            createdPeers.get(i).startDiscovery();
        }
    }
}

