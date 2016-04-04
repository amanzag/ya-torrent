package es.amanzag.yatorrent.storage;

public interface PieceListener {
    
    void onCompletionChanged(int newBytes, int completedBytes, int totalBytes);

}
