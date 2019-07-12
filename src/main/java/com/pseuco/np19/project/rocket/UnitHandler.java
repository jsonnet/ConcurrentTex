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
 *
 * Finally this thread will print the pages to the document or will take care of
 * printing an ErrorPage!
 */
public class UnitHandler extends Thread {

    private ExecutorService executor;
    private Unit unit;
    private UnitData udata;
    private ConcurrentDocument document;

    public UnitHandler(Unit unit){
        // Creates ThreadPool for every Unit with max of all available logical cores
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.unit = unit;
        this.udata = new UnitData(unit.getConfiguration(), unit.getPrinter());

        //create an empty document
        this.document = new ConcurrentDocument();



    }

    @Override
    public void run() {
        //Start a Thread to parse...
        Thread parserThread = new Thread(() -> {
            try {
                Parser.parse(unit.getInputReader(), document);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        parserThread.start();

        while(!(document.isFinished() && document.isJobsEmpty()) && !udata.isUnableToBreak()){
            //System.out.println("hallo ich tue etwas");
            executor.submit(new ParagraphThread(udata, document.getJob(), executor));
        }

        System.out.println("Ja lol ey");
        if(udata.isUnableToBreak()){
            executor.shutdownNow();
        }

        //FIXME: what if new render submit by a worker needs to be pushed?
        // This will drop all new submits at this point
        //executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Now that every task is done, print all pages that have been rendered
        try {
            this.unit.getPrinter().printPages(udata.getPages());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
