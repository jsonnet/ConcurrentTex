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
import com.pseuco.np19.project.slug.tree.block.BlockElement;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static com.pseuco.np19.project.launcher.breaker.Breaker.breakIntoPieces;

public class Rocket implements ParagraphManager {

    //INFO : minimal output, FINE/ALL: better for debugging, WARNING: for stuff like unableToBreakPage, SEVERE: exception prints
    private static final Level LOG_LEVEL = Level.OFF;
    static Logger log;

    //TODO maybe we remove this part again or use something different?!
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

    private final ConcurrentDocument document;
    private Unit unit;
    private Configuration configuration;
    private boolean allFinished, unableToBreak;
    private int countAssignments, numBlockElements, threadsDone;
    private List[] itemLists;
    private Thread[] threads;
    private Iterator<BlockElement> elem;

    private Rocket(Unit unit) {
        this.unit = unit;
        this.configuration = this.unit.getConfiguration();
        this.allFinished = false;
        this.unableToBreak = false;
        this.countAssignments = -1;
        this.document = new ConcurrentDocument();
        this.threadsDone = 0;
    }

    public static void main(String[] arguments) {
        // This is the same as in Slug but now calling Rocket.handleDocument()
        try {
            //TODO args parsing is atomic, so no need to think about threading
            List<Unit> units = CLI.parseArgs(arguments);
            if (units.isEmpty()) {
                CLI.printUsage(System.out);
            }
            // we process one unit after another
            //TODO spawn multiple threads (worker threads based on ThreadPool) here for each unit (maybe get a new class to handle for simplicity?)
            // these workers are global for each unit also, we need java futures (look into this last one)
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

        //TODO run this separate and read document each time and spawn new thread based on the newly found elements
        Parser.parse(this.unit.getInputReader(), document);

        //TODO no.. but could the parsed and thus spawned elements
        this.numBlockElements = document.getElements().size();

        //TODO needs to be converted to a ConcurrentHashMap of maybe LinkedList connected to index
        itemLists = new LinkedList[this.numBlockElements];

        //TODO lets see about this later
        elem = this.document.getElements().iterator();

        //Get the amount of available logic cores to spawn dynamic range of Threads
        //TODO we think about doing a WorkingQueue (extra class with ConcurrentBlockingQueue) which distributes them to a global list of workers
        // but again only based on all avail cores
        int cores = Runtime.getRuntime().availableProcessors();
        threads = new Thread[cores];

        //FIXME reading unableToBreak could be a data-race write-read 🤦
        //TODO we move this to before processing the units maybe we need to think about assigning some threads (one for each unit) for parsing but DONOT
        // leave them idle
        for (int i = 0; i < cores && !unableToBreak; i++) {
            threads[i] = new ParagraphThread(this.configuration, i, this);
            threads[i].start();
        }

        //TODO we could still count the elements related to how many finished
        while (!(allFinished && (this.threadsDone == threads.length) || unableToBreak)) {
            try {
                //wait needed, so other methods of the object can still be executed
                //TODO should be okay, as breakJob breaks the wait with notify
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //This will finish the document if a paragraph was not able to break. Skip the rest because unnecessary and will cause IO-Errors
        //TODO should be okay, no need to change
        if (unableToBreak) {
            //The Rocket will take care of printing the last error page!
            try {
                this.unit.getPrinter().printErrorPage();
                this.unit.getPrinter().finishDocument();
            } catch (IOException e) {
                log.log(Level.SEVERE, "Not able to print the ErrorPage and/or finish Document");
            }
            log.log(Level.INFO, "Exiting due to not being able to break paragraph. Over and out!");
            return;
        }
        log.log(Level.INFO, "Reached end of processing. Time to put it all together");

        //TODO yeah let's rethink about this part 1. leave it as it is, or 2. find a way to use the Map with the breakIntoPieces
        //Joining list of lists (1-2 ms)
        List<Item<Renderable>> items = new ArrayList<>();
        for (List item : itemLists)
            items.addAll(item);
        log.log(Level.INFO, "JOINED lists in ArrayList");

        // PageBreak
        //TODO should be good to go
        this.configuration.getBlockFormatter().pushForcedPageBreak(items::add);

        //TODO dunno what to do, we need a way of knowing when to break our page (i.e. when the page is full) to print it prior completion
        // probability needs needs changing in parser
        try {
            List<Piece<Renderable>> pieces = breakIntoPieces(this.configuration.getBlockParameters(), items, this.configuration.getBlockTolerances(),
                    this.configuration.getGeometry().getTextHeight());

            this.unit.getPrinter().printPages(this.unit.getPrinter().renderPages(pieces));
        } catch (UnableToBreakException ignored) {
            this.unit.getPrinter().printErrorPage();
            System.err.println("Unable to break lines!");
        }

        //TODO the threads should now be free for all other running threads
        this.unit.getPrinter().finishDocument();
        log.log(Level.INFO, "FINISHED printing a document!\n\n");
    }

    @Override
    public synchronized BlockElementJob assignNewBlock() {

        //TODO following can be reused
        this.countAssignments++;
        // Nothing to do anymore if count is greater than length of overall BlockElements
        // The thread gets null and should handle exiting by itself!
        if (countAssignments >= this.numBlockElements) { //!elem.hasNext()
            this.allFinished = true;
            this.threadsDone++;
            this.notifyAll();
            return null;
        }

        log.log(Level.FINE, "Assigning job " + this.countAssignments);

        //TODO get a new element but not here, worker should handle this itself with Queue
        return new BlockElementJob(this.countAssignments, elem.next());
    }

    @Override
    public void closeJob(BlockElementJob job) {
        //TODO here we add to out ConcurrentHashMap with key= ID and val= LinkedList
        this.itemLists[job.getJobID()] = job.getFinishedList();
    }

    public synchronized void handleBrokenDoc() {
        //TODO Something like this
        for (Thread t : threads) {
            t.interrupt();
        }
        //TODO something against these weird data-races maybe mutex? or other
        this.unableToBreak = true;
        this.notifyAll();
        log.log(Level.WARNING, "Unable to break, help!");
    }
}
