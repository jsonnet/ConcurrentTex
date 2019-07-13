package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.Configuration;
import com.pseuco.np19.project.launcher.printer.Page;
import com.pseuco.np19.project.launcher.printer.Printer;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class UnitData {

    private final Configuration config;
    private final Printer printer;
    private int segmentCount;
    private Map<Integer, Segment> segments;
    private List pages;
    private AtomicBoolean unableToBreak;


    public UnitData(Configuration config, Printer printer) {
        this.segments = new ConcurrentHashMap<>();
        this.pages = Collections.synchronizedList(new LinkedList<Page>()); //Is that how it works
        this.unableToBreak = new AtomicBoolean(false);
        this.config = config;
        this.printer = printer;
        this.segmentCount = 0;
    }

    /**
     * This will take the result and add it to the correct position in the segments map
     */
    public synchronized void closeJob(Job job, ExecutorService executor) {
        int segID = job.getSegmentID();
        //Check if segment exists, if not create new Segment
        if (segments.get(segID) == null) {
            segments.put(segID, new Segment(executor, config, printer, this));
            segmentCount++;
        }

        //Choose action based on the fact that this job was the last job for Segment or not
        if (job.isLast()) {
            segments.get(segID).add(job.getSeqNumber(), job.getFinishedList(), job.getParasInSegment());
        } else {
            segments.get(segID).add(job.getSeqNumber(), job.getFinishedList(), -1);
        }
    }

    //TODO why synced?
    public synchronized void addPages(List<Page> l) {
        //System.out.println("I am adding the pages");
        this.pages.addAll(l);
    }

    public boolean isUnableToBreak() {
        return this.unableToBreak.get();
    }

    /**
     * This sets the unableToBreak flag to true
     * this will not be reversible
     */
    public void setUnableToBreak() {
        this.unableToBreak.compareAndSet(false, true);
    }

    //TODO maybe return something different (copy etc)
    public synchronized List getPages() {
        return pages;
    }

    // No data race as config is final and is only being read
    public Configuration getConfig() {
        return this.config;
    }

    public synchronized int getSegmentCount() {
        return this.segmentCount;
    }
}
