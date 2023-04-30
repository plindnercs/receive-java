package edu.plus.cs.packet;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DataPacketBody extends PacketBody implements Serializable {
    byte[] data;

    public DataPacketBody(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        return "DataPacketBody{" +
                "data=" + new String(data, StandardCharsets.UTF_8) +
                '}';
    }
}
