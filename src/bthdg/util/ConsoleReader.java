package bthdg.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public abstract class ConsoleReader extends Thread {
    protected abstract void beforeLine();
    protected abstract boolean processLine(String line) throws Exception;

    public ConsoleReader() {
        super("ConsoleReader");
    }

    public void run() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            while(!isInterrupted()) {
                beforeLine();
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                try {
                    boolean exit = processLine(line);
                    if(exit) {
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("error for command '"+line+"': " + e);
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("error: " + e);
            e.printStackTrace();
        }
    }
}
