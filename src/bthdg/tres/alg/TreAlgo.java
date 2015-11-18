package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.Tres;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.*;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;

public class TreAlgo extends TresAlgo {
    public static double PEAK_TOLERANCE = 0.000000005; // cov_vel:

    final OscIndicator m_oscIndicator;
    final CciIndicator m_cciIndicator;
    final AndIndicator m_andIndicator;
    private double m_lastDirectionAdjusted;

    private TreAlgo(String name, TresExchData exchData) {
        super(name, exchData);

        m_oscIndicator = new OscIndicator(this) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                recalcAnd();
            }
        };
        m_indicators.add(m_oscIndicator);

        m_cciIndicator = new CciIndicator(this) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint cci = m_cciIndicator.getLastPoint();
                onCciBar(cci);
                recalcAnd();
            }
        };
        m_indicators.add(m_cciIndicator);

        m_andIndicator = new AndIndicator(this);
        m_indicators.add(m_andIndicator);
    }

    protected void onCciBar(ChartPoint chartPoint) { /*noop here*/ }

    private void recalcAnd() {
        ChartPoint osc = m_oscIndicator.getLastPoint();
        ChartPoint cci = m_cciIndicator.getLastPoint();
        if ((osc != null) && (cci != null)) {
            long oscMillis = osc.m_millis;
            long cciMillis = cci.m_millis;
            long millis = Math.max(oscMillis, cciMillis);

            double oscValue = getDirectionAdjustedByPeakWatchers(m_oscIndicator);
            double cciValue = getDirectionAdjustedByPeakWatchers(m_cciIndicator);

            double and = (oscValue + getCciValue(cciValue)) / 2;
            m_andIndicator.addBar(new ChartPoint(millis, and));

            double directionAdjusted = getDirectionAdjusted();// [-1 ... 1]
            if (((m_lastDirectionAdjusted == 0) && (directionAdjusted != 0))
                    || ((m_lastDirectionAdjusted > 0) && (directionAdjusted <= 0))
                    || ((m_lastDirectionAdjusted < 0) && (directionAdjusted >= 0))) {
                notifyListener();
            }
            m_lastDirectionAdjusted = directionAdjusted;
        }
    }

    protected double getCciValue(double cciValue) { return cciValue; }
    @Override public String getRunAlgoParams() { return "TreAlgo"; }

    @Override public double lastTickPrice() {
        return (m_oscIndicator.lastTickTime() > m_cciIndicator.lastTickTime())
                ? m_oscIndicator.lastTickPrice()
                : m_cciIndicator.lastTickPrice();
    }

    @Override public long lastTickTime() { return Math.max(m_oscIndicator.lastTickTime(), m_cciIndicator.lastTickTime()); }
    @Override public Color getColor() { return Colors.LIGHT_MAGNETA; }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        ChartPoint lastPoint = m_andIndicator.getLastPoint();
        if (lastPoint != null) {
            return (lastPoint.m_value > 0) ? 1 : -1;
        }
        return 0;
    }

    @Override public Direction getDirection() {
        return Direction.get(getDirectionAdjusted());
    } // UP/DOWN


    // ===========================================================================================
    public static class AndIndicator extends TresIndicator {
        public static double PEAK_TOLERANCE = 0.1;

        public AndIndicator(TresAlgo algo) {
            super("+", PEAK_TOLERANCE, algo);
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() {return Colors.LIGHT_MAGNETA; }

        @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
            double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
            minMaxCalculator.m_minValue = -max;
            minMaxCalculator.m_maxValue = max;
        }
    }


    // ===========================================================================================
    public static class TreAlgoBlended extends TreAlgo {
        private final AverageIndicator m_averageCciIndicator;
        private final VelocityIndicator m_velocityIndicator;
        private final VelocityRateIndicator m_velRateIndicator;

        public TreAlgoBlended(TresExchData tresExchData) {
            super("TreBlended", tresExchData);

            final Tres tres = tresExchData.m_tres;
            long barSizeMillis = tres.m_barSizeMillis;
            long velocitySize = barSizeMillis / 6;

            m_indicators.remove(m_andIndicator);

            m_averageCciIndicator = new AverageIndicator("ca", this, tres.m_phases / 2) {
                @Override public Color getColor() { return Color.green; }

                @Override public void addBar(ChartPoint chartPoint) {
                    super.addBar(chartPoint);
                    ChartPoint lastPoint = getLastPoint();
                    m_velocityIndicator.addBar(lastPoint);
                }
            };
            m_indicators.add(m_averageCciIndicator);

            m_velocityIndicator = new VelocityIndicator(this, "cv", velocitySize, PEAK_TOLERANCE) {
                @Override public void addBar(ChartPoint chartPoint) {
                    super.addBar(chartPoint);
                    ChartPoint lastPoint = getLastPoint();
                    m_velRateIndicator.addBar(lastPoint);
                }

                @Override protected boolean countPeaks() { return false; }
                @Override public String toString() { return "TreAlgo.VelocityIndicator"; }
            };
            m_indicators.add(m_velocityIndicator);

            m_velRateIndicator = new VelocityRateIndicator(this, m_cciIndicator) {
                @Override public void addBar(ChartPoint chartPoint) {
                    super.addBar(chartPoint);
                }
            };
            m_indicators.add(m_velRateIndicator);

            m_indicators.add(m_andIndicator);
        }

        @Override protected void onCciBar(ChartPoint chartPoint) {
            m_averageCciIndicator.addBar(chartPoint);
        }

        @Override protected double getCciValue(double cciValue) {
            return cciValue * m_velRateIndicator.m_velRateCalc.m_velocityRate;
        }

        @Override public double getDirectionAdjusted() { // [-1 ... 1]
            ChartPoint lastPoint = m_andIndicator.getLastPoint();
            if (lastPoint != null) {
                return lastPoint.m_value;
            }
            return 0;
        }
    }


    // ===========================================================================================
    public static class TreAlgoSharp extends TreAlgo {
        public TreAlgoSharp(TresExchData tresExchData) {
            super("TreSharp", tresExchData);
        }
    }
}