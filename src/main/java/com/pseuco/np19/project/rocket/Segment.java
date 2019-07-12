package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.Configuration;
import com.pseuco.np19.project.launcher.breaker.Piece;
import com.pseuco.np19.project.launcher.breaker.UnableToBreakException;
import com.pseuco.np19.project.launcher.breaker.item.Item;
import com.pseuco.np19.project.launcher.printer.Page;
import com.pseuco.np19.project.launcher.printer.Printer;
import com.pseuco.np19.project.launcher.render.Renderable;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static com.pseuco.np19.project.launcher.breaker.Breaker.breakIntoPieces;

public class Segment {
    private int id;
    private HashMap<Integer, List> items; //needs to be map since I need to store in sequence
    private int addCounter;
    private int expected = -1;
    private final Configuration config;
    private final Printer printer;
    private ExecutorService executor;
    private UnitData udata;
    private boolean isFinished = false;

    public Segment(ExecutorService executor, Configuration config, Printer printer, UnitData udata){
        this.items = new HashMap<Integer, List>();
        this.printer = printer;
        this.config = config;
        this.executor = executor;
        this.udata = udata;
    }


    public synchronized void add(int seqNmbr, List<Item<Renderable>> l, int expected){

        this.addCounter++;
        this.items.put(seqNmbr, l);

        //only set expected if it has not been set yet
        this.expected = expected != -1 ? expected : this.expected;

        if(this.expected == this.addCounter){
            //Rendering is a job for the executer
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    //System.out.println("I am rendering");
                    try {

                        LinkedList<Item<Renderable>> itemList = new LinkedList<Item<Renderable>>(); //FIXME: Might not work due to incorrect order adding
                        for(List l : items.values()){
                            itemList.addAll(l);
                        }

                        List<Piece<Renderable>> pieces = breakIntoPieces(
                                config.getBlockParameters(),
                                itemList,
                                config.getBlockTolerances(),
                                config.getGeometry().getTextHeight()
                        );

                        //System.out.println("genau");
                        List<Page> renderPages = printer.renderPages(pieces);
                        udata.addPages(renderPages);
                        isFinished = true;
                    } catch (UnableToBreakException e) {
                        System.out.println("Could not render. HELP");
                    }
                }
            });

        }

    }

}
