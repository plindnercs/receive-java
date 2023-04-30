package edu.plus.cs.packet;

import java.io.Serializable;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class FinalizePacketBody extends PacketBody implements Serializable {
    byte[] md5; // 128 bit

    public FinalizePacketBody(byte[] md5) {
        this.md5 = md5;
    }

    @Override
    public String toString() {
        return "FinalizePacketBody{" +
                "md5=" + String.format("%032x", new BigInteger(1, md5)) +
                '}';
    }
}
