package edu.plus.cs.packet;

public class AcknowledgementPacket extends Packet {
    public AcknowledgementPacket(short transmissionId, int sequenceNumber) {
        super(transmissionId, sequenceNumber);
    }

    public AcknowledgementPacket(byte[] data) {
        super(data);
    }

    @Override
    public byte[] serialize() {
        return super.serialize();
    }
}
