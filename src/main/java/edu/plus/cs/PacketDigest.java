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
    private final File targetFolder;
    private final HashMap<Short, FileReference> openFiles = new HashMap<>();

    public PacketDigest(File targetFolder) {
        this.targetFolder = targetFolder;
    }

    public boolean continueSequence(short transmissionId, Packet packet) {
        if (packet instanceof InitializePacket) {
            try {
                return handleInitializePacket(transmissionId, packet.getSequenceNumber(), ((InitializePacket) packet));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else if (packet instanceof DataPacket) {
            return handleDataPacket(transmissionId, (DataPacket) packet);
        } else if (packet instanceof FinalizePacket) {
            return handleFinalizePacket(transmissionId, (FinalizePacket) packet);
        }
        return false;
    }

    private boolean handleInitializePacket(short transmissionId, int sequenceNumber, InitializePacket initializePacket) throws FileNotFoundException {
        if (sequenceNumber != 0) throw new RuntimeException("Sequence number invalid");
        if (openFiles.get(transmissionId) != null) throw new RuntimeException("No such open file");

        File f = new File(targetFolder, new String(initializePacket.getFileName()));
        f.delete();

        FileOutputStream os = new FileOutputStream(f);

        openFiles.put(transmissionId, new FileReference(f, os));

        return true;
    }

    private boolean handleDataPacket(short transmissionId, DataPacket dataPacket) {
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

    private boolean handleFinalizePacket(short transmissionId, FinalizePacket finalizePacket) {
        FileReference fileReference = openFiles.get(transmissionId);
        if (fileReference == null) throw new RuntimeException("no such open file");

        OutputStream os = fileReference.getOutputStream();

        try {
            byte[] hashShould = finalizePacket.getMd5();
            byte[] hashActual = MessageDigest.getInstance("MD5").digest(Files.readAllBytes(fileReference.getFile().toPath()));

            if (Arrays.equals(hashShould, hashActual)) {
                System.out.println("File " + fileReference.getFile().getName() + " successfully transferred");
                try {
                    os.flush();
                    os.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.err.println("Hashes are not equal");
                System.out.println("Should: " + String.format("%032x", new BigInteger(1, hashShould)));
                System.out.println("Actual: " + String.format("%032x", new BigInteger(1, hashActual)));
            }
        } catch (IOException | NoSuchAlgorithmException ex) {
            ex.printStackTrace();
        }

        openFiles.remove(transmissionId);

        return false;
    }

    public void cancelSequence(int transmissionId) {
        openFiles.remove(transmissionId);
    }
}
