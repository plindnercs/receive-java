package edu.plus.cs;

import edu.plus.cs.packet.Packet;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PacketSequencer {

    private int maxOpenSequences;
    private int maxCachedPacketsPerSequence;
    private BiFunction<Integer, Packet, Boolean> continueSequence;
    private Consumer<Integer> cancelSequence;

    private class OpenPacketSequence {
        public long openedAt = System.currentTimeMillis();
        public List<Packet> cachedPackets = new ArrayList<>();
        public int nextSeqNr = 0;
    }

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

            if (packet.getSequenceNumber() != sequence.nextSeqNr) {
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
                    sequence.nextSeqNr++;

                    List<Packet> cache = sequence.cachedPackets;
                    if (cache.size() > 0) {
                        cache.sort((p1, p2) -> Integer.compare(p1.getSequenceNumber(), p2.getSequenceNumber()));

                        while (cache.size() > 0 && cache.get(0).getSequenceNumber() == sequence.nextSeqNr) {
                            Packet nextPacket = cache.remove(0);
                            boolean continueSequenceNow = this.continueSequence.apply(transmissionId, nextPacket);

                            if (!continueSequenceNow) {
                                cache.clear();
                                openSequences.remove(transmissionId);
                            } else {
                                sequence.nextSeqNr++;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            cancelSequence.accept(transmissionId);
        }

        while (openSequences.size() > maxOpenSequences) {
            openSequences.remove(openSequences.entrySet().stream().min((e1, e2) -> Long.compare(e1.getValue().openedAt, e2.getValue().openedAt)).get().getKey());
        }
    }
}