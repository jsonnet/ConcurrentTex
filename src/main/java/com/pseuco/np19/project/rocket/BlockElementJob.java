package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.breaker.item.Item;
import com.pseuco.np19.project.launcher.render.Renderable;
import com.pseuco.np19.project.slug.tree.block.BlockElement;

import java.util.List;

class BlockElementJob {

    private int jobID;
    private BlockElement element;
    private List<Item<Renderable>> finishedList = null;

    BlockElementJob(int id, BlockElement element) {
        this.jobID = id;
        this.element = element;
    }

    int getJobID() {
        return this.jobID;
    }


    BlockElement getElement() {
        return this.element;
    }

    List<Item<Renderable>> getFinishedList() {
        return this.finishedList;
    }

    //TODO sync needed here?
    synchronized void setFinishedList(List<Item<Renderable>> finishedList) {
        this.finishedList = finishedList;
    }
}
