package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.parser.DocumentBuilder;
import com.pseuco.np19.project.launcher.parser.ParagraphBuilder;
import com.pseuco.np19.project.launcher.parser.Position;
import com.pseuco.np19.project.slug.tree.block.BlockElement;
import com.pseuco.np19.project.slug.tree.block.ForcedPageBreak;
import com.pseuco.np19.project.slug.tree.block.Paragraph;

import java.util.concurrent.LinkedBlockingDeque;

public class ConcurrentDocument implements DocumentBuilder {

    private int segmentCounter;
    private int paragraphCounter;
    private BlockElement lastBlockElement;
    private LinkedBlockingDeque<Job> jobs;
    private boolean isFinished;


    public ConcurrentDocument() {
        // Thread safe as long as you do not use ...All() operations
        this.jobs = new LinkedBlockingDeque<Job>();
        this.isFinished = false;
    }

    @Override
    public void appendForcedPageBreak(Position position) {
        this.paragraphCounter++;
        Job endJob = new Job(this.segmentCounter, this.paragraphCounter, new ForcedPageBreak(), true);
        jobs.add(endJob);

        //Since this marks the end of segment reset the counter and keep track that we are working
        //on the next segment
        //FIXME segCounter datarace?
        this.segmentCounter++;
        this.paragraphCounter = 0;
    }

    @Override
    public ParagraphBuilder appendParagraph(Position position) {
        this.paragraphCounter++;
        //add last appended Paragraph to the queue since it should be done by now
        Job newJob = new Job(this.segmentCounter, this.paragraphCounter, this.lastBlockElement);
        jobs.add(newJob);

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
        // FIXME does this one need a sync block? #datarace from below
        this.isFinished = true;     //This flag is helpful for the ThreadPool
    }

    public boolean isJobsEmpty() {
        return jobs.isEmpty();  //TODO: Check DataRace or not
    }

    //TODO look into this data race, but most certainly is due to read-write
    public synchronized boolean isFinished() {
        return this.isFinished;
    }

    public Job getJob() {
        // No DataRace due to concurrent data-structure
        return jobs.poll();
    }

    //TODO look into this data race, but most certainly is due to read-write
    public synchronized int getSegmentCounter() {
        return segmentCounter;
    }
}
