package edu.plus.cs;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: <targetFolder> <port>");
            return;
        }

        File targetFolder = new File(args[0]);
        if (targetFolder.exists() && !targetFolder.isDirectory()) {
            System.err.println("Target folder can't be a file");
            return;
        }
        targetFolder.mkdirs();

        int port = Integer.parseInt(args[1]);

        new Receiver(targetFolder, port).start();
    }
}