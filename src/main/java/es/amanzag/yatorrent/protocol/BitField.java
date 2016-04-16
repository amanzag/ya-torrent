package es.amanzag.yatorrent.protocol;

public class BitField {
    
    private int numberOfPieces;
    private byte[] statuses;
    
    public BitField(int numberOfPieces) {
        this(numberOfPieces, new byte[numberOfPieces/8 + (numberOfPieces%8 > 0 ? 1 : 0)]);
    }
    
    public BitField(int numberOfPieces, byte[] statuses) {
        if(numberOfPieces > statuses.length * 8 || numberOfPieces < statuses.length*8-7) {
            throw new IllegalArgumentException("Incorrect bitfield length");
        }
        this.numberOfPieces = numberOfPieces;
        this.statuses = statuses;
    }
    
    public void add(BitField other) {
        if (statuses.length != other.statuses.length || numberOfPieces != other.numberOfPieces) {
            throw new IllegalArgumentException("Can't add a bitfield to another of different length");
        }
        for (int i = 0; i < statuses.length; i++) {
            statuses[i] = (byte) (statuses[i] | other.statuses[i]);
        }
    }
    
    public void setPresent(int position, boolean present) {
        int byteIndex = position / 8;
        int offset = position % 8;
        byte newByte = 0;
        if (present) {
            newByte = (byte) (statuses[byteIndex] | ((byte)0x01 << offset));
        } else {
            newByte = (byte) (statuses[byteIndex] & ~((byte)0x01 << offset));
        }
        statuses[byteIndex] = newByte;
    }
    
    public boolean isPresent(int position) {
        int byteIndex = position / 8;
        int offset = position % 8;
        return (statuses[byteIndex] & ((byte)0x01 << offset)) > 0;
    }
    
    public int getSize() {
        return numberOfPieces;
    }
    
    public boolean hasBitsSet() {
        for (int i=0; i<statuses.length-1; i++) {
            if(statuses[i] != 0) {
                return true;
            }
        }
        byte lastByte = statuses[statuses.length-1];
        int numberOfBitsInLast = numberOfPieces % 8;
        if(numberOfBitsInLast==0) {
            return lastByte != 0;
        }
        byte lastMask = (byte) ~(0xFF << numberOfBitsInLast);
        return (byte)(lastByte & lastMask) != 0;
    }
    
    public boolean hasBitsUnset() {
        for (int i=0; i<statuses.length-1; i++) {
            if(~statuses[i] != 0) {
                return true;
            }
        }
        byte lastByte = statuses[statuses.length-1];
        int numberOfBitsInLast = numberOfPieces % 8;
        if(numberOfBitsInLast==0) {
            return ~lastByte != 0;
        }
        byte lastMask = (byte) (0xFF << numberOfBitsInLast);
        return (byte)~(lastByte | lastMask) != 0;
    }
    
    public BitField intersection(BitField other) {
        if (this.numberOfPieces != other.numberOfPieces) {
            throw new IllegalArgumentException();
        }
        byte[] result = new byte[statuses.length];
        for (int i=0; i<this.statuses.length; i++) {
            result[i] = (byte) (this.statuses[i] & other.statuses[i]);
        }
        return new BitField(this.numberOfPieces, result);
    }

    public BitField reverse() {
        byte[] result = new byte[statuses.length];
        for (int i=0; i<this.statuses.length; i++) {
            result[i] = (byte) ~this.statuses[i];
        }
        return new BitField(this.numberOfPieces, result);
    }
    
    public byte[] asByteArray() {
        return statuses;
    }
}
