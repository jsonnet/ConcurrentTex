package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.Configuration;
import com.pseuco.np19.project.launcher.printer.Page;
import com.pseuco.np19.project.launcher.printer.Printer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class UnitData {

    private final Configuration config;
    private final Printer printer;
    private final ExecutorService executor;
    private final Lock printLock = new ReentrantLock();
    private Map<Integer, Segment> segments;
    private Map<Integer, List<Page>> pages;
    private AtomicBoolean unableToBreak;
    private int printedPages = 0, printQueuePages = 0;


    public UnitData(Configuration config, Printer printer, ExecutorService executor) {
        this.segments = new ConcurrentHashMap<>();
        this.pages = new ConcurrentHashMap<>();
        this.unableToBreak = new AtomicBoolean(false);
        this.config = config;
        this.printer = printer;
        this.executor = executor;
    }

    /**
     * This will take the result and add it to the correct position in the segments map
     * sync has to be here -> Lukas
     */
    public synchronized void closeJob(Job job, ExecutorService executor) {
        int segID = job.getSegmentID();

        //Check if segment exists, if not create new Segment
        if (segments.get(segID) == null) {
            segments.put(segID, new Segment(executor, config, printer, this, segID));
        }

        //Choose action based on the fact that this job was the last job for Segment or not
        if (job.isLast()) {
            segments.get(segID).add(job.getSeqNumber(), job.getFinishedList(), job.getParasInSegment());
        } else {
            segments.get(segID).add(job.getSeqNumber(), job.getFinishedList(), -1);
        }
    }

    public synchronized void addPages(int seq, List<Page> l) {
        // Add the pages to the correct position in the map
        this.pages.put(seq, l);

        // Submit all possible print jobs as the correct order is not given at this point we need to check
        //TODO printQueuePages data race if not sync!
        while (pages.containsKey(printQueuePages)) {
            printQueuePages++;
            executor.submit(() -> {
                // Here we lock the printer so no other thread can print (--> we are only printing here)
                printLock.lock();
                try {
                    printer.printPages(pages.get(printedPages));
                    printedPages++;
                    // Only for short time lock on udata to notify UnitHandler
                    synchronized (this) {
                        this.notify();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    printLock.unlock();
                }
            });
        }
    }

    public int getPrintedPages() {
        return this.printedPages;
    }

    public boolean isUnableToBreak() {
        return this.unableToBreak.get();
    }

    /**
     * This sets the unableToBreak flag to true
     * this will not be reversible
     */
    public void setUnableToBreak() {
        // this one needs to use CAS as multiple threads could set the flag at once
        this.unableToBreak.compareAndSet(false, true);
        synchronized (this) {
            this.notify();
        }
    }

    // No data race as config is final and is only being read
    public Configuration getConfig() {
        return this.config;
    }
}
