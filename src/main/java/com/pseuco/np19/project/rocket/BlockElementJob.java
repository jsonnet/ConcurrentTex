package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.breaker.item.Item;
import com.pseuco.np19.project.launcher.render.Renderable;
import com.pseuco.np19.project.slug.tree.block.BlockElement;

import java.util.List;

public class BlockElementJob {

    private int jobID;
    private BlockElement element;
    private List<Item<Renderable>> finishedList = null;

    public  BlockElementJob(int id, BlockElement element){
        this.jobID = id;
        this.element = element;
    }

    public synchronized int getJobID() {
        return this.jobID;
    }


    public synchronized BlockElement getElement() {
        return this.element;
    }

    public synchronized List<Item<Renderable>> getFinishedList() {
        return this.finishedList;
    }

    public synchronized void setFinishedList(List<Item<Renderable>> finishedList) {
        this.finishedList = finishedList;
    }
}
