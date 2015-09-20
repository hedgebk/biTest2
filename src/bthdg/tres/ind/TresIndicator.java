package bthdg.tres.ind;

import bthdg.osc.TrendWatcher;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;

import java.util.LinkedList;

public abstract class TresIndicator {

    public static TresIndicator get(String indicatorName) {
        if (indicatorName.equals("coppock")) {
            return new CoppockIndicator();
        }
        throw new RuntimeException("unsupported indicator '" + indicatorName + "'");
    }

    public abstract TresPhasedIndicator createPhased(TresExchData exchData, int phaseIndex);

    public static abstract class TresPhasedIndicator {
        private final TresIndicator m_indicatorAvg;
        private final TresExchData m_exchData;
        private final int m_phaseIndex;
        final TrendWatcher<ChartPoint> m_peakCalculator;
        final LinkedList<ChartPoint> m_points = new LinkedList<ChartPoint>();
        final LinkedList<ChartPoint> m_peaks = new LinkedList<ChartPoint>();

        public TresPhasedIndicator(TresIndicator tresIndicatorAvg, TresExchData exchData, int phaseIndex, double peakTolerance) {
            m_indicatorAvg = tresIndicatorAvg;
            m_exchData = exchData;
            m_phaseIndex = phaseIndex;
            m_peakCalculator = new TrendWatcher<ChartPoint>(peakTolerance) {
                @Override protected double toDouble(ChartPoint tick) { return tick.m_value; }
                @Override protected void onNewPeak(ChartPoint peak, ChartPoint last) {
                    synchronized (m_peaks) {
                        m_peaks.add(peak);
                    }
                }
            };
        }

        public abstract boolean update(long timestamp, double price);
    }
}
