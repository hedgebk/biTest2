package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CciIndicator;

public class CciAlgo extends TresAlgo {
    final CciIndicator m_cciIndicator;

    public CciAlgo(TresExchData tresExchData) {
        super("CCI", tresExchData);
        m_cciIndicator = new CciIndicator(this);
        m_indicators.add(m_cciIndicator);
    }

    @Override public double lastTickPrice() { return m_cciIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_cciIndicator.lastTickTime(); }
    @Override public double getDirectionAdjusted() { return m_cciIndicator.getDirectionAdjusted(); } // [-1 ... 1]
    @Override public Direction getDirection() { return m_cciIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN

    @Override public String getRunAlgoParams() {
        return "CCI.tolerance=" + m_cciIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance;
    }
}
