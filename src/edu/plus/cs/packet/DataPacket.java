package edu.plus.cs.packet;

import edu.plus.cs.packet.util.PacketInterpreter;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class DataPacket extends Packet {
    byte[] data;

    public DataPacket(short transmissionId, int sequenceNumber, byte[] data) {
        super(transmissionId, sequenceNumber);
        this.data = data;
    }

    public DataPacket(byte[] data, int len) {
        super(data);
        this.data = PacketInterpreter.getByteArrayAt(data, HEADER_SIZE, len - HEADER_SIZE);
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public byte[] serialize() {
        byte[] header = super.serialize();
        ByteBuffer byteBuffer = ByteBuffer.allocate(HEADER_SIZE + data.length);
        byteBuffer.put(header);
        byteBuffer.put(data);
        return byteBuffer.array();
    }

    @Override
    public String toString() {
        return "DataPacket{" +
                "data=" + Arrays.toString(data) +
                ", transmissionId=" + transmissionId +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}
