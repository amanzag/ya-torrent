package es.amanzag.yatorrent.protocol.messages;

import java.io.IOException;
import java.nio.ByteBuffer;

import es.amanzag.yatorrent.protocol.BitField;
import es.amanzag.yatorrent.protocol.BlockRequest;
import es.amanzag.yatorrent.protocol.TorrentProtocolException;
import es.amanzag.yatorrent.storage.Piece;

public class RawMessage {

    public static enum Type {
    	CHOKE(0), UNCHOKE(1), INTERESTED(2), NOT_INTERESTED(3), HAVE(4),
    	BITFIELD(5), REQUEST(6), PIECE(7), CANCEL(8), HANDSHAKE(-1), KEEP_ALIVE(-2);
    	
    	private byte id;
    	private Type(int id) {
    		this.id = (byte)id;
    	}
    	public byte getId() { return id; }
    }
    
    private Type type;
    private int length;
    private ByteBuffer rawData;
    
    public RawMessage(Type type, int length, ByteBuffer rawData) {
        super();
        this.type = type;
        this.length = length;
        this.rawData = rawData;
    }
    
    public Type getType() {
        return type;
    }
    
    public int getLength() {
        return length;
    }
    
    public ByteBuffer getRawData() {
        return rawData;
    }
    
    public static RawMessage.Type decodeMessageType(byte type) {
        if(type == RawMessage.Type.CHOKE.getId()) {
            return RawMessage.Type.CHOKE;
        } else if(type == RawMessage.Type.UNCHOKE.getId()) {
            return RawMessage.Type.UNCHOKE;
        } else if(type == RawMessage.Type.INTERESTED.getId()) {
            return RawMessage.Type.INTERESTED;
        } else if(type == RawMessage.Type.NOT_INTERESTED.getId()) {
            return RawMessage.Type.NOT_INTERESTED;
        } else if(type == RawMessage.Type.HAVE.getId()) {
            return RawMessage.Type.HAVE;
        } else if(type == RawMessage.Type.BITFIELD.getId()) {
            return RawMessage.Type.BITFIELD;
        } else if(type == RawMessage.Type.REQUEST.getId()) {
            return RawMessage.Type.REQUEST;
        } else if(type == RawMessage.Type.PIECE.getId()) {
            return RawMessage.Type.PIECE;
        } else if(type == RawMessage.Type.CANCEL.getId()) {
            return RawMessage.Type.CANCEL;
        } else if(type == RawMessage.Type.HANDSHAKE.getId()) {
            return RawMessage.Type.HANDSHAKE;
        } else {
            return null;
        }
    }
    
    public static int parseHave(RawMessage msg) {
        return msg.getRawData().getInt(5);
    }
    
    public static int[] parseRequest(RawMessage msg) {
        int[] result = new int[3];
        result[0] = msg.getRawData().getInt(5);
        result[1] = msg.getRawData().getInt(9);
        result[2] = msg.getRawData().getInt(13);
        return result;
    }
    
    public static int[] parseCancel(RawMessage msg) {
        return parseRequest(msg);
    }
    
    public static byte[][] parseHandshake(RawMessage msg) {
        byte[][] result = new byte[2][20];
        msg.getRawData().position(28);
        msg.getRawData().get(result[0], 0, 20);
        msg.getRawData().position(48);
        msg.getRawData().get(result[1], 0, 20);
        return result;
    }
    
    public static BitField parseBitField(RawMessage msg, int length) {
        byte[] bitfield = new byte[msg.getLength()-5];
        msg.getRawData().position(5);
        msg.getRawData().get(bitfield);
        try {
            return new BitField(length, bitfield);
        } catch (IllegalArgumentException e) {
            throw new TorrentProtocolException("Expected bitfield length doesn't match the one received", e);
        }
    }
    
    public static Object[] parsePiece(RawMessage msg) {
        msg.getRawData().position(msg.getRawData().position()+5);
        int index = msg.getRawData().getInt();
        int begin = msg.getRawData().getInt();
        ByteBuffer block = msg.getRawData().slice();
        return new Object[] { index, begin, block };
    }
    
    private final static byte PSTRLEN = (byte)19;
    private final static byte[] PSTR = "BitTorrent protocol".getBytes();
    private final static byte[] RESERVED_BYTES = new byte[]{0,0,0,0,0,0,0,0};
    
    public static RawMessage createHandshake(byte[] infoHash, byte[] peerId) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + PSTR.length + RESERVED_BYTES.length + infoHash.length + peerId.length);
        buffer.put(PSTRLEN);
        buffer.put(PSTR);
        buffer.put(RESERVED_BYTES);
        buffer.put(infoHash);
        buffer.put(peerId);
        buffer.flip();
        return new RawMessage(Type.HANDSHAKE, PSTRLEN, buffer);
    }
    
    public static RawMessage createInterested() {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.putInt(1);
        buffer.put(Type.INTERESTED.getId());
        buffer.flip();
        return new RawMessage(Type.INTERESTED, 1, buffer);
    }
    public static RawMessage createNotInterested() {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.putInt(1);
        buffer.put(Type.NOT_INTERESTED.getId());
        buffer.flip();
        return new RawMessage(Type.NOT_INTERESTED, 1, buffer);
    }
    public static RawMessage createChoke() {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.putInt(1);
        buffer.put(Type.CHOKE.getId());
        buffer.flip();
        return new RawMessage(Type.CHOKE, 1, buffer);
    }
    public static RawMessage createUnchoke() {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.putInt(1);
        buffer.put(Type.UNCHOKE.getId());
        buffer.flip();
        return new RawMessage(Type.UNCHOKE, 1, buffer);
    }
    public static RawMessage createBitField(BitField bitField) {
        byte[] byteArray = bitField.asByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(5 + byteArray.length);
        buffer.putInt(1 + byteArray.length);
        buffer.put(Type.BITFIELD.getId());
        buffer.put(byteArray);
        buffer.flip();
        return new RawMessage(Type.BITFIELD, 1 + byteArray.length, buffer);
    }
    public static RawMessage createRequest(int pieceIndex, int offset, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(4+1+12);
        buffer.putInt(13);
        buffer.put(Type.REQUEST.getId());
        buffer.putInt(pieceIndex);
        buffer.putInt(offset);
        buffer.putInt(length);
        buffer.flip();
        return new RawMessage(Type.REQUEST, 13, buffer);
    }
    public static RawMessage createHave(int pieceIndex) {
        ByteBuffer buffer = ByteBuffer.allocate(4+1+4);
        buffer.putInt(5);
        buffer.put(Type.HAVE.getId());
        buffer.putInt(pieceIndex);
        buffer.flip();
        return new RawMessage(Type.HAVE, 5, buffer);
    }
    public static RawMessage createPiece(BlockRequest block, Piece piece) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 9 + block.length);
        buffer.putInt(9 + block.length);
        buffer.put(Type.PIECE.getId());
        piece.read(buffer, block.offset);
        buffer.flip();
        return new RawMessage(Type.PIECE, 9 + block.length, buffer);
    }

}
