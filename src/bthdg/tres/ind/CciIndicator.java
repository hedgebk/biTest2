package bthdg.tres.ind;

import bthdg.calc.CciCalculator;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;

// http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:commodity_channel_index_cci
public class CciIndicator extends TresIndicator {
    public static double PEAK_TOLERANCE = 20;
    public static final Color CCI_COLOR = Colors.setAlpha(Colors.LIGHT_ORANGE, 25);
    public static final Color CCI_AVG_COLOR = new Color(230, 100, 43);

    @Override public Color getColor() { return CCI_AVG_COLOR; }
    @Override public Color getPeakColor() { return CCI_AVG_COLOR; }

    public CciIndicator(TresAlgo algo) {
        this("Cci", algo);
    }

    protected CciIndicator(String name, TresAlgo algo) {
        super( name, PEAK_TOLERANCE, algo);
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedCciIndicator(this, exchData, phaseIndex);
    }

    // ======================================================================================
    public static class PhasedCciIndicator extends TresPhasedIndicator {
        public static int SMA_LENGTH = 20;

        private final CciCalculator m_calculator;

        @Override public Color getColor() { return CCI_COLOR; }
        @Override public double lastTickPrice() { return m_calculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_calculator.m_lastTickTime; }

        public PhasedCciIndicator(CciIndicator indicator, TresExchData exchData, int phaseIndex) {
            super(indicator, exchData, phaseIndex, PEAK_TOLERANCE);
            m_calculator = new CciCalculator(SMA_LENGTH, exchData.m_tres.m_barSizeMillis, exchData.m_tres.getBarOffset(phaseIndex)) {
                @Override protected void bar(long barEnd, double value) {
                    ChartPoint tick = new ChartPoint(barEnd, value);
                    if (m_exchData.m_tres.m_collectPoints) {
                        m_points.add(tick); // add to the end
                    }
                    m_peakCalculator.update(tick);
                    onBar(tick);
                }
            };
        }

        @Override public boolean update(long timestamp, double price) {
            return m_calculator.update(timestamp, price);
        }
    }


    // ======================================================================================
    public static class CciAdjustedIndicator extends TresIndicator {
        public static double FRAME_RATIO = 21;
        private static final Color COLOR = new Color(130, 200, 43);

        private final Utils.SlidingValuesFrame m_cciFrameCounter;
        private long m_lastCciMillis;
        private double m_lastCciValue;
        private double m_lastCciAdjusted;
        private double m_lastMinValue;
        private double m_lastMaxValue;

        public CciAdjustedIndicator(TresAlgo algo) {
            super("Cci'", 0, algo);
            TresExchData tresExchData = m_algo.m_tresExchData;
            long millis = (long) (tresExchData.m_tres.m_barSizeMillis * FRAME_RATIO);
            m_cciFrameCounter = new Utils.SlidingValuesFrame(millis);
        }

        @Override public Color getColor() { return COLOR; }
        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }

        @Override public void addBar(ChartPoint chartPoint) {
            ChartPoint adjustedPoint = recalcCciAdjusted(chartPoint);
            super.addBar(adjustedPoint);
        }

        private ChartPoint recalcCciAdjusted(ChartPoint cci) {
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
                        m_lastCciMillis = cciMillis;
                        m_lastCciValue = cciValue;
                        return new ChartPoint(m_lastCciMillis, m_lastCciAdjusted);
                    }
                }
            }
            return null;
        }
    }
}
