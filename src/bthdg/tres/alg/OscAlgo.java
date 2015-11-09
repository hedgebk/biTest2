package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.TresExchData;
import bthdg.tres.TresOscCalculator;
import bthdg.tres.ind.OscIndicator;

public class OscAlgo extends TresAlgo {
    final OscIndicator m_oscIndicator;

    public OscAlgo(TresExchData exchData) {
        super("OSC", exchData);
        m_oscIndicator = new OscIndicator(this);
        m_indicators.add(m_oscIndicator);
    }

    @Override public String getRunAlgoParams() {
        return "COPPOCK.tolerance=" + m_oscIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance
                + " oskLock=" + TresOscCalculator.LOCK_OSC_LEVEL;
    }

    @Override public double lastTickPrice() { return m_oscIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_oscIndicator.lastTickTime(); }

    @Override public double getDirectionAdjusted() { return m_oscIndicator.getDirectionAdjusted(); } // [-1 ... 1]
    @Override public Direction getDirection() { return m_oscIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN
}
