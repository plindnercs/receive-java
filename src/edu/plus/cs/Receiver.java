package edu.plus.cs;

import edu.plus.cs.packet.DataPacketBody;
import edu.plus.cs.packet.FinalizePacketBody;
import edu.plus.cs.packet.InitializePacketBody;
import edu.plus.cs.packet.Packet;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Receiver {
    private DatagramSocket socket;
    private PacketDigest digest;
    private PacketSequencer sequencer;

    public Receiver(File dropOffFolder, int port) {
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
        byte[] buffer = new byte[65527];
        while (true) {
            DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(udpPacket);
                Packet packet = new Packet(udpPacket.getData(), udpPacket.getLength());
                if (packet.getSequenceNumber() % 1 == 0 || !(packet.getPacketBody() instanceof DataPacketBody)) {
                    //System.out.println("Received Packet " + packet);
                }
                if (packet.getPacketBody() instanceof InitializePacketBody) {
                    System.out.println("rec inf at " + System.currentTimeMillis());
                } else if (packet.getPacketBody() instanceof FinalizePacketBody) {
                    System.out.println("rec fin at " + System.currentTimeMillis());
                }
                int transmissionId = transmissionId(packet.getTransmissionId(), udpPacket.getAddress(), udpPacket.getPort());
                sequencer.push(packet, transmissionId);
            } catch (Exception e) {
                System.out.println("Discarded Packet with content [" +
                        new String(udpPacket.getData(), 0, udpPacket.getLength()) + "]");
            }
        }
    }

    private int transmissionId(short uid, InetAddress receiverAddress, int receiverPort) {
        return uid + receiverAddress.hashCode() + Integer.valueOf(receiverPort).hashCode();
    }
}
