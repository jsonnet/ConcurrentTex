package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.cli.Unit;
import com.pseuco.np19.project.launcher.parser.Parser;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This class will create a ThreadPool and submit the tasks to the threads
 * The paragraphs will be assign to workers and if a segement is ready a worker
 * will get the task to handle the rendering of the pages...
 * <p>
 * Finally this thread will print the pages to the document or will take care of
 * printing an ErrorPage!
 */
public class UnitHandler extends Thread {

    private final ExecutorService executor;
    private final Unit unit;
    private final UnitData udata;
    private final ConcurrentDocument document;
    private final Parser parser;


    public UnitHandler(Unit unit) {
        // Creates ThreadPool for every Unit with max of all available logical cores
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        //int cores = Runtime.getRuntime().availableProcessors(); //help
        //this.executor = (cores == 1) ? Executors.newFixedThreadPool(cores) : Executors.newCachedThreadPool();
        //this.executor = (cores == 1) ? Executors.newFixedThreadPool(cores) : Executors.newFixedThreadPool(cores-1);

        this.unit = unit;
        this.udata = new UnitData(unit.getConfiguration(), unit.getPrinter(), executor);

        //create an empty document
        this.document = new ConcurrentDocument(udata, executor);

        parser = new Parser(unit.getInputReader(), document);
    }

    @Override
    public void run() {
        //Start a Thread to parse...
        Thread parserThread = new Thread(() -> {
            try {
                parser.buildDocument();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        // We are starting the parsing, but no need too join, as it signals with finish call!
        parserThread.start();

        while ((!document.isFinished() && !udata.isUnableToBreak()) || (udata.getPrintedPages() != document.getSegmentCounter()) && !udata.isUnableToBreak()) {
            synchronized (udata) {
                try {
                    udata.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        //Moved this down here because while loop now stops if unableToBreak and if it was before even skipts the while...
        //So this is the correct time to handle it!
        if (udata.isUnableToBreak()) {
            System.out.println("unable to break. SHUTDOWN initialized!");
            parser.abort();
            executor.shutdownNow();
            try {
                this.unit.getPrinter().printErrorPage();
                this.unit.getPrinter().finishDocument();
            } catch (IOException e) {
                System.out.println("Could not print Error page");
            }
            // do not do unnecessary work if a paragraph failed to typeset
            return;
        }

        // This will drop all new submits at this point, but all possible should be done by now
        executor.shutdown();

        //Now that every task is done, all pages have been printed
        try {
            this.unit.getPrinter().finishDocument();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
