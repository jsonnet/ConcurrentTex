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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.pseuco.np19.project.launcher.breaker.Breaker.breakIntoPieces;

public class Rocket implements ParagraphManager {

    //INFO : minimal output, FINE/ALL: better for debugging, WARNING: for stuff like unableToBreakPage, SEVERE: exception prints
    private static final Level LOG_LEVEL = Level.OFF;
    static Logger log;

    //Needed for the Logger to display lines as wanted. Advise: do not touch
    static {
        InputStream stream = Rocket.class.getClassLoader().getResourceAsStream("logging.properties");
        try {
            LogManager.getLogManager().readConfiguration(stream);
            log = Logger.getLogger(Rocket.class.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        // PUBLISH this level
        handler.setLevel(Level.FINER);
        log.addHandler(handler);
        log.setLevel(LOG_LEVEL);   //Setting a level so only specifig logs are printed
    }

    private final Document document;
    private Unit unit;
    private Configuration configuration;
    private boolean allFinished, unableToBreak;
    private int countAssignments, numBlockElements, threadsDone;
    private List[] itemLists;
    private Thread[] threads;

    private Rocket(Unit unit) {
        this.unit = unit;
        this.configuration = this.unit.getConfiguration();
        this.allFinished = false;
        this.unableToBreak = false;
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
    private synchronized void processUnit() throws IOException {

        Parser.parse(this.unit.getInputReader(), document);
        this.numBlockElements = document.getElements().size();
        log.log(Level.INFO, this.numBlockElements + " are about to be processed");
        itemLists = new LinkedList[this.numBlockElements + 1]; // +1 for last empty page

        //Get the amount of available logic cores to spawn dynamic range of Threads
        int cores = Runtime.getRuntime().availableProcessors();
        threads = new Thread[cores];
        for (int i = 0; i < cores; i++) {
            threads[i] = new ParagraphThread(this.configuration, i, this);
            threads[i].start();
        }

        //TODO unableToBreak not needed as notify below, but saves time
        while (!(allFinished && (this.threadsDone == threads.length) || unableToBreak)) {
            try {
                //TODO wait needed, so other methods of the object can still be executed
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //This will finish the document if a paragraph was not able to break. Skip the rest because unnecessary and will cause IO-Errors
        if (unableToBreak) {
            //The Rocket will take care of printing the last error page!
            try {
                this.unit.getPrinter().printErrorPage();
                this.unit.getPrinter().finishDocument(); //FIXME Lukas pls: finishDocument not printErrorPage
            } catch (IOException e) {
                log.log(Level.SEVERE, "Not able to print the ErrorPage and/or finish Document");
            }
            log.log(Level.INFO, "Exiting due to not being able to break paragraph. Over and out!");
            return;
        }

        log.log(Level.INFO, "Reached end of processing. Time to put it all together");

        // Empty page
        var empty = new LinkedList<>();
        this.configuration.getBlockFormatter().pushForcedPageBreak(empty::add);
        this.itemLists[this.itemLists.length - 1] = empty;

        // Joining list of lists
        List<Item<Renderable>> items = new ArrayList<>();
        //TODO unsafe op
        for (List item : itemLists)
            items.addAll(item);
        log.log(Level.INFO, "JOINED lists in ArrayList");

        try {
            List<Piece<Renderable>> pieces = breakIntoPieces(
                    this.configuration.getBlockParameters(),
                    items,
                    this.configuration.getBlockTolerances(),
                    this.configuration.getGeometry().getTextHeight()
            );

            this.unit.getPrinter().printPages(this.unit.getPrinter().renderPages(pieces));
        } catch (UnableToBreakException ignored) {
            this.unit.getPrinter().printErrorPage();
            System.err.println("Unable to break lines!");
        }

        this.unit.getPrinter().finishDocument();
        log.log(Level.INFO, "FINISHED printing a document!\n\n");
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

        log.log(Level.FINE, "Assigning job " + this.countAssignments);

        //Not finished? return new tupel
        return new BlockElementJob(this.countAssignments, this.document.getElements().get(this.countAssignments));
    }

    @Override
    public void closeJob(BlockElementJob job) {
        this.itemLists[job.getJobID()] = job.getFinishedList();
    }

    public synchronized void handleBrokenDoc() {
        for (Thread t : threads) {
            t.interrupt();
        }
        this.unableToBreak = true;
        this.notifyAll();
        log.log(Level.WARNING, "Unable to break, help!");
    }
}
