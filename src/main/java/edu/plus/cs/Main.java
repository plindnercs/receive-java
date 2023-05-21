package edu.plus.cs;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Main {
    public static void main(String[] args) throws UnknownHostException {
        if (args.length != 5) {
            System.err.println("Usage: <transmissionId> <port> <targetFolder> <ackIp> <ackPort>");
            return;
        }

        short transmissionId = Short.parseShort(args[0]);
        int port = Integer.parseInt(args[1]);

        File targetFolder = new File(args[2]);
        if (targetFolder.exists() && !targetFolder.isDirectory()) {
            System.err.println("Target folder can't be a file");
            return;
        }
        targetFolder.mkdirs();

        String ackIp = args[3];
        int ackPort = Integer.parseInt(args[4]);

        new Receiver(transmissionId, port, targetFolder, InetAddress.getByName(ackIp), ackPort).start();
    }
}