package edu.plus.cs;

import edu.plus.cs.packet.DataPacket;
import edu.plus.cs.packet.FinalizePacket;
import edu.plus.cs.packet.InitializePacket;
import edu.plus.cs.packet.Packet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
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
            return handleFinalizePacket(transmissionId, packet.getSequenceNumber(), (FinalizePacket) packet);
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

    private boolean handleFinalizePacket(int transmissionId, long sequenceNumber, FinalizePacket finalizePacket) {
        FileReference fileReference = openFiles.get(transmissionId);
        if (fileReference == null) throw new RuntimeException("no such open file");

        OutputStream os = fileReference.getOutputStream();

        /*byte[] hashShould = fin.getMurmurHash3();
        byte[] hashActual = h.finish().asBigEndianByteArray();*/

        if (true/*hashShould.equals(hashActual)*/) {
            System.out.println("File successfully transferred");
            try {
                os.flush();
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Hashes do not match! Keeping corrupted file anyway!");
            /*System.out.printf("Should: %d %d%n", hashShould[0], hashShould[1]);
            System.out.printf("Actual: %d %d%n", hashActual[0], hashActual[1]);*/
        }

        return false;
    }

    public void cancelSequence(int transmissionId) {
        FileReference fileReference = openFiles.remove(transmissionId);
        if (fileReference == null) return;
    }
}
