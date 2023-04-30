package edu.plus.cs.packet.sequence;

import edu.plus.cs.packet.Packet;

import java.util.ArrayList;
import java.util.List;

public class OpenPacketSequence {
    public long openedAt = System.currentTimeMillis();
    public List<Packet> cachedPackets = new ArrayList<>();
    public int nextSequenceNumber = 0;
}
