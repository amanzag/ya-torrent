package es.amanzag.yatorrent.events;

public class BandwidthUsageEvent {
    
    public int bytesPerSecond;

    public BandwidthUsageEvent(int bytesPerSecond) {
        this.bytesPerSecond = bytesPerSecond;
    }

}
