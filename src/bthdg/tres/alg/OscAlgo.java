package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.OscIndicator;

public class OscAlgo extends TresAlgo {
    final OscIndicator m_oscIndicator;

    public OscAlgo(TresExchData exchData) {
        super("OSC", exchData);
//        exchData.m_oscAlgo = this;

        m_oscIndicator = new OscIndicator(this);
        m_indicators.add(m_oscIndicator);
    }

    @Override public double lastTickPrice() { return m_oscIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_oscIndicator.lastTickTime(); }

    @Override public double getDirectionAdjusted() { return m_oscIndicator.getDirectionAdjusted(); } // [-1 ... 1]
    @Override public Direction getDirection() { return m_oscIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN

    public static class OscChartPoint extends ChartPoint {
        private final double m_stoch2;

        public OscChartPoint(long millis, double stoch1, double stoch2) {
            super(millis, stoch1);
            m_stoch2 = stoch2;
        }
    }

}
