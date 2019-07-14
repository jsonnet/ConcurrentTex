package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.cli.Unit;
import com.pseuco.np19.project.launcher.parser.Parser;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This class will create a ThreadPool and submit the tasks to the threads
 * The paragraphs will be assign to workers and if a segement is ready a worker
 * will get the task to handle the rendering of the pages...
 * <p>
 * Finally this thread will print the pages to the document or will take care of
 * printing an ErrorPage!
 */
public class UnitHandler extends Thread {

    private ExecutorService executor;
    private Unit unit;
    private UnitData udata;
    private ConcurrentDocument document;


    public UnitHandler(Unit unit) {
        // Creates ThreadPool for every Unit with max of all available logical cores
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.unit = unit;
        this.udata = new UnitData(unit.getConfiguration(), unit.getPrinter());

        //create an empty document
        this.document = new ConcurrentDocument();
    }

    @Override
    public synchronized void run() {
        //Start a Thread to parse...
        Thread parserThread = new Thread(() -> {
            try {
                Parser.parse(unit.getInputReader(), document);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        parserThread.start();

        while (!(document.isFinished() && document.isJobsEmpty()) && !udata.isUnableToBreak()) {
            //System.out.println("hallo ich tue etwas");
            //FIXME maybe need to check for getJob returning null!
            executor.submit(new ParagraphThread(udata, document.getJob(), executor));
        }

        //System.out.println("Ja lol ey");
        if (udata.isUnableToBreak()) {
            System.out.println("unable to break. SHUTDOWN initialized!");
            executor.shutdownNow();
            //FIXME the following while will break with this!
        }

        while (udata.getSegmentCount() != document.getSegmentCounter()) {
            //System.out.println("not yet");
            try {
                Thread.sleep(10);  //TODO: hier muss gewartet werden. Das hier ist nicht ordentlich und die Bedingung reicht nicht ganz aus
            } catch (InterruptedException e) {
                System.out.println("Handler waiting interrupted!");
                e.printStackTrace();
                break;
            }
        }
        //FIXME: what if new render submit by a worker needs to be pushed?
        // This will drop all new submits at this point
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Now that every task is done, print all pages that have been rendered
        try {
            //FIXME with alice I noticed sometimes it does not print the last segment!! Maybe because of shutdown even its not finished yet
            this.unit.getPrinter().printPages(udata.getPages());
            this.unit.getPrinter().finishDocument();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
