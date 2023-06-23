package edu.plus.cs;

import edu.plus.cs.packet.*;
import edu.plus.cs.packet.util.PacketInterpreter;
import edu.plus.cs.util.OperatingMode;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

public class Receiver {
    private DatagramSocket socket;
    private PacketDigest digest;
    private HashMap<Short, Integer> transmissions = new HashMap<>();
    private short transmissionId;
    private InetAddress ackIp;
    private int ackPort;
    private OperatingMode operatingMode;
    private int windowSize;
    private List<Packet> windowBuffer;
    private Stack<Integer> missingPackets;

    public Receiver(short transmissionId, int port, File dropOffFolder, InetAddress ackIp, int ackPort,
                    OperatingMode operatingMode, int windowSize) {
        this.transmissionId = transmissionId;
        digest = new PacketDigest(dropOffFolder);
        try {
            socket = new DatagramSocket(port);
            socket.setBroadcast(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.ackIp = ackIp;
        this.ackPort = ackPort;
        this.operatingMode = operatingMode;
        this.windowSize = windowSize;
        if (operatingMode == OperatingMode.SLIDING_WINDOW) {
            this.windowBuffer = new ArrayList<>();
            this.missingPackets = new Stack<>();
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

                if (this.transmissionId != transmissionId) {
                    System.err.println("Received packet with wrong transmissionId, abort transmission");
                    break;
                }

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

                if (digest.handlePacket(transmissionId, packet)) {
                    // only send acknowledgement if the operating mode requires to
                    if (operatingMode == OperatingMode.STOP_WAIT) {
                        sendAcknowledgementPacket(transmissionId, packet.getSequenceNumber());
                    }
                } else {
                    System.err.println("Error while handling packet: " + packet);
                }
            } catch (Exception e) {
                System.err.println(e);
                System.out.println("Discarded packet with content [" +
                        new String(udpPacket.getData(), 0, udpPacket.getLength()) + "]");
            }
        }
    }

    private void sendAcknowledgementPacket(short transmissionId, int sequenceNumber) throws IOException {
        AcknowledgementPacket ackPacket = new AcknowledgementPacket(transmissionId, sequenceNumber);

        byte[] bytes = ackPacket.serialize();

        DatagramPacket udpPacket = new DatagramPacket(bytes, bytes.length, this.ackIp, this.ackPort);
        this.socket.send(udpPacket);
    }

    private void printPacket(Packet packet) {
        System.out.println("Received packet: ");
        System.out.println(packet);
    }
}
