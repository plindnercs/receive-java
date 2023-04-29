package edu.plus.cs.packet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Packet implements Serializable {
    short transmissionId; // 16 bit
    int sequenceNumber; // 32 bit
    PacketBody packetBody;

    public static final int HEADER_SIZE = 6;

    public Packet(short transmissionId, int sequenceNumber, PacketBody packetBody) {
        this.transmissionId = transmissionId;
        this.sequenceNumber = sequenceNumber;
        this.packetBody = packetBody;
    }

    public Packet(byte[] data, int len) throws Exception {
        this(
                (short) getUIntAt(data, 0),
                getUByteAt(data, 4),
                switch (getUByteAt(data, 5)) {
                    case (byte) 0x00 -> new InitializePacketBody(
                            (int) getULongAt(data, HEADER_SIZE),
                            getStringAt(data, HEADER_SIZE + 8, len - (HEADER_SIZE + 8)).toCharArray()
                    );
                    case (byte) 0x01 -> new DataPacketBody(
                            getByteArrayAt(data, HEADER_SIZE, len - HEADER_SIZE)
                    );
                    case (byte) 0xFF -> new FinalizePacketBody(
                            (new String(getByteArrayAt(data, HEADER_SIZE, len - HEADER_SIZE))).toCharArray()
                    );
                    default -> throw new Exception("unknown packet type");
                }
        );
    }

    private static int getUIntAt(byte[] data, int index) {
        return ((data[index] & 0xFF) << 24) | ((data[index + 1] & 0xFF) << 16) |
                ((data[index + 2] & 0xFF) << 8) | (data[index + 3] & 0xFF);
    }

    private static short getUByteAt(byte[] data, int index) {
        return (short) (data[index] & 0xFF);
    }

    private static long getULongAt(byte[] data, int index) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (data[index + i] & 0xFF);
        }
        return result;
    }

    private static String getStringAt(byte[] data, int index, int len) {
        return new String(data, index, len, StandardCharsets.UTF_8);
    }

    private static byte[] getByteArrayAt(byte[] data, int index, int len) {
        return Arrays.copyOfRange(data, index, index + len);
    }

    public Packet(byte[] data) throws Exception {
        this(data, data.length);
    }

    public PacketBody getPacketBody() {
        return packetBody;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public short getTransmissionId() {
        return transmissionId;
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeShort(transmissionId);
        oos.writeInt(sequenceNumber);
        oos.writeObject(packetBody);
        oos.flush();
        return bos.toByteArray();
    }

    @Override
    public String toString() {
        return "Packet{" +
                "transmissionId=" + transmissionId +
                ", sequenceNumber=" + sequenceNumber +
                ", packetBody=" + packetBody +
                '}';
    }
}
