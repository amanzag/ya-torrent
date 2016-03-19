package es.amanzag.yatorrent.protocol;

public class BlockRequest {
    
    public BlockRequest(int pieceIndex, int offset, int length) {
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.length = length;
    }
    public int pieceIndex;
    public int offset;
    public int length;

}
