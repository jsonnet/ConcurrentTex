package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.cli.CLI;
import com.pseuco.np19.project.launcher.cli.CLIException;
import com.pseuco.np19.project.launcher.cli.Unit;

import java.util.List;

public class Rocket {

    public static void main(String[] arguments) {

        try {
            List<Unit> units = CLI.parseArgs(arguments);
            if (units.isEmpty()) {
                CLI.printUsage(System.out);
            }

            UnitHandler[] unitThreads = new UnitHandler[units.size()];

            // For every Unit start a new Thread that will process it
            for (int i=0; i<units.size(); i++) {
                unitThreads[i] = new UnitHandler(units.get(i));
                unitThreads[i].start();
            }

            for(UnitHandler uh : unitThreads){
                uh.join();
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
