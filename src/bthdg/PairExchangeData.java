package bthdg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PairExchangeData {
    private static final double MIN_AVAILABLE_AMOUNT = 0.02; // min amount to start

    final SharedExchangeData m_sharedExch1;
    final SharedExchangeData m_sharedExch2;
    public final List<ForkData> m_forks;
    public TopData.TopDataEx m_lastDiff;
    public Utils.AverageCounter m_diffAverageCounter;
    private double m_totalIncome;
    private int m_runs;
    private boolean m_stopRequested;
    public boolean m_isFinished;
    public long m_timestamp;

    public String exchNames() { return m_sharedExch1.m_exchange.m_name + "-" + m_sharedExch2.m_exchange.m_name; }

    public PairExchangeData(Exchange exch1, Exchange exch2) {
        this(new SharedExchangeData(exch1), new SharedExchangeData(exch2), new ArrayList<ForkData>());
        m_diffAverageCounter = new Utils.AverageCounter(Fetcher.MOVING_AVERAGE); // limit can be different - passed as param for start
        maybeStartNewFork();
    }

    private PairExchangeData(SharedExchangeData s1, SharedExchangeData s2, List<ForkData> forks) {
        m_sharedExch1 = s1;
        m_sharedExch2 = s2;
        m_forks = forks;
    }

    public boolean checkState(IterationContext iContext) throws Exception {
        System.out.println("PairExchangeData.checkState() we have " + m_forks.size() + " fork(s)");

        if (m_stopRequested) {
            System.out.println("stop was requested");
            setState(ForkState.STOP);
        }

        List<ForkData> finishForks = null;
        for (ForkData fork : m_forks) {
            boolean toFinish = fork.m_state.preCheckState(iContext, fork);
            if (toFinish) {
                System.out.println("got fork to finish: " + fork);
                if (finishForks == null) {
                    finishForks = new ArrayList<ForkData>();
                }
                finishForks.add(fork);
            }
        }
        if (finishForks != null) {
            for (ForkData fork : finishForks) {
                m_forks.remove(fork);
                if (fork.m_state == ForkState.END) {
                    System.out.println("finish fork was in state END");
                    maybeStartNewFork();
                }
            }
        }

        boolean needQueryTrades = false;
        for (ForkData fork : m_forks) {
            if (fork.m_state.needQueryTrades()) { // for now most states need trades queried
                needQueryTrades = true;
            }
        }
        if (needQueryTrades) {
            iContext.getNewTradesData(m_sharedExch1);
            iContext.getNewTradesData(m_sharedExch2);
        }

        for (ForkData fork : m_forks) {
            fork.checkState(iContext);
        }

        m_isFinished = m_forks.isEmpty();
        return m_isFinished; // no more tasks to execute
    }

    public void maybeStartNewFork() {
        double amount1 = m_sharedExch1.calcAmountToOpen();
        double amount2 = m_sharedExch2.calcAmountToOpen();
        double amount = Math.min(amount1, amount2);
        System.out.println("available amount: "+amount);
        if(amount > MIN_AVAILABLE_AMOUNT) {
            System.out.println(" we have MIN_AVAILABLE_AMOUNT - adding brand new Fork");
            m_forks.add(new ForkData(this));
        } else {
            System.out.println(" not reached MIN_AVAILABLE_AMOUNT - NO new Fork");
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
        System.out.println(" earnIncome=" + Fetcher.format(m_totalIncome)+", runs="+m_runs);
    }

    public void stop() {
        m_stopRequested = true;
    }

    public String getState() {
        return m_isFinished ? "FINISHED" : "RUNNING "+m_forks.size() + " forks";
    }

    public String getForksState() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0, m_forksSize = m_forks.size(); i < m_forksSize; i++) {
            if(i> 0) {
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
        List<ForkData> forks = readForks(deserializer);
        deserializer.readStr("; ");

        deserializer.readPropStart("lastDiff");
        TopData.TopDataEx lastDif = TopData.TopDataEx.deserialize(deserializer);
        deserializer.readPropStart("diffAvgCntr");
        Utils.AverageCounter diffAvgCntr = Utils.AverageCounter.deserialize(deserializer);
        deserializer.readPropStart("stopRequested");
        String stopRequested = deserializer.readTill("; ");
        deserializer.readPropStart("isFinished");
        String isFinished = deserializer.readTill("]");

        PairExchangeData ret = new PairExchangeData(sh1, sh2, forks);
        ret.m_lastDiff = lastDif;
        ret.m_diffAverageCounter = diffAvgCntr;
        ret.m_totalIncome = Double.parseDouble(totalIncome);
        ret.m_runs = Integer.parseInt(runs);
        ret.m_stopRequested = Boolean.parseBoolean(stopRequested);
        ret.m_isFinished = Boolean.parseBoolean(isFinished);

        for (ForkData fork : forks) {
            fork.postDeserialize(ret);
        }
        return ret;
    }

    private static List<ForkData> readForks(Deserializer deserializer) throws IOException {
        // [[Fork]; [Fork]; [Fork]; ]
        deserializer.readObjectStart();
        List<ForkData> ret = new ArrayList<ForkData>();
        while(true) {
            if(deserializer.readIf("]")) {
                return ret;
            }
            ForkData fork = ForkData.deserialize(deserializer);
            ret.add(fork);
            deserializer.readStr("; ");
        }
    }

    public SharedExchangeData getSharedExch(Exchange exch) {
        if( m_sharedExch1.m_exchange == exch ) {
            return m_sharedExch1;
        } if( m_sharedExch2.m_exchange == exch ) {
            return m_sharedExch2;
        }
        throw new RuntimeException("unable to get get SharedExch for exch="+exch);
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
} // bthdg.PairExchangeData
