package edu.plus.cs.sequence;

import edu.plus.cs.packet.Packet;

import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PacketSequencer {

    private int maxOpenSequences;
    private int maxCachedPacketsPerSequence;
    private BiFunction<Integer, Packet, Boolean> continueSequence;
    private Consumer<Integer> cancelSequence;

    private Map<Integer, OpenPacketSequence> openSequences = new HashMap<>();

    public PacketSequencer(int maxOpenSequences, int maxCachedPacketsPerSequence,
                           BiFunction<Integer, Packet, Boolean> continueSequence, Consumer<Integer> cancelSequence) {
        this.maxOpenSequences = maxOpenSequences;
        this.maxCachedPacketsPerSequence = maxCachedPacketsPerSequence;
        this.continueSequence = continueSequence;
        this.cancelSequence = cancelSequence;
    }

    public void push(Packet packet, int transmissionId) {
        try {
            OpenPacketSequence sequence = openSequences.computeIfAbsent(transmissionId, k -> new OpenPacketSequence());

            // check if packet did not arrive in expected (correct) order
            if (packet.getSequenceNumber() != sequence.nextSequenceNumber) {
                // add it to the cache to process it later on
                sequence.cachedPackets.add(packet);

                // check if there are too many cached packets already
                if (sequence.cachedPackets.size() > maxCachedPacketsPerSequence) {
                    // remove it from the open sequences
                    openSequences.remove(transmissionId);

                    // removes entry from openFiles in PacketDigest
                    cancelSequence.accept(transmissionId);
                }
            } else {
                // handles packet via PacketDigest and decides if the transmit sequence will be continued
                boolean continueSequence = this.continueSequence.apply(transmissionId, packet);

                if (!continueSequence) {
                    // done with processing this sequence, remove it from the open sequences
                    openSequences.remove(transmissionId);
                } else {
                    sequence.nextSequenceNumber++;

                    List<Packet> cache = sequence.cachedPackets;
                    // check if there are some packets which were received in a wrong order
                    if (cache.size() > 0) {
                        // keep correct order of the incoming packets
                        cache.sort(Comparator.comparingInt(Packet::getSequenceNumber));

                        while (cache.size() > 0 && cache.get(0).getSequenceNumber() == sequence.nextSequenceNumber) {
                            Packet nextPacket = cache.remove(0);
                            boolean continueSequenceNow = this.continueSequence.apply(transmissionId, nextPacket);

                            // done with processing this sequence, remove it from the open sequences & clear cache
                            if (!continueSequenceNow) {
                                cache.clear();
                                openSequences.remove(transmissionId);
                            } else {
                                sequence.nextSequenceNumber++;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // removes entry from openFiles in PacketDigest
            cancelSequence.accept(transmissionId);
        }

        // if maximum of open sequences is reached, remove the oldest ones
        while (openSequences.size() > maxOpenSequences) {
            openSequences.remove(openSequences.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().openedAt)).get().getKey());
        }
    }
}