package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CoppockIndicator;

public class CoppockAlgo extends TresAlgo {
    final CoppockIndicator m_coppockIndicator;

    public CoppockAlgo(TresExchData tresExchData) {
        super("COPPOCK", tresExchData);
        m_coppockIndicator = new CoppockIndicator(this) {
            @Override protected void onBar() {
                super.onBar();
                onCoppockBar();
            }
        };
        m_indicators.add(m_coppockIndicator);
    }

    protected void onCoppockBar() {}
    @Override public double lastTickPrice() { return m_coppockIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_coppockIndicator.lastTickTime(); }
    @Override public double getDirectionAdjusted() { return m_coppockIndicator.getDirectionAdjusted(); } // [-1 ... 1]
    @Override public Direction getDirection() { return m_coppockIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN

    @Override public String getRunAlgoParams() {
        return "COPPOCK.tolerance=" + m_coppockIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance;
    }
}
