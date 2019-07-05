package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.Configuration;
import com.pseuco.np19.project.launcher.breaker.item.Item;
import com.pseuco.np19.project.launcher.cli.CLI;
import com.pseuco.np19.project.launcher.cli.CLIException;
import com.pseuco.np19.project.launcher.cli.Unit;
import com.pseuco.np19.project.launcher.parser.Parser;
import com.pseuco.np19.project.launcher.render.Renderable;
import com.pseuco.np19.project.slug.tree.Document;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Rocket implements ParagraphManager {

    private Unit unit;
    private Configuration configuration;
    private boolean allFinished;
    private int countAssignments, numBlockElements, threadsDone;

    private ArrayList<List<Item<Renderable>>> itemLists;

    private Thread[] threads;
    final Document document;

    public Rocket(Unit unit){
        this.unit = unit;
        this.configuration = this.unit.getConfiguration();
        this.allFinished = false;
        this.countAssignments = -1;
        this.document = new Document();
        this.threadsDone = 0;
        //this.itemLists = new List<Item<Renderable>>[]; //must be done later
    }



    // this function starts new threads that handle paragraph processes
    // If the threads have finished the document is printed
    public synchronized void processUnit() throws IOException {

        Parser.parse(this.unit.getInputReader(), document);
        this.numBlockElements = document.getElements().size();
        System.out.println(this.numBlockElements + " are about to be processed");
        // We use an ArrayList because a normal array would cause generic error
        // This internally is just an array and it will not copy itself to make it bigger so there should
        // not be a concurrency problem
        itemLists = new ArrayList<List<Item<Renderable>>>(this.numBlockElements+1);
        for(int i=0; i<this.numBlockElements+1; i++){
            itemLists.add(i, null);
        }

        //Get the amount of availabe Processors to spawn dynamic range of Threads
        int cores = Runtime.getRuntime().availableProcessors();
        threads = new Thread[cores];
        for (int i=0; i<cores;i++){
            threads[i] = new ParagraphThread(this.configuration, i, this);
            threads[i].start();
        }


        while(!(allFinished && (this.threadsDone == threads.length))) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        for(Thread t : threads){
            try {
                t.join();
            } catch (InterruptedException e) {
                System.out.println("Got problem while joining!");
            }
        }
        System.out.println("Reached end of processing. Time to put it all together");



    }

    @Override
    public synchronized BlockElementJob assignNewBlock() {
        this.countAssignments++;
        // Nothing to do anymore if count is greater than length of overall BlockElements
        // The thread gets null and should handle exiting by itself!
        if(countAssignments >= this.numBlockElements) {
            this.allFinished = true;
            this.threadsDone++;
            this.notifyAll();
            return null;
        }

        //System.out.println("Assigning job " + this.countAssignments);

        //Not finished? return new tupel
        return new BlockElementJob(this.countAssignments, this.document.getElements().get(this.countAssignments));
    }

    public synchronized boolean allThreadsFinished(){
        for(Thread t : threads){
            if (t.isAlive()) return false;
        }

        return true;
    }

    @Override
    public synchronized void  closeJob(BlockElementJob job) {
        this.itemLists.add(job.getJobID(), job.getFinishedList());
    }

    public static void main(String[] arguments) {

        // This is the same as in Slug but now calling Rocket.handleDocument()
        try {
            List<Unit> units = CLI.parseArgs(arguments);
            if (units.isEmpty()) {
                CLI.printUsage(System.out);
            }
            // we process one unit after another
            for (Unit unit : units) {
                (new Rocket(unit)).processUnit();
            }
            System.exit(0);
        } catch (CLIException error) {
            System.err.println(error.getMessage());
            CLI.printUsage(System.err);
            System.exit(1);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }


}
