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

public class UnitData {

    private Map<Integer, Segment> segments;
    private List pages;
    private boolean unableToBreak;
    private final Configuration config;
    private final Printer printer;


    public UnitData(Configuration config, Printer printer){
        this.segments = new ConcurrentHashMap<Integer, Segment>();
        this.pages = Collections.synchronizedList(new LinkedList<Page>()); //Is that how it works
        this.unableToBreak = false;
        this.config = config;
        this.printer = printer;
    }

    /**
     * This will take the result and add it to the correct position in the segments map
     */
    public void closeJob(Job job, ExecutorService executor){
        int segID = job.getSegmentID();
        //Check if segment exists, if not create new Segment
        if (segments.get(segID) == null) segments.put(segID, new Segment(executor, config, printer, this));

        //Choose action based on the fact that this job was the last job for Segment or not
        if(job.isLast()){
            segments.get(segID).add(job.getSeqNumber(), job.getFinishedList(), job.getParasInSegment());
        }else{
            segments.get(segID).add(job.getSeqNumber(), job.getFinishedList(), -1);
        }
    }

    public void addPages(List<Page> l){
        this.pages.addAll(l);
    }

    public boolean isUnableToBreak(){
        return this.unableToBreak;
    }

    public List getPages() {
        return pages;
    }

    public Configuration getConfig(){
        return this.config;
    }

    public void setUnableToBreak(boolean b){
        this.unableToBreak = b;
    }
}
