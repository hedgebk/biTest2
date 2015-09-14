package bthdg.tres;


import bthdg.calc.CciCalculator;
import bthdg.osc.TrendWatcher;

import java.util.LinkedList;

public class TresCciCalculator extends CciCalculator {
    private static final double PEAK_TOLERANCE = 0.01;
    public static final int DEF_SMA_LENGTH = 20;

    private final TresExchData m_exchData;
    LinkedList<CciTick> m_cciPoints = new LinkedList<CciTick>();
    LinkedList<CciTick> m_cciPeaks = new LinkedList<CciTick>();
    TrendWatcher<CciTick> m_peakCalculator = new TrendWatcher<CciTick>(PEAK_TOLERANCE) {
        @Override protected double toDouble(CciTick tick) { return tick.m_value; }
        @Override protected void onNewPeak(CciTick peak) {
            synchronized (m_cciPeaks) {
                m_cciPeaks.add(peak);
            }
        }
    };
    protected CciTick m_lastTick;

    public TresCciCalculator(TresExchData exchData, int phaseIndex) {
        super(DEF_SMA_LENGTH, exchData.m_tres.m_barSizeMillis, exchData.m_tres.getBarOffset(phaseIndex));
        m_exchData = exchData;
    }

    @Override protected void bar(long barEnd, double value) {
        CciTick tick = new CciTick(barEnd, value);
        m_cciPoints.add(tick); // add to the end
        m_peakCalculator.update(tick);
        m_lastTick = tick;
    }


    public static class CciTick {
        public final long m_barEnd;
        public final double m_value;

        public CciTick(long barEnd, double value) {
            m_barEnd = barEnd;
            m_value = value;
        }
    }
}
