package edu.plus.cs.packet;

import edu.plus.cs.packet.util.PacketInterpreter;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public class FinalizePacket extends Packet {
    private byte[] md5; // 128 bit

    public FinalizePacket(byte[] data, int len) {
        super(data);
        this.md5 = PacketInterpreter.getByteArrayAt(data, HEADER_SIZE, len - HEADER_SIZE);
    }

    @Override
    public byte[] serialize() {
        byte[] header = super.serialize();
        ByteBuffer byteBuffer = ByteBuffer.allocate(16);
        byteBuffer.put(header);
        byteBuffer.put(md5);
        return byteBuffer.array();
    }

    @Override
    public String toString() {
        return "FinalizePacket{" +
                "md5=" + String.format("%032x", new BigInteger(1, md5)) +
                ", transmissionId=" + transmissionId +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}
