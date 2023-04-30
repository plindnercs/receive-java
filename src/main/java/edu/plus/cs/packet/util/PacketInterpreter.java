package edu.plus.cs.packet.util;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class PacketInterpreter {

    public static boolean isInitializationPacket(DatagramPacket udpPacket) {
        return getSequenceNumber(udpPacket.getData()) == 0;
    }

    public static int getSequenceNumber(byte[] data) {
        return ByteBuffer.wrap(data, 2,  4).getInt();
    }

    public static short getTransmissionId(byte[] data) {
        return ByteBuffer.wrap(data, 0, 2).getShort();
    }

    public static int getUIntAt(byte[] data, int index) {
        return ((data[index] & 0xFF) << 24) | ((data[index + 1] & 0xFF) << 16) |
                ((data[index + 2] & 0xFF) << 8) | (data[index + 3] & 0xFF);
    }

    public static String getStringAt(byte[] data, int index, int len) {
        return new String(data, index, len, StandardCharsets.UTF_8);
    }

    public static byte[] getByteArrayAt(byte[] data, int index, int len) {
        return Arrays.copyOfRange(data, index, index + len);
    }
}
