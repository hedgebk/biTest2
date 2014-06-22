package bthdg.run;

import bthdg.AccountData;
import bthdg.Exchange;
import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.util.Utils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class BiAlgo implements Runner.IAlgo {
    private static final long MIN_ITERATION_TIME = 3000;
    private static final long MOVING_AVERAGE = (9 * 60 + 28) * 1000;

    private final Exchange[] m_exchanges = new Exchange[] {Exchange.BTCN, Exchange.OKCOIN/*, Exchange.HUOBI*/};
    private final Pair m_pair = Pair.BTC_CNH;

    private List<ExchangesPair> m_exchPairs = mkExchPairs();
    private List<ExchangesPairData> m_exchPairsDatas = mkExchPairsDatas();
    private MdStorage m_mdStorage = new MdStorage();
    private Runnable m_stopRunnable;
    private int m_maxExchNameLen; // for formatting
    private Map<Exchange, AccountData> m_startAccountMap = new HashMap<Exchange, AccountData>();

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }
    @Override public void stop(Runnable runnable) { m_stopRunnable = runnable; }

    public BiAlgo() {}

    @Override public void runAlgo() {
        new Thread("Bialgo") {
            @Override public void run() {
                runAlgoInt();
            }
        }.start();
    }

    private void runAlgoInt() {
        try {
            runInit();
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
            err("runAlgo error: " + e, e);
        }
    }

    private void runInit() throws Exception {
        doInParallel("getAccountData", new IExchangeRunnable() {
            @Override public void run(Exchange exchange) throws Exception {
                Properties keys = BaseExch.loadKeys();
                exchange.init(keys);
                AccountData accountData = Fetcher.fetchAccount(exchange);
                m_startAccountMap.put(exchange, accountData);
            }
        });
    }

    private void runIteration(int iterationCount) throws Exception {
        doInParallel("getMktData", new IExchangeRunnable() {
            @Override public void run(Exchange exchange) throws Exception {
                TopData topData = Fetcher.fetchTop(exchange, m_pair);
                m_mdStorage.put(exchange, m_pair, topData);
            }
        });

        int length = m_exchanges.length;
        if(m_maxExchNameLen == 0) { // calc once
            for (int i = length - 1; i >= 0; i--) {
                m_maxExchNameLen = Math.max(m_maxExchNameLen, m_exchanges[i].name().length());
            }
        }
        for (int i = length - 1; i >= 0; i--) {
            Exchange exchange = m_exchanges[i];
            TopDataHolder topDataHolder = m_mdStorage.get(exchange, m_pair);
            log(Utils.padRight(exchange.name(), m_maxExchNameLen) + ": " + topDataHolder.m_topData);
        }

        runForPairs();
    }

    private void doInParallel(String name, final IExchangeRunnable exchangeRunnable) throws InterruptedException {
        int length = m_exchanges.length;
        final AtomicInteger counter = new AtomicInteger(length);
        for (int i = length - 1; i >= 0; i--) {
            final Exchange exchange = m_exchanges[i];
            Runnable r = new Runnable() {
                @Override public void run() {
                    try {
                        exchangeRunnable.run(exchange);
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
            };
            new Thread(r, exchange.name() + "_" + name).start();
        }
        synchronized (counter) {
            if(counter.get() != 0) {
                counter.wait();
            }
        }
        return;
    }

    private void runForPairs() {
        for (ExchangesPairData exchPairsData : m_exchPairsDatas) {
            exchPairsData.run();
        }
    }

    private List<ExchangesPairData> mkExchPairsDatas() {
        List<ExchangesPairData> ret = new ArrayList<ExchangesPairData>(m_exchPairs.size());
        for (ExchangesPair exchangesPair : m_exchPairs) {
            ret.add(new ExchangesPairData(exchangesPair));
        }
        return ret;
    }

    private List<ExchangesPair> mkExchPairs() {
        List<ExchangesPair> ret = new ArrayList<ExchangesPair>();
        for (Exchange exch1 : m_exchanges) {
            boolean foundSelf = false;
            for (Exchange exch2 : m_exchanges) {
                if (foundSelf) {
                    ret.add(new ExchangesPair(exch1, exch2));
                } else if (exch1 == exch2) {
                    foundSelf = true;
                }
            }
        }
        return ret;
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

    /** **************************************************************************************** */
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

    private class ExchangesPairData {
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
            TopData td1 = m_mdStorage.get(e1, m_pair).m_topData;
            TopData td2 = m_mdStorage.get(e2, m_pair).m_topData;
            double mid1 = td1.getMid();
            double mid2 = td2.getMid();
            double diff = mid1 - mid2;
            double bidAskDiff1 = td1.getBidAskDiff();
            double bidAskDiff2 = td2.getBidAskDiff();
            double bidAskDiff = bidAskDiff1 + bidAskDiff2;
            double avgDiff = m_diffAverageCounter.add(System.currentTimeMillis(), diff);
            double diffDiff = diff - avgDiff;
            log(name() + " diff=" + Utils.XX_YYYY.format(diff) +
                    ", avgDiff="+ Utils.XX_YYYY.format(avgDiff) +
                    ", diffDiff="+ Utils.XX_YYYY.format(diffDiff) +
                    ", bidAskDiff="+ Utils.XX_YYYY.format(bidAskDiff));
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

    private interface IExchangeRunnable {
        void run(Exchange exchange) throws Exception;
    }
}
