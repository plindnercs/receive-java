package edu.plus.cs.packet.sequence;

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

            if (packet.getSequenceNumber() != sequence.nextSequenceNumber) {
                sequence.cachedPackets.add(packet);

                if (sequence.cachedPackets.size() > maxCachedPacketsPerSequence) {
                    openSequences.remove(transmissionId);
                    cancelSequence.accept(transmissionId);
                }
            } else {
                boolean continueSequence = this.continueSequence.apply(transmissionId, packet);
                if (!continueSequence) {
                    openSequences.remove(transmissionId);
                } else {
                    sequence.nextSequenceNumber++;

                    List<Packet> cache = sequence.cachedPackets;
                    if (cache.size() > 0) {
                        cache.sort(Comparator.comparingInt(Packet::getSequenceNumber));

                        while (cache.size() > 0 && cache.get(0).getSequenceNumber() == sequence.nextSequenceNumber) {
                            Packet nextPacket = cache.remove(0);
                            boolean continueSequenceNow = this.continueSequence.apply(transmissionId, nextPacket);

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
            cancelSequence.accept(transmissionId);
        }

        while (openSequences.size() > maxOpenSequences) {
            openSequences.remove(openSequences.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().openedAt)).get().getKey());
        }
    }
}