package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.AverageIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.tres.ind.VelocityIndicator;
import bthdg.tres.ind.VelocityRateIndicator;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;

public class CoppockPlusAlgo extends CoppockAlgo {
    public static double PEAK_TOLERANCE  = 0.000000005; // cov_vel:

    private final AverageIndicator m_averageCoppocIndicator;
    private final VelocityIndicator m_velocityIndicator;
    private final VelocityRateIndicator m_velRateIndicator;
    final AndIndicator m_andIndicator;
    private final AverageIndicator m_averageVelocityIndicator;
    private double m_lastDirectionAdjusted;

    public CoppockPlusAlgo(TresExchData tresExchData) {
        super("Cop+", tresExchData);

        long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;
        long velocitySize = barSizeMillis / 6;

        m_averageCoppocIndicator = new AverageIndicator("q", this, tresExchData.m_tres.m_phases/2) {
            @Override public Color getColor() { return Color.green; }

            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_velocityIndicator.addBar(lastPoint);
            }
        };
        m_indicators.add(m_averageCoppocIndicator);

        m_velocityIndicator = new VelocityIndicator(this, "vel", velocitySize, PEAK_TOLERANCE) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_averageVelocityIndicator.addBar(lastPoint);
            }
            @Override protected boolean countPeaks() { return false; }
            @Override public String toString() { return "CoppockPlusAlgo.VelocityIndicator"; }
        };
        m_indicators.add(m_velocityIndicator);

        m_averageVelocityIndicator = new AverageIndicator("A", this, tresExchData.m_tres.m_phases) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_velRateIndicator.addBar(lastPoint);
            }
            @Override protected boolean drawZeroLine() { return true; }
        };
        m_indicators.add(m_averageVelocityIndicator);

        m_velRateIndicator = new VelocityRateIndicator(this, m_coppockIndicator) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);

                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    double copValue = getDirectionAdjustedByPeakWatchers(m_coppockIndicator);
                    double copValue2 = copValue * m_velRateIndicator.m_velRateCalc.m_velocityRate;
                    long millis = lastPoint.m_millis;
                    m_andIndicator.addBar(new ChartPoint(millis, copValue2));
                }
            }
        };
        m_indicators.add(m_velRateIndicator);

        m_andIndicator = new AndIndicator(this);
        m_indicators.add(m_andIndicator);
    }

    @Override protected void onCoppockBar() {
        ChartPoint lastPoint = m_coppockIndicator.getLastPoint();
        m_averageCoppocIndicator.addBar(lastPoint);

        double directionAdjusted = getDirectionAdjusted();// [-1 ... 1]
        if (((m_lastDirectionAdjusted == 0) && (directionAdjusted != 0))
                || ((m_lastDirectionAdjusted > 0) && (directionAdjusted <= 0))
                || ((m_lastDirectionAdjusted < 0) && (directionAdjusted >= 0))) {
            notifyListener();
        }
        m_lastDirectionAdjusted = directionAdjusted;
    }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        ChartPoint lastPoint = m_andIndicator.getLastPoint();
        if (lastPoint != null) {
            return lastPoint.m_value;
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
        @Override protected boolean countPeaks() { return false; }
        @Override public Color getColor() { return Colors.LIGHT_MAGNETA; }
        @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
            double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
            minMaxCalculator.m_minValue = -max;
            minMaxCalculator.m_maxValue = max;
        }
    }
}
