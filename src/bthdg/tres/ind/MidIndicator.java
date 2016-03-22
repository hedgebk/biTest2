package bthdg.tres.ind;

import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Utils;

import java.awt.*;

public class MidIndicator extends TresIndicator {
    public static double MID_PEAK_TOLERANCE = 0.1;
    private ChartPoint m_chartPoint1;
    private ChartPoint m_chartPoint2;

    public MidIndicator(TresAlgo algo) {
        super("~", MID_PEAK_TOLERANCE, algo);
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
    @Override public Color getColor() { return Color.red; }
    @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
        double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
        minMaxCalculator.m_minValue = -max;
        minMaxCalculator.m_maxValue = max;
    }
    @Override protected boolean drawZeroLine() { return true; }

    public void addBar1(ChartPoint chartPoint1) {
        if (chartPoint1 != null) {
            if ((m_chartPoint1 == null) || (m_chartPoint1.m_millis != chartPoint1.m_millis) || (m_chartPoint1.m_value != chartPoint1.m_value)) {
                m_chartPoint1 = chartPoint1;
                ChartPoint midPoint = recalcMid();
                addBar(midPoint);
            }
        }
    }

    public void addBar2(ChartPoint chartPoint2) {
        if (chartPoint2 != null) {
            if ((m_chartPoint2 == null) || (m_chartPoint2.m_millis != chartPoint2.m_millis) || (m_chartPoint2.m_value != chartPoint2.m_value)) {
                m_chartPoint2 = chartPoint2;
                ChartPoint midPoint = recalcMid();
                addBar(midPoint);
            }
        }
    }

    private ChartPoint recalcMid() {
        if ((m_chartPoint1 != null) && (m_chartPoint2 != null)) {
            long millis = Math.max(m_chartPoint1.m_millis, m_chartPoint2.m_millis);
            double val1 = m_chartPoint1.m_value;
            double val2 = m_chartPoint2.m_value;
            double mid = (val1 + val2) / 2;
            ChartPoint chartPoint = new ChartPoint(millis, mid);
            return chartPoint;
        }
        return null;
    }
}