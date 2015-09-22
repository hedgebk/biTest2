package bthdg.tres;


import bthdg.calc.CciCalculator;
import bthdg.osc.TrendWatcher;

import java.util.LinkedList;

public class TresCciCalculator extends CciCalculator {
    private static final double PEAK_TOLERANCE = 0.01;
    public static final int DEF_SMA_LENGTH = 20;

    private final TresExchData m_exchData;
    LinkedList<ChartPoint> m_cciPoints = new LinkedList<ChartPoint>();
    final LinkedList<ChartPoint> m_cciPeaks = new LinkedList<ChartPoint>();
    TrendWatcher<ChartPoint> m_peakCalculator = new TrendWatcher<ChartPoint>(PEAK_TOLERANCE) {
        @Override protected double toDouble(ChartPoint tick) { return tick.m_value; }
        @Override protected void onNewPeak(ChartPoint peak, ChartPoint last) {
            synchronized (m_cciPeaks) {
                m_cciPeaks.add(peak);
            }
        }
    };
    protected ChartPoint m_lastTick;

    public TresCciCalculator(TresExchData exchData, int phaseIndex) {
        super(DEF_SMA_LENGTH, exchData.m_tres.m_barSizeMillis, exchData.m_tres.getBarOffset(phaseIndex));
        m_exchData = exchData;
    }

    @Override protected void bar(long barEnd, double value) {
        ChartPoint tick = new ChartPoint(barEnd, value);
        m_cciPoints.add(tick); // add to the end
        m_peakCalculator.update(tick);
        m_lastTick = tick;
    }
}
