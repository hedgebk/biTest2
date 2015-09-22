package bthdg.tres;

import bthdg.calc.CoppockCalculator;
import bthdg.osc.TrendWatcher;

import java.util.LinkedList;

public class TresCoppockCalculator extends CoppockCalculator {
    private static final double PEAK_TOLERANCE = 0.0075;

    private final TresExchData m_exchData;
    private final int m_phaseIndex;
    LinkedList<ChartPoint> m_coppockPoints = new LinkedList<ChartPoint>();
    final LinkedList<ChartPoint> m_coppockPeaks = new LinkedList<ChartPoint>();
    TrendWatcher<ChartPoint> m_peakCalculator = new TrendWatcher<ChartPoint>(PEAK_TOLERANCE) {
        @Override protected double toDouble(ChartPoint tick) { return tick.m_value; }
        @Override protected void onNewPeak(ChartPoint peak, ChartPoint last) {
            synchronized (m_coppockPeaks) {
                m_coppockPeaks.add(peak);
            }
        }
    };
    protected ChartPoint m_lastTick;

    public TresCoppockCalculator(TresExchData exchData, int phaseIndex) {
        super(10, 14, 11, exchData.m_tres.m_barSizeMillis, exchData.m_tres.getBarOffset(phaseIndex));
        m_exchData = exchData;
        m_phaseIndex = phaseIndex;
    }

    @Override protected void bar(long barEnd, double value) {
        ChartPoint tick = new ChartPoint(barEnd, value);
        m_coppockPoints.add(tick); // add to the end
        m_peakCalculator.update(tick);
        m_lastTick = tick;
    }
}
