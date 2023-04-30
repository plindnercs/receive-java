package edu.plus.cs.packet;

import edu.plus.cs.packet.util.PacketInterpreter;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class InitializePacket extends Packet {
    int maxSequenceNumber; // 32 bit
    char[] fileName; // [0 ... 2048] bit

    public InitializePacket(byte[] data, int len) {
        super(data);
        this.maxSequenceNumber = PacketInterpreter.getUIntAt(data, HEADER_SIZE);
        this.fileName = PacketInterpreter.getStringAt(data, HEADER_SIZE + 4, len - (HEADER_SIZE + 4)).toCharArray();
    }

    @Override
    public byte[] serialize() {
        byte[] header = super.serialize();
        ByteBuffer byteBuffer = ByteBuffer.allocate(4 + new String(fileName).getBytes().length + header.length);
        byteBuffer.put(header);
        byteBuffer.putInt(maxSequenceNumber);
        byteBuffer.put(new String(fileName).getBytes(StandardCharsets.UTF_8));
        return byteBuffer.array();
    }

    public int getMaxSequenceNumber() {
        return maxSequenceNumber;
    }

    public void setMaxSequenceNumber(int maxSequenceNumber) {
        this.maxSequenceNumber = maxSequenceNumber;
    }

    public char[] getFileName() {
        return fileName;
    }

    public void setFileName(char[] fileName) {
        this.fileName = fileName;
    }

    @Override
    public String toString() {
        return "InitializePacket{" +
                "maxSequenceNumber=" + maxSequenceNumber +
                ", fileName=" + Arrays.toString(fileName) +
                ", transmissionId=" + transmissionId +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}
