package bthdg.run;

import bthdg.Exchange;
import bthdg.Fetcher;
import bthdg.Log;
import bthdg.util.Utils;
import bthdg.exch.Pair;
import bthdg.exch.TopData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class BiAlgo implements Runner.IAlgo {
    private static final long MIN_ITERATION_TIME = 3000;

    private static Exchange[] s_exchanges = new Exchange[] {Exchange.BTCN, Exchange.OKCOIN, Exchange.HUOBI};
    private static List<ExchangesPair> s_exchPairs = mkExchPairs();
    private static List<ExchangesPairData> s_exchPairsDatas = mkExchPairsDatas();
    private static MdStorage s_mdStorage = new MdStorage();
    private Runnable m_stopRunnable;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    private static List<ExchangesPairData> mkExchPairsDatas() {
        List<ExchangesPairData> ret = new ArrayList<ExchangesPairData>(s_exchPairs.size());
        for (ExchangesPair exchangesPair : s_exchPairs) {
            ret.add(new ExchangesPairData(exchangesPair));
        }
        return ret;
    }

    private static List<ExchangesPair> mkExchPairs() {
        List<ExchangesPair> ret = new ArrayList<ExchangesPair>();
        for (Exchange exch1 : s_exchanges) {
            boolean foundSelf = false;
            for (Exchange exch2 : s_exchanges) {
                if (foundSelf) {
                    ret.add(new ExchangesPair(exch1, exch2));
                } else {
                    if (exch1 == exch2) {
                        foundSelf = true;
                    }
                }
            }
        }
        return ret;
    }

    public BiAlgo() {
    }

    @Override public void runAlgo() {
        new Thread("Bialgo") {
            @Override public void run() {
                runAlgoInt();
            }
        }.start();
    }

    private void runAlgoInt() {
        try {
            int iterationCount = 0;
            while (m_stopRunnable == null) {
                long millis = System.currentTimeMillis();
                log("iteration " + iterationCount + " ----------------------");
                runIteration(iterationCount);
                sleepIfNeeded(millis);
                iterationCount++;
            }
            m_stopRunnable.run();
        } catch (Exception e) {
            Log.err("runAlgo error: " + e, e);
        }
    }

    private void sleepIfNeeded(long start) throws InterruptedException {
        long took = System.currentTimeMillis() - start;
        log(" iteration took " + took + " ms");
        if(took < MIN_ITERATION_TIME) {
            long toSleep = MIN_ITERATION_TIME - took;
            log("  to sleep " + toSleep + " ms...");
            Thread.sleep(toSleep);
        }
    }

    @Override public void stop(Runnable runnable) {
        m_stopRunnable = runnable;
    }

    private void runIteration(int iterationCount) throws InterruptedException {
        int length = s_exchanges.length;
        final AtomicInteger counter = new AtomicInteger(length);
        for (int i = length - 1; i >= 0; i--) {
            final Exchange exchange = s_exchanges[i];
            Runnable r = new Runnable() {
                @Override public void run() {
                    runForOnExch(counter, exchange);
                }
            };
            new Thread(r, exchange.name() + "_" + iterationCount).start();
        }
        synchronized (counter) {
            if(counter.get() != 0) {
                counter.wait();
            }
        }

        int max = 0;
        for (int i = length - 1; i >= 0; i--) {
            max = Math.max(max, s_exchanges[i].name().length());
        }
        for (int i = length - 1; i >= 0; i--) {
            Exchange exchange = s_exchanges[i];
            TopDataHolder topDataHolder = s_mdStorage.get(exchange, Pair.BTC_CNH);
            log(Utils.padRight(exchange.name(), max) + ": " + topDataHolder.m_topData);
        }

        runForPairs();
    }

    private void runForPairs() {
        for (ExchangesPairData exchPairsData : s_exchPairsDatas) {
            exchPairsData.run();
        }
    }

    private void runForOnExch(AtomicInteger counter, Exchange exchange) {
        try {
            TopData topData = Fetcher.fetchTop(exchange, Pair.BTC_CNH);
//            log(exchange.name() + ":  " + topData);
            s_mdStorage.put(exchange, Pair.BTC_CNH, topData);
        } catch (Exception e) {
            err("runForOnExch error: " + e, e);
            e.printStackTrace();
        } finally {
            synchronized (counter) {
                int value = counter.decrementAndGet();
                if (value == 0) {
                    counter.notifyAll();
                }
            }
        }
    }

    private static class ExchangesPair {
        public final Exchange m_exchange1;
        public final Exchange m_exchange2;

        private ExchangesPair(Exchange exchange1, Exchange exchange2) {
            m_exchange1 = exchange1;
            m_exchange2 = exchange2;
        }

        public String name() {
            return m_exchange1.name() + "_" + m_exchange2.name();
        }
    }

    private static class ExchangesPairData {
        private static final long MOVING_AVERAGE = 20 * 60 * 1000;

        public final ExchangesPair m_exchangesPair;
        private final Utils.AverageCounter m_diffAverageCounter;
        private final List<TimeDiff> m_timeDiffs = new ArrayList<TimeDiff>();

        public String name() { return m_exchangesPair.name(); }

        private ExchangesPairData(ExchangesPair exchangesPair) {
            m_exchangesPair = exchangesPair;
            m_diffAverageCounter = new Utils.AverageCounter(MOVING_AVERAGE);
        }

        public void run() {
            Exchange e1 = m_exchangesPair.m_exchange1;
            Exchange e2 = m_exchangesPair.m_exchange2;
            TopData td1 = s_mdStorage.get(e1, Pair.BTC_CNH).m_topData;
            TopData td2 = s_mdStorage.get(e2, Pair.BTC_CNH).m_topData;
            double mid1 = td1.getMid();
            double mid2 = td2.getMid();
            double diff = mid1 - mid2;
            log(name()+" diff="+diff);
            m_timeDiffs.add(new TimeDiff(System.currentTimeMillis(), diff));
        }
    }

    public static class TimeDiff {
        public final long m_millis;
        public final double m_diff;

        public TimeDiff(long millis, double diff) {
            m_millis = millis;
            m_diff = diff;
        }
    }
}
