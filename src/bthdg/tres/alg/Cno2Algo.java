package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CciIndicator;
import bthdg.tres.ind.OscIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.util.Utils;

import java.awt.*;

public class Cno2Algo extends TresAlgo {
    public static int FRAME_RATIO = 30;
    public static double AND_PEAK_TOLERANCE = 0.1;
    private static final double OSC_TOLERANCE = 0.35;

    final OscIndicator m_oscIndicator;
    final CciIndicator m_cciIndicator;
    private final Utils.SlidingValuesFrame m_cciFrameCounter;
    final AndIndicator m_andIndicator;
    private long m_lastCciMillis;
    private double m_lastCciValue;
    private double m_lastCciAdjusted;

    public Cno2Algo(TresExchData tresExchData) {
        super("C2O", tresExchData);
        m_cciIndicator = new CciIndicator(this) {
            @Override protected void onBar() {
                super.onBar();
                recalcCciAdjusted();
                recalcAnd();
            }
        };
        m_indicators.add(m_cciIndicator);

        m_oscIndicator = new OscIndicator(this/*, OSC_TOLERANCE*/) { // use def OSC_TOLERANCE for now
            @Override protected void onBar() {
                super.onBar();
                recalcAnd();
            }
        };
        m_indicators.add(m_oscIndicator);

        m_andIndicator = new AndIndicator(this);
        m_indicators.add(m_andIndicator);

        long millis = tresExchData.m_tres.m_barSizeMillis * FRAME_RATIO;
        m_cciFrameCounter = new Utils.SlidingValuesFrame(millis);
    }

    private void recalcCciAdjusted() {
        ChartPoint cci = m_cciIndicator.getLastPoint();
        if (cci != null) {
            double cciValue = cci.m_value;
            long cciMillis = cci.m_millis;
            if ((m_lastCciMillis != cciMillis) || (m_lastCciValue != cciValue)) {
                m_cciFrameCounter.justAdd(cciMillis, cciValue);
                if (m_cciFrameCounter.m_full) {
                    Utils.DoubleDoubleMinMaxCalculator minMaxCalc = new Utils.DoubleDoubleMinMaxCalculator();
                    for (Double value : m_cciFrameCounter.m_map.values()) {
                        minMaxCalc.calculate(value);
                    }
                    Double minValue = minMaxCalc.m_minValue;
                    Double maxValue = minMaxCalc.m_maxValue;
                    double cciAdjusted = (cciValue - minValue) / (maxValue - minValue);
                    m_lastCciAdjusted = cciAdjusted;

                }
                m_lastCciMillis = cciMillis;
                m_lastCciValue = cciValue;
            }
        }
    }

    private void recalcAnd() {
        ChartPoint osc = m_oscIndicator.getLastPoint();
        ChartPoint cci = m_cciIndicator.getLastPoint();
        if ((osc != null) && (cci != null) && m_cciFrameCounter.m_full) {
            double directionAdjusted = calcCno2();
            long millis = Math.max(cci.m_millis, cci.m_millis);
            ChartPoint chartPoint = new ChartPoint(millis, directionAdjusted);
            m_andIndicator.addBar(chartPoint);
        }
    }

    @Override public double lastTickPrice() {
        return (m_oscIndicator.lastTickTime() > m_cciIndicator.lastTickTime())
                ? m_oscIndicator.lastTickPrice()
                : m_cciIndicator.lastTickPrice();
    }

    @Override public long lastTickTime() {
        return Math.max(m_oscIndicator.lastTickTime(), m_cciIndicator.lastTickTime());
    }


    private double calcCno2() { // [-1 ... 1]
        ChartPoint osc = m_oscIndicator.getLastPoint();
        ChartPoint cci = m_cciIndicator.getLastPoint();
        if ((osc != null) && (cci != null)) {
            double oscValue = osc.m_value;          // [0 ... 1]
            double cciAdjusted = m_lastCciAdjusted; // [0 ... 1]
            return oscValue + cciAdjusted - 1;      // [-1 ... 1]
        }
        return 0;
    }

    @Override public Color getColor() { return Color.red; }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        return getDirectionAdjustedByPeakWatchers(m_andIndicator);
    }
    @Override public Direction getDirection() { return m_andIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN

    @Override public String getRunAlgoParams() {
        return "Cno2 "
                + "And.tlrnc=" + m_andIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance
                + "osc.tlrnc=" + m_oscIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance
                + "cci.tlrnc=" + m_cciIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance;
    }


    // ======================================================================================
    public static class AndIndicator extends TresIndicator {
        public AndIndicator(TresAlgo algo) {
            super("+", AND_PEAK_TOLERANCE, algo);
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.red; }
        @Override public Color getPeakColor() { return Color.red; }
        @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
            double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
            minMaxCalculator.m_minValue = -max;
            minMaxCalculator.m_maxValue = max;
        }
    }
}
