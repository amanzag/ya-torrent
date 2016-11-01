package es.amanzag.yatorrent.protocol.io;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;

import com.google.common.eventbus.EventBus;

import es.amanzag.yatorrent.events.BandwidthUsageEvent;

public class BandwithReporter {
    
    private final static Duration SAMPLE_MAX_TIME = Duration.of(5, ChronoUnit.SECONDS);
    private Deque<Entry<Instant, Integer>> samples;
    private EventBus bus;

    public BandwithReporter(EventBus bus) {
        samples = new LinkedList<>();
        this.bus = bus;
    }
    
    public void register(int bytes) {
        samples.addFirst(new AbstractMap.SimpleEntry<Instant, Integer>(Instant.now(), bytes));
    }
    
    public void report() {
        Instant now = Instant.now();
        Instant earliestSample = now;
        int bytes = 0;
        
        Iterator<Entry<Instant, Integer>> iterator = samples.iterator();
        while(iterator.hasNext()) {
            Entry<Instant, Integer> sample = iterator.next();
            if(sample.getKey().plus(SAMPLE_MAX_TIME).isAfter(now)) {
                bytes += sample.getValue();
                earliestSample = sample.getKey();
            } else {
                iterator.remove();
            }
        }
        
        double bytesPerSecond = 0;
        if(!earliestSample.equals(now)) {
            bytesPerSecond = (bytes * 1000.0) / Duration.between(earliestSample, now).toMillis();
        }
        bus.post(new BandwidthUsageEvent((int) bytesPerSecond));
    }

}
