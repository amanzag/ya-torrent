package es.amanzag.yatorrent.protocol;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class BitFieldTest {

    @Test
    @Parameters({"0", "1", "7", "8", "15"})
    public void testSettingBits(int index) throws Exception {
        BitField bf = new BitField(16);
        assertThat(bf.isPresent(index), is(false));
        bf.setPresent(index, true);
        assertThat(bf.isPresent(index), is(true));
    }
    
    @Test
    @Parameters({"0", "1", "7", "8", "15"})
    public void testUnsettingBits(int index) throws Exception {
        BitField bf = new BitField(16, new byte[] { (byte) 0xff, (byte) 0xff});
        assertThat(bf.isPresent(index), is(true));
        bf.setPresent(index, false);
        assertThat(bf.isPresent(index), is(false));
    }
    
    @Test(expected=IllegalArgumentException.class)
    @Parameters({"0", "1", "7", "17", "23", "24"})
    public void testIllegalBitfield(int size) throws Exception {
        new BitField(size, new byte[] { (byte) 0xff, (byte) 0xff});
    }
    
    @Test
    public void testAdd() throws Exception {
        BitField bf = new BitField(16, new byte[] { (byte) 0xff, (byte) 0x00});
        BitField bf2 = new BitField(16, new byte[] { (byte) 0x00, (byte) 0xff});
        bf.add(bf2);
        for (int i = 0; i < 16; i++) {
            assertThat(bf.isPresent(i), is(true));
        }
    }
    
    @Test
    @Parameters({"7", "9", "2", "287"})
    public void testMissalignedBits(int bits) throws Exception {
        BitField bf = new BitField(bits);
        bf.setPresent(bits-1, true);
        assertThat(bf.isPresent(bits-1), is(true));
        assertThat(bf.isPresent(bits-2), is(false));
    }
    
    @Test
    public void testSetAndUnsetWithinSameByte() throws Exception {
        BitField bf = new BitField(18);
        bf.setPresent(2, true);
        bf.setPresent(4, false);
        assertThat(bf.isPresent(2), is(true));
        assertThat(bf.isPresent(4), is(false));
    }
    
}
