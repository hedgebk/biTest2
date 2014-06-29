package bthdg.run;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.util.Utils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class BiAlgo implements Runner.IAlgo {
    private static final double START_LEVEL = 0.0002;
    private static final long MOVING_AVERAGE = Utils.toMillis(5, 28); // TODO: make exchange_pair dependent
    private static final long MIN_ITERATION_TIME = 3000;

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
            long start = System.currentTimeMillis();
            int iterationCount = 0;
            while (m_stopRunnable == null) {
                long millis = System.currentTimeMillis();
                log("iteration " + iterationCount + " ---------------------- elapsed: " + Utils.millisToDHMSStr(millis - start));
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
// TODO: enable later
//                AccountData accountData = Fetcher.fetchAccount(exchange);
//                m_startAccountMap.put(exchange, accountData);
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

        int maxExchNameLen = getMaxExchNameLen();
        for (Exchange exchange : m_exchanges) { // log loaded tops
            TopDataHolder topDataHolder = m_mdStorage.get(exchange, m_pair);
            log(Utils.padRight(exchange.name(), maxExchNameLen) + ": " + topDataHolder.m_topData);
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

    public int getMaxExchNameLen() {
        if (m_maxExchNameLen == 0) { // calc once
            for (Exchange exchange : m_exchanges) {
                m_maxExchNameLen = Math.max(m_maxExchNameLen, exchange.name().length());
            }
        }
        return m_maxExchNameLen;
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
        private MktPoint m_start;

        public String name() { return m_exchangesPair.name(); }

        private ExchangesPairData(ExchangesPair exchangesPair) {
            m_exchangesPair = exchangesPair;
            m_diffAverageCounter = new Utils.AverageCounter(MOVING_AVERAGE);
        }

        public void run() {
            // TODO: introduce exchangesPair.priceFormatter - since precisions can be different on exchanges
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
            log(name() + " diff=" + e1.roundPriceStr(diff, m_pair) + // TODO: introduce roundPriceStrPlus - to add some precision
                    ", avgDiff="+ e1.roundPriceStr(avgDiff, m_pair) +
                    ", diffDiff="+ e1.roundPriceStr(diffDiff, m_pair) +
                    ", bidAskDiff="+ e1.roundPriceStr(bidAskDiff, m_pair));
            m_timeDiffs.add(new TimeDiff(System.currentTimeMillis(), diff));

            MktPoint mktPoint = new MktPoint(td1, td2, avgDiff, diffDiff, bidAskDiff);
            if (m_start == null) {
                checkForNew(mktPoint);
            } else {
                checkForEnd(mktPoint);
            }
        }

        private void checkForEnd(MktPoint mktPoint) {
            if(((mktPoint.m_diffDiff > 0) && (m_start.m_diffDiff < 0))
               || ((mktPoint.m_diffDiff < 0) && (m_start.m_diffDiff > 0))) { // look for opposite difDiffs
                double gain = mktPoint.gain();
                double midMid = mktPoint.midMid();
                double level = gain / midMid;

                Exchange e1 = m_exchangesPair.m_exchange1;
                log(name() + " E  gain=" + e1.roundPriceStr(gain, m_pair) +
                        ", midMid="+ e1.roundPriceStr(midMid, m_pair) +
                        ", level="+ Utils.format8(level));
                if (level > START_LEVEL) {
                    log(name() + "   GOT END");
                    onEnd(mktPoint);
                    m_start = null;
                }
            }
        }

        private void checkForNew(MktPoint mktPoint) {
            double gain = mktPoint.gain();
            double midMid = mktPoint.midMid();
            double level = gain / midMid;

            Exchange e1 = m_exchangesPair.m_exchange1;
            log(name() + " S  gain=" + e1.roundPriceStr(gain, m_pair) +
                    ", midMid="+ e1.roundPriceStr(midMid, m_pair) +
                    ", level="+ Utils.format8(level));
            if (level > START_LEVEL) {
                log(name() + "   GOT START");
                m_start = mktPoint;
            }
        }

        private void onEnd(MktPoint end) {
            boolean startUp = !m_start.aboveAverage();
            boolean endUp = !end.aboveAverage();
            double balance1 = startUp
                    ?  m_start.m_td2.m_bid - end.m_td2.m_ask
                    : -m_start.m_td2.m_ask + end.m_td2.m_bid;
            double balance2 = startUp
                    ? -m_start.m_td2.m_ask + end.m_td2.m_bid
                    :  m_start.m_td2.m_bid - end.m_td2.m_ask;
            double balance = balance1 + balance2;

            log(name() + "%%%%%%%%%");
            log(name() + "%%%%%% START up=" + startUp + "; top1=" + m_start.m_td1 + "; top2=" + m_start.m_td2);
            log(name() + "%%%%%% END   up=" + endUp +   "; top1=" + end.m_td1 +     "; top2=" + end.m_td2    );
            log(name() + "%%%%%%");
            log(name() + "%%%%%% START buy " + (startUp ? "2" : "1") + " @ " + (startUp ? m_start.m_td2 : m_start.m_td1).askStr() +
                                   "; sell " + (startUp ? "1" : "2") + " @ " + (startUp ? m_start.m_td1 : m_start.m_td2).bidStr() );
            log(name() + "%%%%%% END   buy " + (startUp ? "1" : "2") + " @ " + (startUp ? end.m_td1     : end.m_td2    ).askStr() +
                                   "; sell " + (startUp ? "2" : "1") + " @ " + (startUp ? end.m_td2     : end.m_td1    ).bidStr() );
            log(name() + "%%%%%%");
            log(name() + "%%%%%% balance1=" + balance1 + "; balance2=" + balance2 + "; balance=" + balance);
            log(name() + "%%%%%%");
        }

        // pre-calculated top data relations
        private class MktPoint {
            final TopData m_td1;
            final TopData m_td2;
            final double m_avgDiff;
            // cached
            final double m_diffDiff;
            final double m_bidAskDiff;

            public boolean aboveAverage() { return (m_diffDiff > 0); }

            public MktPoint(TopData td1, TopData td2, double avgDiff, double diffDiff, double bidAskDiff) {
                m_td1 = td1;
                m_td2 = td2;
                m_avgDiff = avgDiff;
                m_diffDiff = diffDiff;
                m_bidAskDiff = bidAskDiff;
            }

            public double gain() {
                return Math.abs(m_diffDiff) - m_bidAskDiff / 2;
            }

            public double midMid() {
                double mid1 = m_td1.getMid();
                double mid2 = m_td2.getMid();
                return (mid1 + mid2) / 2;
            }
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
