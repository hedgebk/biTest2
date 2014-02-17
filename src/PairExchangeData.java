import java.util.ArrayList;
import java.util.List;

public class PairExchangeData {
    final SharedExchangeData m_sharedExch1;
    final SharedExchangeData m_sharedExch2;
    public final List<ForkData> m_forks = new ArrayList<ForkData>();
    public TopData.TopDataEx m_lastDiff;
    public final Utils.AverageCounter m_diffAverageCounter = new Utils.AverageCounter(Fetcher.MOVING_AVERAGE);
    private double m_totalIncome;

    public String exchNames() { return m_sharedExch1.m_exchange.m_name + "-" + m_sharedExch2.m_exchange.m_name; }

    public PairExchangeData(Exchange exch1, Exchange exch2) {
        m_sharedExch1 = new SharedExchangeData(exch1);
        m_sharedExch2 = new SharedExchangeData(exch2);
        ForkData firstForkData = new ForkData(this);
        m_forks.add(firstForkData);
    }

    public boolean checkState(IterationContext iContext) throws Exception {
        System.out.println("PairExchangeData.checkState() we have " + m_forks.size() + " fork(s)");

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
                    System.out.println("finish fork was in state END - adding brand new Fork");
                    m_forks.add(new ForkData(this));
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

        return m_forks.isEmpty(); // no more tasks to execute
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
        System.out.println(" earnIncome=" + Fetcher.format(m_totalIncome));
    }
} // PairExchangeData
