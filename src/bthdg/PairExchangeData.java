package bthdg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PairExchangeData {
    private static final double MIN_AVAILABLE_AMOUNT = 0.02; // min amount to start
    private static final double MIN_FORK_INCREASE_AMOUNT = 0.002;

    final SharedExchangeData m_sharedExch1;
    final SharedExchangeData m_sharedExch2;
    public List<ForkData> m_forks; // running tasks
    public TopData.TopDataEx m_lastDiff;
    public Utils.AverageCounter m_diffAverageCounter; // diff between exchanges - top1 - top2
    double m_totalIncome;
    int m_runs;
    private boolean m_stopRequested;
    public boolean m_isFinished;
    public long m_startTime;
    public long m_timestamp;

    public String exchNames() { return m_sharedExch1.m_exchange.m_name + "-" + m_sharedExch2.m_exchange.m_name; }
    public double midCommissionAmount() { return m_sharedExch1.midCommissionAmount() + m_sharedExch2.midCommissionAmount(); }
    public double midDiff() { return m_sharedExch1.midPrice() - m_sharedExch2.midPrice(); }
    private static void log(String s) { Log.log(s); }

    public PairExchangeData(Exchange exch1, Exchange exch2) {
        this(new SharedExchangeData(exch1), new SharedExchangeData(exch2), new ArrayList<ForkData>(), System.currentTimeMillis());
        m_diffAverageCounter = new Utils.AverageCounter(Fetcher.MOVING_AVERAGE); // limit can be different - passed as param for start
    }

    private PairExchangeData(SharedExchangeData s1, SharedExchangeData s2, List<ForkData> forks, long startTime) {
        m_sharedExch1 = s1;
        m_sharedExch2 = s2;
        m_forks = forks;
        m_startTime = startTime;
    }

    public boolean checkState(IterationContext iContext) throws Exception {
        log("PairExchangeData.checkState() we have " + m_forks.size() + " fork(s)");

        if (m_stopRequested) {
            log("stop was requested - stopping all forks...");
            setState(ForkState.STOP);
        } else {
            if (m_forks.isEmpty()) { // start first forks
                queryAccountData();
                if (placeStartForksIfPossible(iContext)) {
                    return false; // do not finish yet
                }
            }
        }

        removeFinishedForks(iContext);
        requestTradesIfNeeded(iContext); // check if forks need trades

        for (ForkData fork : m_forks) {
            fork.checkState(iContext);
        }

        forkIfNeeded();

        placeCloseCrossesIfNeeded(iContext); // or this may increase opposite fork

        m_isFinished = m_forks.isEmpty();
        boolean ret = m_stopRequested && m_isFinished;

        if (m_stopRequested) {
            m_stopRequested = false; // stop request was processed - clear flag
        }

        logState();

        return ret; // no more tasks to execute ?
    }

    private void logState() {
        log("#############################");
        log(getState());
//        log("isFinished=" + m_isFinished + ", forksNum=" + m_forks.size() + ", runs=" + m_runs + ", total=" + m_totalIncome);
        logExch(m_sharedExch1);
        logExch(m_sharedExch2);

        for (ForkData fork : m_forks) {
            log("FORK id=" + fork.m_id +
                    ", pairExData=" + fork.m_pairExData.exchNames() +
                    ", direction=" + fork.m_direction +
                    ", amount=" + format(fork.m_amount) +
                    ", state=" + fork.m_state +
                    ", live=" + fork.getLiveTime());
            logCross(fork.m_openCross,  "Open ");
            logCross(fork.m_closeCross, "Close");
        }
    }

    private static void logExch(SharedExchangeData exch) {
        AccountData account = exch.m_account;
        TopData top = exch.m_lastTop;
        String topStr = (top != null)
                ? "bid=" + format(top.m_bid) + "; ask=" + format(top.m_ask) + "; bidAskDiff=" + format(top.m_ask-top.m_bid)
                : "<no top data>";
        log("exch " + Utils.padRight(exch.m_exchange.toString(), 8) + ": " + topStr +
                "; available: btc=" + format(account.available(Currency.BTC)) + ", usd=" + format(account.available(Currency.USD)) +
                "; allocated: btc=" + format(account.allocated(Currency.BTC)) + ", usd=" + format(account.allocated(Currency.USD)));
    }

    private void logCross(CrossData cross, String crossSide) {
        if (cross != null) {
            log(" " + crossSide + " Cross: buy " + cross.m_buyExch.m_exchange +
                    " -> sell " + cross.m_sellExch.m_exchange +
                    ", state=" + cross.m_state +
                    ", live=" + cross.getLiveTime());
            log("  Buy  order: " + cross.m_buyOrder);
            log("  Sell order: " + cross.m_sellOrder);
        }
    }

    private void removeFinishedForks(IterationContext iContext) throws Exception {
        List<ForkData> finishForks = null;
        for (ForkData fork : m_forks) {
            boolean toFinish = fork.m_state.preCheckState(iContext, fork);
            if (toFinish) {
                log("got fork to finish: " + fork);
                if (finishForks == null) {
                    finishForks = new ArrayList<ForkData>();
                }
                finishForks.add(fork);
            }
        }
        if (finishForks != null) {
            log("collected " + finishForks.size() + " forks to finish");
            for (ForkData fork : finishForks) {
                m_forks.remove(fork);
            }
            if (!m_stopRequested) {
                placeStartForksIfPossible(iContext);
            }
        }
    }

    private boolean placeStartForksIfPossible(final IterationContext iContext) throws Exception {
        log("placeStartForksIfPossible()");
        if( !doWithFreshTopData(iContext, new Runnable() {
            @Override public void run() {
                if (placeStartFork(ForkDirection.DOWN) && placeStartFork(ForkDirection.UP)) {
                    iContext.delay(0); // forks placed - do without delay
                } else {
                    // not placed - e.g. not enough of money on account
                    iContext.delay(10000);
                }
            }
        })) { // no fresh data to start - wait
            iContext.delay(5000);
            return true;
        }
        return false;
    }

    private boolean placeStartFork(ForkDirection direction) {
        double amount = calcAmount(direction);
        log(" placeStartFork(direction=" + direction + ") amount=" + amount);
        if (amount >= OrderData.MIN_ORDER_QTY) {
            ForkData newFork = new ForkData(this, direction, amount);
            log("  added new Fork: " + newFork);
            m_forks.add(newFork);
            return true;
        }
        log("  not enough amount=" + amount + " to start new fork");
        return false;
    }

    private void placeCloseCrossesIfNeeded(final IterationContext iContext) throws Exception {
        for (final ForkData fork : m_forks) {
            if (fork.m_state == ForkState.OPEN_CROSS_EXECUTED) {
                log("pair exchange [" + exchNames() + "]: fork is OPEN_CROSS_EXECUTED. placing close cross on " + fork);
                doWithFreshTopData(iContext, new Runnable() {
                    @Override public void run() {
                        placeCloseCrossIfNeeded(iContext, fork);
                    }
                });
            }
        }
    }

    private void placeCloseCrossIfNeeded(IterationContext iContext, ForkData fork) {
        log("pair exchange [" + exchNames() + "]: placeCloseCrossIfNeeded on " + fork);
        ForkDirection direction = fork.m_direction;
        ForkDirection opposite = direction.opposite();
        double amount = calcAmount(opposite);
        if (amount > MIN_FORK_INCREASE_AMOUNT) {
            // search other fork to increase
            for (final ForkData otherFork : m_forks) {
                ForkDirection otherDirection = otherFork.m_direction;
                if ((direction != otherDirection) && otherFork.isNotStarted()) {
                    log(" looks not needed to placeCloseCross. existing can be increased. " + fork);
                    if (otherFork.increaseOpenAmount(iContext, this)) {
                        fork.stop();
                        amount = 0; // flag that no need to place separate close crosses
                        break;
                    }
                } // todo: check if other fork has open cross executed - then just cache out - no need to wait. but qty can be different !
            }
        }
        if (amount > OrderData.MIN_ORDER_QTY) {
            log(" placeCloseCross(amount="+amount+") on " + fork);
            fork.placeCloseCross(amount);
        }
    }

    private void forkIfNeeded() {
        List<ForkData> forked = null;
        for (ForkData fork : m_forks) {
            ForkData fork3 = fork.forkIfNeeded();
            if (fork3 != null) {
                if (forked == null) {
                    forked = new ArrayList<ForkData>();
                }
                forked.add(fork3);
            }
        }
        if (forked != null) {
            m_forks.addAll(forked);
        }
    }

    double calcAmount(ForkDirection direction) {
        SharedExchangeData buyExch = direction.buyExch(this);
        SharedExchangeData sellExch = direction.sellExch(this);

        AccountData buyAcct = buyExch.m_account;
        AccountData sellAcct = sellExch.m_account;

        double usd = buyAcct.availableUsd();
        double btc = sellAcct.availableBtc();

        log("buy exch  " + buyExch.m_exchange + ": availableUsd=" + format(usd));
        log("sell exch " + sellExch.m_exchange + ": availableBtc=" + format(btc));

        double maxPrice = Math.max(m_sharedExch1.m_lastTop.m_ask, m_sharedExch2.m_lastTop.m_ask); // ASK > BID
        double btcFromUsd = usd / maxPrice;
        log("  maxPrice=" + format(maxPrice) + ", btcFromUsd=" + format(btcFromUsd));

        double amount = Math.min(btc, btcFromUsd) * 0.95;
        amount = Utils.fourDecimalDigits(amount); // round amount
        log("   finally amount=" + format(amount));
        return amount;
    }

    private static String format(double usd) {
        return Fetcher.format(usd);
    }

    private void requestTradesIfNeeded(IterationContext iContext) {
        boolean needQueryTrades = false;
        for (ForkData fork : m_forks) {
            if (fork.m_state.needQueryTrades()) { // for now most states need trades queried
                needQueryTrades = true;
                break;
            }
        }
        if (needQueryTrades) {
            iContext.getNewTradesData(m_sharedExch1);
            iContext.getNewTradesData(m_sharedExch2);
        }
    }

    public void maybeStartNewFork() {
        double amount1 = m_sharedExch1.calcAmountToOpen();
        double amount2 = m_sharedExch2.calcAmountToOpen();
        double amount = Math.min(amount1, amount2);
        log("available amount: " + amount);
        if (amount > MIN_AVAILABLE_AMOUNT) {
            log(" we have MIN_AVAILABLE_AMOUNT(=" + MIN_AVAILABLE_AMOUNT + ") - adding brand new Fork");
            ForkData newFork = new ForkData(this, null, 0); // todo: pass amount to constructor
            m_forks.add(newFork);
        } else {
            log(" not reached MIN_AVAILABLE_AMOUNT - NO new Fork");
        }
    }

    public void onTopsLoaded(TopDatas topDatas) {
        m_lastDiff = topDatas.calculateDiff(); // top1 - top2
        if (m_lastDiff != null) {
            m_diffAverageCounter.justAdd(System.currentTimeMillis(), m_lastDiff.m_mid);
        }
    }

    public void setState(ForkState error) {
        for (ForkData fork : m_forks) {
            fork.setState(error);
        }
    }

    public void addIncome(double earnThisRun) {
        m_totalIncome += earnThisRun;
        m_runs++;
        log(" earnIncome=" + format(m_totalIncome) + ", runs=" + m_runs);
    }

    public void stop() {
        m_stopRequested = true;
    }

    public String getState() {
        return (m_isFinished
                    ? "FINISHED"
                    : "RUNNING " + m_forks.size() + " forks") +
                ", runs=" + m_runs +
                ", total=" + format(m_totalIncome) +
                ", live=" + Utils.millisToDHMSStr(System.currentTimeMillis() - m_startTime);
    }

    public String getForksState() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0, m_forksSize = m_forks.size(); i < m_forksSize; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            ForkData fork = m_forks.get(i);
            fork.appendState(sb);
        }
        sb.append("]");
        return sb.toString();
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("PairExchange[totalIncome=").append(m_totalIncome);
        sb.append("; runs=").append(m_runs);
        sb.append("; shared1=");
        m_sharedExch1.serialize(sb);
        sb.append("; shared2=");
        m_sharedExch2.serialize(sb);
        sb.append("; forks=[");
        serializeForks(sb);
        sb.append("]; lastDiff=");
        if (m_lastDiff != null) {
            m_lastDiff.serialize(sb);
        }
        sb.append("; diffAvgCntr=");
        m_diffAverageCounter.serialize(sb);
        sb.append("; stopRequested=").append(m_stopRequested);
        sb.append("; isFinished=").append(m_isFinished);
        sb.append("; startTime=").append(m_startTime);
        sb.append("]");
        return sb.toString();
    }

    private void serializeForks(StringBuilder sb) {
        for (ForkData fork: m_forks) {
            fork.serialize(sb);
            sb.append("; ");
        }
    }

    public static PairExchangeData deserialize(Deserializer deserializer) throws IOException {
        deserializer.readObjectStart("PairExchange");
        deserializer.readPropStart("totalIncome");
        String totalIncome = deserializer.readTill("; ");
        deserializer.readPropStart("runs");
        String runs = deserializer.readTill("; ");
        deserializer.readPropStart("shared1");
        SharedExchangeData sh1 = SharedExchangeData.deserialize(deserializer);
        deserializer.readStr("; ");
        deserializer.readPropStart("shared2");
        SharedExchangeData sh2 = SharedExchangeData.deserialize(deserializer);
        deserializer.readStr("; ");
        deserializer.readPropStart("forks");
        List<ForkData> forks = deserializeForks(deserializer);
        deserializer.readStr("; ");

        deserializer.readPropStart("lastDiff");
        TopData.TopDataEx lastDif = TopData.TopDataEx.deserialize(deserializer);
        deserializer.readPropStart("diffAvgCntr");
        Utils.AverageCounter diffAvgCntr = Utils.AverageCounter.deserialize(deserializer);
        deserializer.readPropStart("stopRequested");
        String stopRequested = deserializer.readTill("; ");
        deserializer.readPropStart("isFinished");
        String isFinished = deserializer.readTill("; ");
        deserializer.readPropStart("startTime");
        String startTime = deserializer.readTill("]");

        PairExchangeData ret = new PairExchangeData(sh1, sh2, forks, 0);
        ret.m_lastDiff = lastDif;
        ret.m_diffAverageCounter = diffAvgCntr;
        ret.m_totalIncome = Double.parseDouble(totalIncome);
        ret.m_runs = Integer.parseInt(runs);
        ret.m_stopRequested = Boolean.parseBoolean(stopRequested);
        ret.m_isFinished = Boolean.parseBoolean(isFinished);
        ret.m_startTime = Long.parseLong(startTime);

        for (ForkData fork : forks) {
            fork.postDeserialize(ret);
        }
        return ret;
    }

    private static List<ForkData> deserializeForks(Deserializer deserializer) throws IOException {
        // [[Fork]; [Fork]; [Fork]; ]
        deserializer.readObjectStart();
        List<ForkData> ret = new ArrayList<ForkData>();
        while (true) {
            if (deserializer.readIf("]")) {
                return ret;
            }
            ForkData fork = ForkData.deserialize(deserializer);
            ret.add(fork);
            deserializer.readStr("; ");
        }
    }

    public SharedExchangeData getSharedExch(Exchange exch) {
        if (m_sharedExch1.m_exchange == exch) {
            return m_sharedExch1;
        }
        if (m_sharedExch2.m_exchange == exch) {
            return m_sharedExch2;
        }
        throw new RuntimeException("unable to get get SharedExch for exch=" + exch);
    }

    public void compare(PairExchangeData other) {
        m_sharedExch1.compare(other.m_sharedExch1);
        m_sharedExch2.compare(other.m_sharedExch2);
        compareForks(m_forks, other.m_forks);
        if (Utils.compareAndNotNulls(m_lastDiff, other.m_lastDiff)) {
            m_lastDiff.compare(other.m_lastDiff);
        }
        m_diffAverageCounter.compare(other.m_diffAverageCounter);
        if (m_totalIncome != other.m_totalIncome) {
            throw new RuntimeException("m_totalIncome");
        }
        if (m_runs != other.m_runs) {
            throw new RuntimeException("m_runs");
        }
        if (m_stopRequested != other.m_stopRequested) {
            throw new RuntimeException("m_stopRequested");
        }
        if (m_isFinished != other.m_isFinished) {
            throw new RuntimeException("m_isFinished");
        }
        if (m_startTime != other.m_startTime) {
            throw new RuntimeException("m_startTime");
        }
    }

    private void compareForks(List<ForkData> forks, List<ForkData> other) {
        if (Utils.compareAndNotNulls(forks, other)) {
            int size = forks.size();
            if (size != other.size()) {
                throw new RuntimeException("forks.size");
            }
            for (int i = 0; i < size; i++) {
                ForkData fork1 = forks.get(i);
                ForkData fork2 = other.get(i);
                if (Utils.compareAndNotNulls(forks, other)) {
                    fork1.compare(fork2);
                }
            }
        }
    }

    public long updateTimestamp() {
        m_timestamp = System.currentTimeMillis();
        return m_timestamp;
    }

    public void queryAccountData() throws Exception {
        m_sharedExch1.queryAccountData();
        m_sharedExch2.queryAccountData();
        // todo: handle if query unsuccessfull
    }

    /** @return true if all top data loaded */
    public boolean doWithFreshTopData(IterationContext iContext, Runnable run) throws Exception {
        TopDatas tops = iContext.getTopsData(this);
        if (tops.bothFresh()) {
            logDiffAverageDelta();
            run.run();
            return true;
        } else {
            log("some exchange top data is not fresh " +
                    "(fresh1=" + tops.top1fresh() + ", fresh2=" + tops.top2fresh() + ") - do nothing");
            return false;
        }
    }

    public void logDiffAverageDelta() {
        double midDiffAverage = m_diffAverageCounter.get();
        double delta = m_lastDiff.m_mid - midDiffAverage;
        log("diff=" + m_lastDiff + ";  avg=" + format(midDiffAverage) + ";  delta=" + format(delta));
    }

    public void addGain(double gain) {
        m_totalIncome += gain;
        m_runs ++;
    }
} // bthdg.PairExchangeData
