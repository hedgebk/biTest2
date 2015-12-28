package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.*;
import bthdg.util.Utils;

import java.awt.*;

public class Cno3Algo extends TresAlgo {
    private static final Color COLOR = new Color(30, 100, 73);

    private final CciIndicator m_cciIndicator;
    private final CciIndicator.CciAdjustedIndicator m_cciAdjustedIndicator;
    private final OscIndicator m_oscIndicator;
    private final MidIndicator m_midIndicator;
    private final AndIndicator m_andIndicator;
    private final SmoochedIndicator m_smoochedIndicator;

    @Override public Color getColor() { return COLOR; }

    public Cno3Algo(TresExchData tresExchData) {
        super("C3O", tresExchData);
        m_cciIndicator = new CciIndicator(this) {
            @Override protected void onBar() {
                super.onBar();
                ChartPoint cci = getLastPoint();
                m_cciAdjustedIndicator.addBar(cci);
            }
        };
        m_indicators.add(m_cciIndicator);

        m_cciAdjustedIndicator = new CciIndicator.CciAdjustedIndicator(this) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint cci = getLastPoint();
                m_midIndicator.addBar1(cci);
            }
        };
        m_indicators.add(m_cciAdjustedIndicator);

        m_oscIndicator = new OscIndicator(this/*, OSC_TOLERANCE*/) { // use def OSC_TOLERANCE for now
            @Override protected void onBar() {
                super.onBar();
                ChartPoint osc = getLastPoint();
                m_midIndicator.addBar2(osc);
            }
        };
        m_indicators.add(m_oscIndicator);

        m_midIndicator = new MidIndicator(this) {
            @Override public void addBar(ChartPoint chartPoint) {
                if (chartPoint != null) {
                    super.addBar(new ChartPoint(chartPoint.m_millis, (chartPoint.m_value - 0.5) * 2));
                    ChartPoint osc = getLastPoint();
                    m_andIndicator.addBar(osc);
                }
            }
        };
        m_indicators.add(m_midIndicator);

        m_andIndicator = new AndIndicator(this) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint osc = getLastPoint();
                m_smoochedIndicator.addBar(osc);
            }
        };
        m_indicators.add(m_andIndicator);

        final long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;
        m_smoochedIndicator = new SmoochedIndicator(this, "sm", (long) (0.2 * barSizeMillis), 0) {
            @Override protected boolean countPeaks() { return false; }
        };
        m_indicators.add(m_smoochedIndicator);
    }

    @Override public double lastTickPrice() {
        return (m_oscIndicator.lastTickTime() > m_cciIndicator.lastTickTime())
                ? m_oscIndicator.lastTickPrice()
                : m_cciIndicator.lastTickPrice();
    }

    @Override public long lastTickTime() {
        return Math.max(m_oscIndicator.lastTickTime(), m_cciIndicator.lastTickTime());
    }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        ChartPoint lastPoint = m_smoochedIndicator.getLastPoint();
        return (lastPoint == null) ? 0 : lastPoint.m_value;
    }
    @Override public Direction getDirection() { return m_andIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN

    @Override public String getRunAlgoParams() {
        return "Cno3";
//                + " Mid.tlrnc=" + m_midIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance
//                + " osc.tlrnc=" + m_oscIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance
//                + " cci.tlrnc=" + m_cciIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance;
    }


    // ======================================================================================
    public static class AndIndicator extends TresIndicator {
        private double m_maxAbs;
        private double m_lastInValue = 0;

        public AndIndicator(TresAlgo algo) {
            super("+", 0.2, algo);
        }
        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.CYAN; }
        @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
            double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
            minMaxCalculator.m_minValue = -max;
            minMaxCalculator.m_maxValue = max;
        }

        @Override public void addBar(ChartPoint chartPoint) {
            if (chartPoint != null) {
                double inValue = chartPoint.m_value;
                double abs = Math.abs(inValue);
                double outValue;
                if (((m_lastInValue > 0) && (inValue > 0)) || ((m_lastInValue < 0) && (inValue < 0))) { // same sign
                    m_maxAbs = Math.max(m_maxAbs, abs);
                    outValue = inValue / m_maxAbs;
                } else { // different sign - zero cross
                    m_maxAbs = abs;
                    outValue = Math.signum(inValue); // 1 | -1
                }
                m_lastInValue = inValue;
                super.addBar(new ChartPoint(chartPoint.m_millis, outValue));
            }
        }
    }
}
