package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CciIndicator;
import bthdg.tres.ind.CoppockIndicator;
import bthdg.tres.ind.TresIndicator;

import java.awt.*;

public class CncAlgo extends TresAlgo {
    public static double CCI_CORRECTION_RATIO = 7408;

    final CoppockIndicator m_coppockIndicator;
    final CciIndicator m_cciIndicator;
    final AndIndicator m_andIndicator;

    public CncAlgo(TresExchData tresExchData) {
        super("C+C", tresExchData);
        m_coppockIndicator = new CoppockIndicator(this) {
            @Override protected void onBar() {
                super.onBar();
                recalcAnd();
            }
        };
        m_indicators.add(m_coppockIndicator);
        m_cciIndicator = new CciIndicator(this) {
            @Override protected void onBar() {
                super.onBar();
                recalcAnd();
            }
        };
        m_indicators.add(m_cciIndicator);
        m_andIndicator = new AndIndicator(this);
        m_indicators.add(m_andIndicator);
    }

    private void recalcAnd() {
        ChartPoint coppock = m_coppockIndicator.getLastPoint();
        ChartPoint cci = m_cciIndicator.getLastPoint();
        if ((coppock != null) && (cci != null)) {
            double coppockValue = coppock.m_value;
            double cciValue = cci.m_value;
            double and = coppockValue + cciValue / CCI_CORRECTION_RATIO;
            long coppockMillis = coppock.m_millis;
            long cciMillis = cci.m_millis;
            long millis = Math.max(coppockMillis, cciMillis);
            ChartPoint chartPoint = new ChartPoint(millis, and);
            m_andIndicator.addBar(chartPoint);
        }
    }

    @Override public double lastTickPrice() {
        return (m_coppockIndicator.lastTickTime() > m_cciIndicator.lastTickTime())
                ? m_coppockIndicator.lastTickPrice()
                : m_cciIndicator.lastTickPrice();
    }

    @Override public long lastTickTime() {
        return Math.max(m_coppockIndicator.lastTickTime(), m_cciIndicator.lastTickTime());
    }

    @Override public Color getColor() { return Color.red; }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        return getDirectionAdjustedByPeakWatchers(m_andIndicator);
    }
    @Override public Direction getDirection() { return m_andIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN

    @Override public String getRunAlgoParams() {
        return "Cnc.And.tolerance=" + m_andIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance;
    }

    public static class AndIndicator extends TresIndicator {
        public static double PEAK_TOLERANCE = 0.06470;

        public AndIndicator(TresAlgo algo) {
            super("+", PEAK_TOLERANCE, algo);
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.red; }
        @Override public Color getPeakColor() { return Color.red; }
        @Override protected boolean centerYZeroLine() { return true; }
    }
}
