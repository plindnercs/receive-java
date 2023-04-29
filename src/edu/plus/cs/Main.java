package edu.plus.cs;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.out.println("Usage: program <path to dropoff folder> [port]");
            return;
        }

        File dropoffFolder = new File(args[0]);
        if (dropoffFolder.exists() && !dropoffFolder.isDirectory()) {
            System.out.println("File exists!");
            return;
        }
        dropoffFolder.mkdirs();

        int port = (args.length == 2) ? Integer.parseInt(args[1]) : 6969;

        new Receiver(dropoffFolder, port).start();
    }
}