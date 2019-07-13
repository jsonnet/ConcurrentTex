package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.breaker.item.Item;
import com.pseuco.np19.project.launcher.render.Renderable;
import com.pseuco.np19.project.slug.tree.block.BlockElement;

import java.util.LinkedList;

public class Job {

    private int segmentID;
    private int seqNumber;
    private int parasInSegment;
    private BlockElement element;
    private boolean isLast;
    private LinkedList<Item<Renderable>> finishedList;

    public Job(int segmentID, int seqNumber, BlockElement element) {
        this.segmentID = segmentID;
        this.seqNumber = seqNumber;
        this.element = element;
        this.parasInSegment = -1;
    }

    public Job(int segmentID, int seqNumber, BlockElement element, boolean isLast) {
        this.segmentID = segmentID;
        this.seqNumber = seqNumber;
        this.element = element;
        this.parasInSegment = seqNumber;
        this.isLast = isLast;
    }

    public int getSegmentID() {
        return segmentID;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public BlockElement getElement() {
        return element;
    }

    public boolean isLast() {
        return isLast;
    }

    public LinkedList<Item<Renderable>> getFinishedList() {
        return finishedList;
    }

    public void setFinishedList(LinkedList<Item<Renderable>> list) {
        this.finishedList = list;
    }

    public int getParasInSegment() {
        return parasInSegment;
    }
}
