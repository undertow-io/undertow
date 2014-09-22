package io.undertow.protocols.http2;

import java.nio.ByteBuffer;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Stuart Douglas
 */
public class HpackEncoderUnitTestCase {

    @Test
    public void testPushBits() {
        int pos = 0;
        byte[] data = new byte[10];
        ByteBuffer bb = ByteBuffer.wrap(data);
        pos = HpackEncoder.pushBits(bb, 0b11, 2, pos);
        pos = HpackEncoder.pushBits(bb, 0b10, 3, pos);
        pos = HpackEncoder.pushBits(bb, 0b1011010, 8, pos);
        pos = HpackEncoder.pushBits(bb, 0b10110101011010, 15, pos);
        pos = HpackEncoder.pushBits(bb, 0b1011, 4, pos);

        Assert.assertEquals((byte)0b11010010, data[0]);
        Assert.assertEquals((byte)0b11010010, data[1]);
        Assert.assertEquals((byte)0b11010101, data[2]);
        Assert.assertEquals((byte)0b10101011, data[3]);

    }
}
