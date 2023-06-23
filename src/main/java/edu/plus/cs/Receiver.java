package edu.plus.cs;

import edu.plus.cs.packet.*;
import edu.plus.cs.packet.util.PacketInterpreter;
import edu.plus.cs.util.OperatingMode;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private int expectedPacket = 0;
    private boolean retryMode = false;
    private int windowStart = 0;

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
                try {
                    socket.receive(udpPacket);
                } catch (SocketTimeoutException ex) {
                    if (operatingMode == OperatingMode.SLIDING_WINDOW) {
                        // transmission of window is complete, now check for missing packets
                        // otherwise continue bulk processing
                        if (missingPackets.isEmpty() &&
                                (windowBuffer.size() == windowSize
                                        || (windowBuffer.stream().anyMatch(packet -> packet instanceof FinalizePacket)
                                && windowBuffer.size() == transmissions.get(transmissionId) - windowStart + 2))) {
                            retryMode = false;
                            // System.out.println(windowBuffer.size());
                            windowBuffer.sort(Comparator.comparing(Packet::getSequenceNumber));
                            Packet lastPacket = windowBuffer.get(windowBuffer.size() - 1);
                            sendAcknowledgementPacket(transmissionId, lastPacket.getSequenceNumber());

                            for (Packet windowPacket : windowBuffer) {
                                if (!digest.handlePacket(transmissionId, windowPacket)) {
                                    System.err.println("Error while handling packet: " + windowPacket);
                                }
                            }

                            // look at next window
                            windowStart += windowSize;

                            // next expected packet should be the first one of the next window
                            expectedPacket = windowStart;

                            if (windowBuffer.stream().anyMatch(packet -> packet instanceof FinalizePacket)) {
                                socket.setSoTimeout(60000);
                                expectedPacket = 0;
                                windowStart = 0;
                            }

                            windowBuffer.clear();
                        } else {
                            // first we check if the window is not complete
                            if (windowBuffer.size() != windowSize) {
                                windowBuffer.sort(Comparator.comparing(Packet::getSequenceNumber));

                                // System.out.println(windowBuffer.size());

                                // add missing sequence numbers such that the window is full
                                int range;
                                if (windowStart + windowSize > transmissions.get(transmissionId)) {
                                    range = transmissions.get(transmissionId);
                                } else {
                                    range = windowStart + windowSize;
                                }
                                for (int i = windowStart; i < range; i++) {
                                    if (!windowBufferHasPacket(windowBuffer, i)
                                            && missingPackets.search(i) == -1) {
                                        missingPackets.push(i);
                                    }
                                }
                            }

                            // we retry a lost packet
                            expectedPacket = missingPackets.pop();

                            System.out.println("Requesting lost packet: " + expectedPacket);

                            sendAcknowledgementPacket(transmissionId, expectedPacket);
                            sendAcknowledgementPacket(transmissionId, expectedPacket);

                            // TimeUnit.MILLISECONDS.sleep(20);

                            retryMode = true;
                        }

                        continue;
                    } else {
                        System.err.println("Socket timed out!");
                    }
                }

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

                    // set timeout for socket in sliding window mode, since we have to
                    // recognize once the transmitter is finished with sending a window
                    if (operatingMode == OperatingMode.SLIDING_WINDOW) {
                        socket.setSoTimeout(50);
                    }
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

                // process single packet
                if (operatingMode != OperatingMode.SLIDING_WINDOW) {
                    if (digest.handlePacket(transmissionId, packet)) {
                        // only send acknowledgement if the operating mode requires to
                        if (operatingMode == OperatingMode.STOP_WAIT) {
                            sendAcknowledgementPacket(transmissionId, packet.getSequenceNumber());
                        }
                    } else {
                        System.err.println("Error while handling packet: " + packet);
                    }
                } else {
                    windowBuffer.add(packet);

                    /*if (packet.getSequenceNumber() != expectedPacket) {
                        System.out.println("Expected packet: " + expectedPacket + " but got Packet: " +
                                packet.getSequenceNumber());
                        // missingPackets.push(expectedPacket);

                        expectedPacket++;
                    }

                    if (!retryMode) {
                        // we are still in correct order
                        expectedPacket++;
                    }*/
                }
            } catch (Exception e) {
                System.err.println(e);
                System.out.println("Discarded packet with content [" +
                        new String(udpPacket.getData(), 0, udpPacket.getLength()) + "]");
            }
        }
    }

    private boolean windowBufferHasPacket(List<Packet> windowBuffer, int sequenceNumber) {
        return windowBuffer.stream().anyMatch(windowPacket -> windowPacket.getSequenceNumber() == sequenceNumber);
    }

    private void sendAcknowledgementPacket(short transmissionId, int sequenceNumber) throws IOException {
        AcknowledgementPacket ackPacket = new AcknowledgementPacket(transmissionId, sequenceNumber);

        byte[] bytes = ackPacket.serialize();

        System.out.println("Sent acknowledgement for Packet: " + sequenceNumber);

        DatagramPacket udpPacket = new DatagramPacket(bytes, bytes.length, this.ackIp, this.ackPort);
        this.socket.send(udpPacket);
    }

    private void printPacket(Packet packet) {
        System.out.println("Received packet: ");
        System.out.println(packet);
    }
}
