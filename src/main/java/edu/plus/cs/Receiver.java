package edu.plus.cs;

import edu.plus.cs.packet.*;
import edu.plus.cs.sequence.PacketSequencer;
import edu.plus.cs.packet.util.PacketInterpreter;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;

public class Receiver {
    private DatagramSocket socket;
    private PacketDigest digest;
    private PacketSequencer sequencer;
    private HashMap<Short, Integer> transmissions = new HashMap<>();

    private short transmissionId;

    public Receiver(short transmissionId, int port, File dropOffFolder) {
        this.transmissionId = transmissionId;
        digest = new PacketDigest(dropOffFolder);
        sequencer = new PacketSequencer(128, 1024, digest::continueSequence, digest::cancelSequence);
        try {
            socket = new DatagramSocket(port);
            socket.setBroadcast(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() {
        byte[] buffer = new byte[65535];
        while (true) {
            DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(udpPacket);

                Packet packet;
                int maxSequenceNumber;
                int sequenceNumber = PacketInterpreter.getSequenceNumber(udpPacket.getData());
                short transmissionId = PacketInterpreter.getTransmissionId(udpPacket.getData());

                if (PacketInterpreter.isInitializationPacket(udpPacket)) {
                    packet = new InitializePacket(udpPacket.getData(), udpPacket.getLength());

                    maxSequenceNumber = ((InitializePacket) packet).getMaxSequenceNumber();

                    transmissions.put(transmissionId, maxSequenceNumber);

                    System.out.println("Received initialization packet at: " + System.currentTimeMillis());
                } else {
                    // no initialization packet seen before for this transmissionId -> abort transmission
                    if (!transmissions.containsKey(transmissionId)) {
                        System.err.println("Did not receive initialization packet before, abort transmission");
                        break;
                    }

                    // check for finalize or data packet
                    if (sequenceNumber == (transmissions.get(transmissionId) + 1)) {
                        packet = new FinalizePacket(udpPacket.getData(), udpPacket.getLength());

                        System.out.println("Received finalize packet at: " + System.currentTimeMillis());
                    } else {
                        packet = new DataPacket(udpPacket.getData(), udpPacket.getLength());
                    }
                }

                // printPacket(packet);

                sequencer.push(packet, transmissionId);
            } catch (Exception e) {
                System.err.println(e);
                System.out.println("Discarded packet with content [" +
                        new String(udpPacket.getData(), 0, udpPacket.getLength()) + "]");
            }
        }
    }

    private void printPacket(Packet packet) {
        System.out.println("Received packet: ");
        System.out.println(packet);
    }
}
