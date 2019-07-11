package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.Configuration;
import com.pseuco.np19.project.launcher.breaker.Piece;
import com.pseuco.np19.project.launcher.breaker.UnableToBreakException;
import com.pseuco.np19.project.launcher.breaker.item.Item;
import com.pseuco.np19.project.launcher.render.Renderable;
import com.pseuco.np19.project.slug.tree.block.BlockElement;
import com.pseuco.np19.project.slug.tree.block.ForcedPageBreak;
import com.pseuco.np19.project.slug.tree.block.IBlockVisitor;
import com.pseuco.np19.project.slug.tree.block.Paragraph;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import static com.pseuco.np19.project.launcher.breaker.Breaker.breakIntoPieces;

public class ParagraphThread extends Thread implements IBlockVisitor {

    private final Configuration configuration;
    private List<Item<Renderable>> items = new LinkedList<>();
    private int id;
    private ParagraphManager paragraphManager;

    ParagraphThread(Configuration config, int id, ParagraphManager pm) {
        this.configuration = config;
        this.id = id;
        this.paragraphManager = pm;
    }

    //TODO okay, let's rethink
    @Override
    public void run() {
        //get the next BlockElement to parse
        //TODO here we request a new job from our JobQueue (class)
        BlockElementJob job = this.paragraphManager.assignNewBlock();
        //This sometimes throws nullpointer exception so this will check job for null and if true while will not be executed
        //TODO yeah, the queue should tell us when its empty or we just ask for via empty()
        BlockElement element = job != null ? job.getElement() : null;

        // If the element to process is null there is nothing more to do so terminate
        //TODO need some good way of checking interrupt (frees processing for other threads quicker)
        while (job != null && !Thread.currentThread().isInterrupted()) {
            //TODO this thing is important
            element.accept(this);

            //Write back the result in ArrayList of Rocket
            //TODO we only need to write to paragraphManager
            job.setFinishedList(this.items);
            paragraphManager.closeJob(job);

            Rocket.log.log(Level.FINE, "Thread " + this.id + " or " + Thread.currentThread() + " finished " + job.getJobID());

            // Get a new job
            //TODO rewrite to a nicer layout, but in principal okay
            job = paragraphManager.assignNewBlock();

            if (job != null) element = job.getElement();

            items = new LinkedList<>(); // Cannot clear here as we just add references to these lists to our array
        }
        Rocket.log.log(Level.INFO, "I - Thread " + this.id + " - sign off now");
    }

    // The same as in Slug
    @Override
    public void visit(Paragraph paragraph) {
        // transform the paragraph into a sequence of items
        List<Item<Renderable>> items = paragraph.format(this.configuration.getInlineFormatter());

        try {
            // break the items into pieces using the Knuth-Plass algorithm
            List<Piece<Renderable>> lines = breakIntoPieces(this.configuration.getInlineParameters(), items, this.configuration.getInlineTolerances(), this.configuration.getGeometry().getTextWidth());

            // transform lines into items and append them to `this.items`
            this.configuration.getBlockFormatter().pushParagraph(this.items::add, lines);
        } catch (UnableToBreakException error) {
            //System.err.println("Unable to break paragraph!");
            Rocket.log.log(Level.WARNING, "Unable to break paragraph!");
            paragraphManager.handleBrokenDoc();
        }
    }


    // Same as in Slug
    @Override
    public void visit(ForcedPageBreak forcedPageBreak) {
        // transform forced page break into items and append them to `this.items`
        this.configuration.getBlockFormatter().pushForcedPageBreak(this.items::add);
    }
}
