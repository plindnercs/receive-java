package edu.plus.cs;

import edu.plus.cs.packet.DataPacket;
import edu.plus.cs.packet.FinalizePacket;
import edu.plus.cs.packet.InitializePacket;
import edu.plus.cs.packet.Packet;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

public class PacketDigest {
    private final File dropoffFolder;
    private final HashMap<Integer, FileReference> openFiles = new HashMap<>();

    public PacketDigest(File dropoffFolder) {
        this.dropoffFolder = dropoffFolder;
    }

    public boolean continueSequence(int transmissionId, Packet packet) {
        if (packet instanceof InitializePacket) {
            try {
                return handleInfoPacket(transmissionId, packet.getSequenceNumber(), ((InitializePacket) packet));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else if (packet instanceof DataPacket) {
            return handleDataPacket(transmissionId, packet.getSequenceNumber(), (DataPacket) packet);
        } else if (packet instanceof FinalizePacket) {
            return handleFinalizePacket(transmissionId, (FinalizePacket) packet);
        }
        return false;
    }

    private boolean handleInfoPacket(int transmissionId, long seqNr, InitializePacket initializePacket) throws FileNotFoundException {
        if (seqNr != 0) throw new RuntimeException("sequence number invalid");
        if (openFiles.get(transmissionId) != null) throw new RuntimeException("no such open file");

        File f = new File(dropoffFolder, new String(initializePacket.getFileName()));

        f.delete();
        FileOutputStream os = new FileOutputStream(f);

        openFiles.put(transmissionId, new FileReference(f, os));

        return true;
    }

    private boolean handleDataPacket(int transmissionId, long sequenceNumber, DataPacket dataPacket) {
        FileReference fileReference = openFiles.get(transmissionId);
        if (fileReference == null) throw new RuntimeException("no such open file");

        OutputStream os = fileReference.getOutputStream();

        try {
            os.write(dataPacket.getData());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private boolean handleFinalizePacket(int transmissionId, FinalizePacket finalizePacket) {
        FileReference fileReference = openFiles.get(transmissionId);
        if (fileReference == null) throw new RuntimeException("no such open file");

        OutputStream os = fileReference.getOutputStream();

        try {
            byte[] hashShould = finalizePacket.getMd5();
            byte[] hashActual = MessageDigest.getInstance("MD5").digest(Files.readAllBytes(fileReference.getFile().toPath()));

            if (Arrays.equals(hashShould, hashActual)) {
                System.out.println("File successfully transferred");
                try {
                    os.flush();
                    os.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("Hashes do not match! Keeping corrupted file anyway!");
                System.out.println("Should: " + String.format("%032x", new BigInteger(1, hashShould)));
                System.out.println("Actual: " + String.format("%032x", new BigInteger(1, hashActual)));
            }
        } catch (IOException | NoSuchAlgorithmException ex) {
            ex.printStackTrace();
        }

        return false;
    }

    public void cancelSequence(int transmissionId) {
        openFiles.remove(transmissionId);
    }
}
