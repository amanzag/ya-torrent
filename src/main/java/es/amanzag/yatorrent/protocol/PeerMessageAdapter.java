package es.amanzag.yatorrent.protocol;

import java.nio.ByteBuffer;

public abstract class PeerMessageAdapter {
	
	public void onHandshake(byte[] infoHash, byte[] peerId) {}
	public void onKeepAlive() {}
	public void onChoke() {}
	public void onUnchoke() {}
	public void onInterested() {}
	public void onNotInterested() {}
	public void onHave(int pieceIndex) {}
	public void onBitfield(BitField bitField) {}
	public void onRequest(int pieceIndex, int offset, int length) {}
	public void onBlock(int pieceIndex, int offset, ByteBuffer data) {}
	public void onCancel(int pieceIndex, int offset, int length) {}
	public void onPiece(int pieceIndex) {}

}
