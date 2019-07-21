package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.cli.Unit;
import com.pseuco.np19.project.launcher.parser.Parser;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This class will create a ThreadPool, start the parser and then wait until the tasks
 * (i.e. all Paragraphs have been processed and pages are printed) are done
 * In case of an error (i.e. unableToBreak) the thread will print the error page and end the unit asap
 * Finally this thread will shutdown the ThreadPool and finish the document (i.e. close the writer)
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
        this.unit = unit;
        this.udata = new UnitData(unit.getConfiguration(), unit.getPrinter(), executor);

        //create an empty document
        this.document = new ConcurrentDocument(udata, executor);
        parser = new Parser(unit.getInputReader(), document);
    }

    @Override
    public void run() {
        // build a thread to parse the unit
        Thread parserThread = new Thread(() -> {
            try {
                parser.buildDocument();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        // We are starting the parsing, but no need too join, as it signals with finish call!
        parserThread.start();

        // Here we wait (i.e. let the thread idle and give it's CPU time to the threadpool) for the whole Unit to be worked and printed
        while ((!document.isFinished() && !udata.isUnableToBreak()) || (udata.getPrintedPages() != document.getSegmentCounter()) && !udata.isUnableToBreak()) {
            synchronized (udata) {
                try {
                    udata.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        // If we encountered a problem above this handles the unableToBreak error by stopping every task and printing the error page
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
            // here we end this unit
            return;
        }

        // This will drop all new submits at this point, but all possible are done by now! Just so the executor shutdowns all threads
        executor.shutdown();

        // Now with everything being done we can finish the document and sign off
        try {
            this.unit.getPrinter().finishDocument();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
