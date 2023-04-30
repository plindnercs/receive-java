package edu.plus.cs.packet;

import edu.plus.cs.packet.util.PacketInterpreter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public abstract class Packet implements Serializable {
    short transmissionId; // 16 bit
    int sequenceNumber; // 32 bit

    public static final int HEADER_SIZE = 6; // transmissionId + sequence number + packet body identifier

    public Packet(short transmissionId, int sequenceNumber) {
        this.transmissionId = transmissionId;
        this.sequenceNumber = sequenceNumber;
    }

    public Packet(byte[] data) {
        this(PacketInterpreter.getTransmissionId(data), PacketInterpreter.getSequenceNumber(data));
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public byte[] serialize() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(HEADER_SIZE);
        byteBuffer.putShort(transmissionId);
        byteBuffer.putInt(sequenceNumber);
        return byteBuffer.array();
    }

    @Override
    public String toString() {
        return "Packet{" +
                "transmissionId=" + transmissionId +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}
