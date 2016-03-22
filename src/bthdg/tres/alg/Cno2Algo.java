package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.*;
import bthdg.util.Utils;

import java.awt.*;

public class Cno2Algo extends TresAlgo {
    public static double FRAME_RATIO = 21;
    public static double MID_PEAK_TOLERANCE = 0.1;
    private static final double OSC_TOLERANCE = 0.35;

    final OscIndicator m_oscIndicator;
    final CciIndicator m_cciIndicator;
    private final Utils.SlidingValuesFrame m_cciFrameCounter;
    final MidIndicator m_midIndicator;
    private final VelocityIndicator m_velocityIndicator;
    private final VelocityRateIndicator m_velRateIndicator;
    private final AndIndicator m_andIndicator;
    private long m_lastCciMillis;
    private double m_lastCciValue;
    private double m_lastCciAdjusted;
    private double m_lastMinValue;
    private double m_lastMaxValue;

    public Cno2Algo(TresExchData tresExchData) {
        super("C2O", tresExchData);
        m_cciIndicator = new CciIndicator(this) {
            @Override protected void onBar() {
                super.onBar();
                recalcCciAdjusted();
                recalcMid();
            }
        };
        m_indicators.add(m_cciIndicator);

        m_oscIndicator = new OscIndicator(this/*, OSC_TOLERANCE*/) { // use def OSC_TOLERANCE for now
            @Override protected void onBar() {
                super.onBar();
                recalcMid();
            }
        };
        m_indicators.add(m_oscIndicator);

        m_midIndicator = new MidIndicator(this) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    m_velocityIndicator.addBar(lastPoint);
                }
            }
        };
        m_indicators.add(m_midIndicator);

        long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;
        long velocitySize = barSizeMillis/2;
        m_velocityIndicator = new VelocityIndicator(this, "vel", velocitySize, 0.1) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_velRateIndicator.addBar(lastPoint);
            }
            @Override public String toString() { return "Cno2.VelocityIndicator"; }
        };
        m_indicators.add(m_velocityIndicator);

        m_velRateIndicator = new VelocityRateIndicator(this, m_midIndicator) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);

                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    double copValue = getDirectionAdjustedByPeakWatchers(m_midIndicator);
                    double copValue2 = copValue * m_velRateIndicator.m_velRateCalc.m_velocityRate;
                    long millis = lastPoint.m_millis;
                    m_andIndicator.addBar(new ChartPoint(millis, copValue2));
                }
            }
        };
        m_indicators.add(m_velRateIndicator);

        m_andIndicator = new AndIndicator(this);
        m_indicators.add(m_andIndicator);

        long millis = (long) (tresExchData.m_tres.m_barSizeMillis * FRAME_RATIO);
        m_cciFrameCounter = new Utils.SlidingValuesFrame(millis);
    }

    private void recalcCciAdjusted() {
        ChartPoint cci = m_cciIndicator.getLastPoint();
        if (cci != null) {
            double cciValue = cci.m_value;
            long cciMillis = cci.m_millis;
            boolean timeChanged = m_lastCciMillis != cciMillis;
            if (timeChanged || (m_lastCciValue != cciValue)) {
                m_cciFrameCounter.justAdd(cciMillis, cciValue);
                if (m_cciFrameCounter.m_full) {
                    // recalc if timeframe changed or new value is out of known bounds
                    if (timeChanged || (cciValue > m_lastMaxValue) || (cciValue < m_lastMinValue)) {
                        Utils.DoubleDoubleMinMaxCalculator minMaxCalc = new Utils.DoubleDoubleMinMaxCalculator();
                        for (Double value : m_cciFrameCounter.m_map.values()) {
                            minMaxCalc.calculate(value);
                        }
                        double minValue = minMaxCalc.m_minValue;
                        m_lastMinValue = minValue;
                        double maxValue = minMaxCalc.m_maxValue;
                        m_lastMaxValue = maxValue;
                    }

                    double minMaxDiff = m_lastMaxValue - m_lastMinValue;
                    if (minMaxDiff != 0) {
                        double cciAdjusted = (cciValue - m_lastMinValue) / minMaxDiff;
                        m_lastCciAdjusted = cciAdjusted;
                    } else {
                        m_lastCciAdjusted = 0;
                    }
                }
                m_lastCciMillis = cciMillis;
                m_lastCciValue = cciValue;
            }
        }
    }

    private void recalcMid() {
        ChartPoint osc = m_oscIndicator.getLastPoint();
        ChartPoint cci = m_cciIndicator.getLastPoint();
        if ((osc != null) && (cci != null) && m_cciFrameCounter.m_full) {
            double directionAdjusted = calcCno2();
            long millis = Math.max(cci.m_millis, cci.m_millis);
            ChartPoint chartPoint = new ChartPoint(millis, directionAdjusted);
            m_midIndicator.addBar(chartPoint);
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
                + " Mid.tlrnc=" + m_midIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance
                + " osc.tlrnc=" + m_oscIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance
                + " cci.tlrnc=" + m_cciIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance;
    }


    // ======================================================================================
    public static class MidIndicator extends TresIndicator {
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
    }

    // ======================================================================================
    public static class AndIndicator extends TresIndicator {
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
    }
}
