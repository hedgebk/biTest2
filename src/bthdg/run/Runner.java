package bthdg.run;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.util.ConsoleReader;
import bthdg.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Runner {
    private List<IAlgo> m_algos = new ArrayList<IAlgo>();

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    public static void main(String[] args) {
        Fetcher.LOG_LOADING = false;

        System.out.println("Runner. Started");
        long millis = System.currentTimeMillis();
        System.out.println("timeMills: " + millis);
        long maxMemory = Runtime.getRuntime().maxMemory();
        System.out.println("maxMemory: " + maxMemory + ", k:" + (maxMemory /= 1024) + ": m:" + (maxMemory /= 1024));

        new Runner().run();

        System.out.println("done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));
    }

    private void run() {
        ConsoleReader consoleReader = new IntConsoleReader();
        consoleReader.start(); // will start separate thread

        m_algos.add(new BiAlgo());
        runAlgos();
    }

    private void stopAll() throws InterruptedException {
        System.out.println("~~~~~~~~~~~ stopRequested ~~~~~~~~~~~");
        final AtomicInteger counter = new AtomicInteger();
        for(IAlgo algo: m_algos) {
            synchronized (counter) {
                counter.incrementAndGet();
            }
            algo.stop(new Runnable() {
                @Override public void run() {
                    synchronized (counter) {
                        int value = counter.decrementAndGet();
                        if(value == 0) {
                            counter.notifyAll();
                        }
                    }
                }
            });
        }
        synchronized (counter) {
            if(counter.get() != 0) {
                log("waiting until all stop");
                counter.wait();
            }
            log("all reported that stopped");
        }
    }

    private void runAlgos() {
        for(IAlgo algo: m_algos) {
            algo.runAlgo();
        }
    }

    public interface IAlgo {
        void runAlgo();
        void stop(Runnable run);
    }

    private class IntConsoleReader extends ConsoleReader {
        @Override protected void beforeLine() {}

        @Override protected boolean processLine(String line) throws Exception {
            if (line.equals("stop")) {
                stopAll();
                return true;
            } else {
                System.out.println("~~~~~~~~~~~ command ignored: " + line);
            }
            return false;
        }
    }
}
