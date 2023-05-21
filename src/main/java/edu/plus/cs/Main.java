package edu.plus.cs;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: <transmissionId> <port> <targetFolder>");
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

        new Receiver(transmissionId, port, targetFolder).start();
    }
}