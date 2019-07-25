package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.Configuration;
import com.pseuco.np19.project.launcher.breaker.Piece;
import com.pseuco.np19.project.launcher.breaker.UnableToBreakException;
import com.pseuco.np19.project.launcher.breaker.item.Item;
import com.pseuco.np19.project.launcher.printer.Printer;
import com.pseuco.np19.project.launcher.render.Renderable;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.pseuco.np19.project.launcher.breaker.Breaker.breakIntoPieces;

public class Segment {
    private final Configuration config;
    private final Printer printer;
    private final int id;
    private final UnitData udata;
    private final ExecutorService executor;
    private final /*Concurrent*/ HashMap<Integer, List<Item<Renderable>>> items;
    private /*final AtomicInteger*/ int expected = -1; //new AtomicInteger(-1);

    public Segment(ExecutorService executor, Configuration config, Printer printer, UnitData udata, int id) {
        this.items = new /*Concurrent*/HashMap<>();
        this.printer = printer;
        this.config = config;
        this.executor = executor;
        this.udata = udata;
        this.id = id;
    }

    public void add(int seqNmbr, List<Item<Renderable>> l, int expected) {
        // actual use of the method, to add the block elements to the segments
        this.items.put(seqNmbr, l);

        // only set expected if it has not been set yet
        if (expected != -1) this.expected = expected; //.compareAndSet(-1, expected);

        // Render the pages of this segment if the segment is complete
        if (this.expected/*.get()*/ == this.items.size()) {
            //Rendering is a job for the executor
            executor.submit(() -> {
                try {
                    //This works (also correct order!)
                    LinkedList<Item<Renderable>> itemList = new LinkedList<>();
                    for (List<Item<Renderable>> l1 : items.values()) {
                        itemList.addAll(l1);
                    }

                    List<Piece<Renderable>> pieces = breakIntoPieces(config.getBlockParameters(), itemList, config.getBlockTolerances(),
                            config.getGeometry().getTextHeight());

                    udata.addPages(id, printer.renderPages(pieces));
                } catch (UnableToBreakException e) {
                    System.out.println("Could not render. HELP");
                }
            });
        }
    }
}
