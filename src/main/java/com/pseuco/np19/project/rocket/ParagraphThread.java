package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.breaker.Piece;
import com.pseuco.np19.project.launcher.breaker.UnableToBreakException;
import com.pseuco.np19.project.launcher.breaker.item.Item;
import com.pseuco.np19.project.launcher.render.Renderable;
import com.pseuco.np19.project.slug.tree.block.ForcedPageBreak;
import com.pseuco.np19.project.slug.tree.block.IBlockVisitor;
import com.pseuco.np19.project.slug.tree.block.Paragraph;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.pseuco.np19.project.launcher.breaker.Breaker.breakIntoPieces;

public class ParagraphThread implements Runnable, IBlockVisitor {
    private UnitData udata;
    private Job job;
    private ExecutorService executor;
    private final LinkedList<Item<Renderable>> items = new LinkedList<>();

    public ParagraphThread(UnitData udata, Job job, ExecutorService executor) {
        this.udata = udata;
        this.job = job;
        this.executor = executor;
    }

    @Override
    public void run(){
        //call correct visit method
        job.getElement().accept(this);

        //write result back into job since it is finished after visit
        job.setFinishedList(this.items);
        //close this job again and exit.
        udata.closeJob(job, executor);

    }

    @Override
    public void visit(Paragraph paragraph) {
        // transform the paragraph into a sequence of items
        final List<Item<Renderable>> items = paragraph.format(udata.getConfig().getInlineFormatter());

        try {
            // break the items into pieces using the Knuth-Plass algorithm
            final List<Piece<Renderable>> lines = breakIntoPieces(
                    udata.getConfig().getInlineParameters(),
                    items,
                    udata.getConfig().getInlineTolerances(),
                    udata.getConfig().getGeometry().getTextWidth()
            );

            // transform lines into items and append them to `this.items`
            udata.getConfig().getBlockFormatter().pushParagraph(this.items::add, lines);
        } catch (UnableToBreakException error) {
            System.err.println("Unable to break paragraph!");
            udata.setUnableToBreak(true);
        }
    }

    @Override
    public void visit(ForcedPageBreak forcedPageBreak) {
        // transform forced page break into items and append them to `this.items`
        udata.getConfig().getBlockFormatter().pushForcedPageBreak(this.items::add);
    }
}
