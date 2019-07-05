package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.Configuration;
import com.pseuco.np19.project.launcher.breaker.Piece;
import com.pseuco.np19.project.launcher.breaker.UnableToBreakException;
import com.pseuco.np19.project.launcher.breaker.item.Item;
import com.pseuco.np19.project.launcher.cli.CLI;
import com.pseuco.np19.project.launcher.cli.CLIException;
import com.pseuco.np19.project.launcher.cli.Unit;
import com.pseuco.np19.project.launcher.parser.Parser;
import com.pseuco.np19.project.launcher.render.Renderable;
import com.pseuco.np19.project.slug.tree.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import static com.pseuco.np19.project.launcher.breaker.Breaker.breakIntoPieces;

public class Rocket implements ParagraphManager {

    final Document document;
    private Unit unit;
    private Configuration configuration;
    private boolean allFinished;
    private int countAssignments, numBlockElements, threadsDone;
    private List[] itemLists;
    private Thread[] threads;

    public Rocket(Unit unit) {
        this.unit = unit;
        this.configuration = this.unit.getConfiguration();
        this.allFinished = false;
        this.countAssignments = -1;
        this.document = new Document();
        this.threadsDone = 0;
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

    // this function starts new threads that handle paragraph processes
    // If the threads have finished the document is printed
    public synchronized void processUnit() throws IOException {

        Parser.parse(this.unit.getInputReader(), document);
        this.numBlockElements = document.getElements().size();
        //System.out.println(this.numBlockElements + " are about to be processed");
        // We use an ArrayList because a normal array would cause generic error
        // This internally is just an array and it will not copy itself to make it bigger so there should
        // not be a concurrency problem
        itemLists = new LinkedList[this.numBlockElements + 1]; // +1 for last empty page

        //Get the amount of availabe Processors to spawn dynamic range of Threads
        int cores = Runtime.getRuntime().availableProcessors();
        threads = new Thread[cores];
        for (int i = 0; i < cores; i++) {
            threads[i] = new ParagraphThread(this.configuration, i, this);
            threads[i].start();
        }

        while (!(allFinished && (this.threadsDone == threads.length))) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.out.println("Got problem while joining!");
            }
        }
        //System.out.println("Reached end of processing. Time to put it all together");

        // Empty page
        var empty = new LinkedList<>();
        this.configuration.getBlockFormatter().pushForcedPageBreak(empty::add);
        this.itemLists[this.itemLists.length - 1] = empty;

        // Joining list of lists
        List<Item<Renderable>> items = new ArrayList<>();
        Stream.of(itemLists).forEachOrdered(items::addAll);
        //System.out.println("Joined");

        try {
            final List<Piece<Renderable>> pieces = breakIntoPieces(this.configuration.getBlockParameters(), items, this.configuration.getBlockTolerances(), this.configuration.getGeometry().getTextHeight());

            this.unit.getPrinter().printPages(this.unit.getPrinter().renderPages(pieces));
        } catch (UnableToBreakException ignored) {
            this.unit.getPrinter().printErrorPage();
            System.err.println("Unable to break lines!");
        }

        this.unit.getPrinter().finishDocument();
        //System.out.println("Hype");
    }

    @Override
    public synchronized BlockElementJob assignNewBlock() {
        this.countAssignments++;
        // Nothing to do anymore if count is greater than length of overall BlockElements
        // The thread gets null and should handle exiting by itself!
        if (countAssignments >= this.numBlockElements) {
            this.allFinished = true;
            this.threadsDone++;
            this.notifyAll();
            return null;
        }

        //System.out.println("Assigning job " + this.countAssignments);

        //Not finished? return new tupel
        return new BlockElementJob(this.countAssignments, this.document.getElements().get(this.countAssignments));
    }

    public synchronized boolean allThreadsFinished() {
        for (Thread t : threads) {
            if (t.isAlive()) return false;
        }

        return true;
    }

    @Override
    public void closeJob(BlockElementJob job) {
        this.itemLists[job.getJobID()] = job.getFinishedList();
    }

    public synchronized void handleBrokenDoc() {
        for (Thread t : threads) {
            t.interrupt();
        }
        System.out.println("Unable to break, help!");
    }
}
