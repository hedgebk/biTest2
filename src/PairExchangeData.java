import java.util.ArrayList;
import java.util.List;

public class PairExchangeData {
    final SharedExchangeData m_sharedExch1;
    final SharedExchangeData m_sharedExch2;
    public final List<ForkData> m_forks = new ArrayList<ForkData>();
    public TopData.TopDataEx m_lastDiff;
    public final Utils.AverageCounter m_diffAverageCounter = new Utils.AverageCounter(Fetcher.MOVING_AVERAGE);

    public PairExchangeData(Exchange exch1, Exchange exch2) {
        m_sharedExch1 = new SharedExchangeData(exch1);
        m_sharedExch2 = new SharedExchangeData(exch2);
        ForkData firstForkData = new ForkData(this);
        m_forks.add(firstForkData);
    }

    public boolean checkState(IterationContext iContext) throws Exception {
        System.out.println("PairExchangeData.checkState() we have " + m_forks.size() + " fork(s)");

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

        return false;
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
} // PairExchangeData
