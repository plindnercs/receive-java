package edu.plus.cs;

import edu.plus.cs.packet.*;
import edu.plus.cs.packet.util.PacketInterpreter;
import edu.plus.cs.util.OperatingMode;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

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
    private int windowStart = 0;
    private int windowTimeout;
    private int duplicateAckDelay;
    private int retransmittedPackets = 0;
    private Predicate<List<Packet>> hasFinalizePacket =
            list -> list.stream().anyMatch(packet -> packet instanceof FinalizePacket);
    private BiPredicate<List<Packet>, Integer> hasPacketWithSequenceNumber =
            (list, sequenceNumber) -> list.stream().anyMatch(packet -> packet.getSequenceNumber() == sequenceNumber);


    public Receiver(short transmissionId, int port, File dropOffFolder, InetAddress ackIp, int ackPort,
                    OperatingMode operatingMode, int windowSize, int windowTimeout, int duplicateAckDelay) {
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
        this.windowTimeout = windowTimeout;
        if (operatingMode == OperatingMode.SLIDING_WINDOW) {
            this.windowBuffer = new ArrayList<>();
            this.missingPackets = new Stack<>();
        }
        this.duplicateAckDelay = duplicateAckDelay;
    }

    public void start() {
        byte[] buffer = new byte[65535];
        while (true) {
            DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);

            try {
                try {
                    socket.receive(udpPacket);
                } catch (SocketTimeoutException ex) {
                    // transmission of window is complete
                    if (operatingMode == OperatingMode.SLIDING_WINDOW) {
                        // check if window is ready for processing
                        if (missingPackets.isEmpty() // process window only if there are no missing packets
                                && (windowBuffer.size() == windowSize // check if window is completely filled
                                || hasFinalizePacket.test(windowBuffer) // or if it is the last window
                                    && windowBuffer.size() == transmissions.get(transmissionId) - windowStart + 2)) {
                            processWindow();
                        } else {
                            // check for missing packets inside the window
                            checkForMissingPackets();

                            if (!missingPackets.isEmpty()) {
                                // request a lost packet
                                requestMissingPacket(missingPackets.pop());
                            }
                        }

                        continue;
                    } else {
                        // this should not happen, since we only set a timeout on the socket
                        // if the SLIDING_WINDOW operating mode is set
                        System.err.println("Socket timed out!");
                    }
                }

                // parse packet
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
                    // recognize that the transmitter is finished with sending a window
                    if (operatingMode == OperatingMode.SLIDING_WINDOW) {
                        socket.setSoTimeout(windowTimeout);
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
                } else { // SLIDING_WINDOW
                    windowBuffer.add(packet);
                }
            } catch (Exception e) {
                System.err.println(e);
                System.out.println("Discarded packet with content [" +
                        new String(udpPacket.getData(), 0, udpPacket.getLength()) + "]");
            }
        }
    }

    private void processWindow() throws IOException {
        // System.out.println(windowBuffer.size());

        // choose last packet from window for acknowledgement
        windowBuffer.sort(Comparator.comparing(Packet::getSequenceNumber));

        Packet lastPacket = windowBuffer.get(windowBuffer.size() - 1);

        // System.out.println("Sent cumulative acknowledgement for: " + lastPacket.getSequenceNumber());
        sendAcknowledgementPacket(transmissionId, lastPacket.getSequenceNumber());

        // handle packets like we do for all operating modes
        for (Packet windowPacket : windowBuffer) {
            if (!digest.handlePacket(transmissionId, windowPacket)) {
                System.err.println("Error while handling packet: " + windowPacket);
            }
        }

        // look at next window
        windowStart += windowSize;

        if (windowBuffer.stream().anyMatch(packet -> packet instanceof FinalizePacket)) {
            System.out.println("Number of retransmitted packets: " + retransmittedPackets);
            socket.setSoTimeout(0); // reset timeout since no transmission is active
            windowStart = 0;
            retransmittedPackets = 0;
        }

        windowBuffer.clear();
    }

    private void checkForMissingPackets() {
        // first we check if the window is not complete
        if (windowBuffer.size() != windowSize) {
            windowBuffer.sort(Comparator.comparing(Packet::getSequenceNumber));

            // System.out.println(windowBuffer.size());

            // add missing sequence numbers such that the window is full
            int range;
            if (windowStart + windowSize > transmissions.get(transmissionId)) { // last window as range
                range = transmissions.get(transmissionId) + 2;
            } else {
                range = windowStart + windowSize; // there is still another full window
            }

            for (int i = windowStart; i < range; i++) {
                if (!hasPacketWithSequenceNumber.test(windowBuffer, i)
                        && missingPackets.search(i) == -1) {
                    missingPackets.push(i);
                }
            }
        }
    }

    private void requestMissingPacket(int sequenceNumber) throws IOException, InterruptedException {
        // System.out.println("Requesting lost packet: " + sequenceNumber);

        sendAcknowledgementPacket(transmissionId, sequenceNumber);
        TimeUnit.MILLISECONDS.sleep(duplicateAckDelay); // delay to avoid flooding the transmitter
        sendAcknowledgementPacket(transmissionId, sequenceNumber);
        TimeUnit.MILLISECONDS.sleep(duplicateAckDelay); // delay to avoid flooding the transmitter

        retransmittedPackets++;
    }

    private void sendAcknowledgementPacket(short transmissionId, int sequenceNumber) throws IOException {
        AcknowledgementPacket ackPacket = new AcknowledgementPacket(transmissionId, sequenceNumber);

        byte[] bytes = ackPacket.serialize();

        // System.out.println("Sent acknowledgement for Packet: " + sequenceNumber);

        DatagramPacket udpPacket = new DatagramPacket(bytes, bytes.length, this.ackIp, this.ackPort);
        this.socket.send(udpPacket);
    }

    private void printPacket(Packet packet) {
        System.out.println("Received packet: ");
        System.out.println(packet);
    }
}
