package bthdg.tres;

import bthdg.calc.CoppockCalculator;
import bthdg.osc.TrendWatcher;

import java.util.LinkedList;

public class TresCoppockCalculator extends CoppockCalculator {
    private static final double PEAK_TOLERANCE = 0.01;

    private final TresExchData m_exchData;
    private final int m_phaseIndex;
    LinkedList<CoppockTick> m_coppockPoints = new LinkedList<CoppockTick>();
    LinkedList<CoppockTick> m_coppockPeaks = new LinkedList<CoppockTick>();
    TrendWatcher<CoppockTick> m_peakCalculator = new TrendWatcher<CoppockTick>(PEAK_TOLERANCE) {
        @Override protected double toDouble(CoppockTick tick) { return tick.m_value; }
        @Override protected void onNewPeak(CoppockTick peak) {
            synchronized (m_coppockPeaks) {
                m_coppockPeaks.add(peak);
            }
        }
    };
    protected CoppockTick m_lastTick;

    public TresCoppockCalculator(TresExchData exchData, int phaseIndex) {
        super(10, 14, 11, exchData.m_tres.m_barSizeMillis, exchData.m_tres.getBarOffset(phaseIndex));
        m_exchData = exchData;
        m_phaseIndex = phaseIndex;
    }

    @Override protected void bar(long barStart, double value) {
        CoppockTick tick = new CoppockTick(barStart, value);
System.out.println("CoppockTick " + value);
        m_coppockPoints.add(tick); // add to the end
        m_peakCalculator.update(tick);
        m_lastTick = tick;
    }

    public static class CoppockTick {
        public final long m_barStart;
        public final double m_value;

        public CoppockTick(long barStart, double value) {
            m_barStart = barStart;
            m_value = value;
        }
    }
}
