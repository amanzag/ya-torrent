package es.amanzag.yatorrent.protocol;

import java.nio.ByteBuffer;

public interface PeerMessageListener {
	
	default void onHandshake(byte[] infoHash, byte[] peerId) {}
	default void onKeepAlive() {}
	default void onChoke() {}
	default void onUnchoke() {}
	default void onInterested() {}
	default void onNotInterested() {}
	default void onHave(int pieceIndex) {}
	default void onBitfield(BitField bitField) {}
	default void onRequest(int pieceIndex, int offset, int length) {}
	default void onBlock(int pieceIndex, int offset, ByteBuffer data) {}
	default void onCancel(int pieceIndex, int offset, int length) {}
	default void onPiece(int pieceIndex) {}
	default void onDisconnect() {}

}
