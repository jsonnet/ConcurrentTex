package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.parser.DocumentBuilder;
import com.pseuco.np19.project.launcher.parser.ParagraphBuilder;
import com.pseuco.np19.project.launcher.parser.Position;
import com.pseuco.np19.project.slug.tree.block.BlockElement;
import com.pseuco.np19.project.slug.tree.block.ForcedPageBreak;
import com.pseuco.np19.project.slug.tree.block.Paragraph;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConcurrentDocument implements DocumentBuilder {

    private int segmentCounter;
    private int paragraphCounter = -1;
    private BlockElement lastBlockElement;
    private AtomicBoolean isFinished;

    private UnitData udata;
    private ExecutorService executor;

    public ConcurrentDocument(UnitData udata, ExecutorService executor) {
        // Thread safe as long as you do not use ...All() operations
        this.isFinished = new AtomicBoolean(false);
        this.udata = udata;
        this.executor = executor;
    }

    @Override
    public void appendForcedPageBreak(Position position) {
        //add last appended Paragraph to the queue since it should be done by now
        if (lastBlockElement != null) {
            this.paragraphCounter++;
            Job newJob = new Job(this.segmentCounter, this.paragraphCounter, this.lastBlockElement);
            lastBlockElement = null;
            executor.submit(new ParagraphThread(udata, newJob, executor));
        }

        this.paragraphCounter++;
        //Create new paragraph aka the ForcedPageBreak, but this time can be done directly without needing to wait
        Job endJob = new Job(this.segmentCounter, this.paragraphCounter, new ForcedPageBreak(), true);
        executor.submit(new ParagraphThread(udata, endJob, executor));
        //Since this marks the end of segment reset the counter and keep track that we are working
        // on the next segment
        this.segmentCounter++;

        this.paragraphCounter = -1;
    }

    @Override
    public ParagraphBuilder appendParagraph(Position position) {
        this.paragraphCounter++;
        //add last appended Paragraph to the queue since it should be done by now, but remember the first it's called no last elem exists or after a page break!
        if (lastBlockElement != null) {
            Job newJob = new Job(this.segmentCounter, this.paragraphCounter, this.lastBlockElement);
            executor.submit(new ParagraphThread(udata, newJob, executor));
        }

        //Create new paragraph object to return
        Paragraph paragraph = new Paragraph();
        //Safe reference to push it to list later when parser is ready
        this.lastBlockElement = paragraph;

        return paragraph;
    }

    @Override
    public void finish() {
        //Append last pageBreak at the end of whole document
        this.appendForcedPageBreak(null);
        //TODO need to look further into atomicBoolean and others
        this.isFinished.set(true);     //This flag is helpful for the ThreadPool
    }

    //No more data race due to atomic boolean
    public boolean isFinished() {
        return this.isFinished.get();
    }

    //gets called after document is finished and only by one thread: so no synchronized needed
    //FIXME is this a data race again? check while loop, but should not be as it is never check when isFinished is false!
    public int getSegmentCounter() {
        return segmentCounter;
    }
}
