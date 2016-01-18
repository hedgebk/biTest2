package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.*;
import bthdg.util.Utils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class AroonAlgo extends TresAlgo {
    public static double PEAK_TOLERANCE2 = 0.48;
    public static double PEAK_TOLERANCE3 = 0.049200;
    public static double PEAK_TOLERANCE4 = 0.96;

    public static double BAR_RATIOS_STEP = 1.065;
    public static int BAR_RATIOS_STEP_NUM = 6;

    private static final double SMOOTH_RATE = Math.pow(BAR_RATIOS_STEP, BAR_RATIOS_STEP_NUM - 1) * 2;

    private final List<AroonIndicator> m_aroonIndicators = new ArrayList<AroonIndicator>();
    private final DirectionIndicator m_directionIndicator;
    private final SmoochedIndicator m_smoochedIndicator;
    private final VelocityIndicator m_velocityIndicator;
    private final SmoochedIndicator m_smoochedVelocityIndicator;
    private final VelocityRateIndicator m_velRateIndicator;
    protected final AndIndicator m_andIndicator;

    @Override public double lastTickPrice() { return m_aroonIndicators.get(0).lastTickPrice(); }
    @Override public long lastTickTime() { return m_aroonIndicators.get(0).lastTickTime(); }
    @Override public Color getColor() { return AroonIndicator.COLOR; }

    public AroonAlgo(TresExchData exchData) {
        this("ARO", exchData);
    }

    public AroonAlgo(String name, TresExchData exchData) {
        super(name, exchData);

        double barRatio = 1;
        for (int i = 0; i < BAR_RATIOS_STEP_NUM; i++) {
            AroonIndicatorInt aroonIndicator = new AroonIndicatorInt("ar" + i, barRatio);
            m_indicators.add(aroonIndicator);
            m_aroonIndicators.add(aroonIndicator);
            barRatio *= BAR_RATIOS_STEP;
        }

        m_directionIndicator = new DirectionIndicator(this) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_smoochedIndicator.addBar(lastPoint);
            }
        };
        m_indicators.add(m_directionIndicator);

        final long barSizeMillis = exchData.m_tres.m_barSizeMillis;
        m_smoochedIndicator = new SmoochedIndicator(this, "sm", (long) (SMOOTH_RATE * barSizeMillis), PEAK_TOLERANCE3) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_velocityIndicator.addBar(lastPoint);
            }
        };
        m_indicators.add(m_smoochedIndicator);

        long velocitySize = barSizeMillis/2;
        m_velocityIndicator = new VelocityIndicator(this, "vel", velocitySize, 0.1) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_smoochedVelocityIndicator.addBar(lastPoint);
            }
            @Override public String toString() { return "Aro.VelIndicator"; }
        };
        m_indicators.add(m_velocityIndicator);

        m_smoochedVelocityIndicator = new SmoochedIndicator(this, "sm", (long) (1.3 * barSizeMillis), 0) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                m_velRateIndicator.addBar(lastPoint);
            }
            @Override protected boolean countPeaks() { return false; }
        };
        m_indicators.add(m_smoochedVelocityIndicator);

        m_velRateIndicator = new VelocityRateIndicator(this, m_smoochedIndicator) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);

                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    double copValue = getDirectionAdjustedByPeakWatchers(m_smoochedIndicator);
                    double copValue2 = copValue * m_velRateIndicator.m_velRateCalc.m_velocityRate;

                    copValue2 = Math.signum(copValue2) * Math.pow(Math.abs(copValue2), 0.25);

                    long millis = lastPoint.m_millis;
                    m_andIndicator.addBar(new ChartPoint(millis, copValue2));
                }
            }
        };
        m_indicators.add(m_velRateIndicator);

        m_andIndicator = new AndIndicator(this);
        m_indicators.add(m_andIndicator);
    }

    @Override public String getRunAlgoParams() { return "Aroon"; }

    private double getAverageDirectionAdjusted() {
        double sum = 0;
        for (AroonIndicator indicator : m_aroonIndicators) {
            double direction = indicator.getDirectionAdjusted();
            sum += direction;
        }
        return sum / m_aroonIndicators.size();
    }


    // --------------------------------------------------------------------------------------------
//    @Override public double getDirectionAdjusted() { // [-1 ... 1]
//        return m_aroonIndicator.getDirectionAdjusted();
//    }

//    @Override public double getDirectionAdjusted() { // [-1 ... 1]
//        Direction direction = m_aroonIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
//        return (direction == null) ? 0 : (direction.isForward() ? 1 : -1);
//    }

//    @Override public double getDirectionAdjusted() { // [-1 ... 1]
//        return getAverageDirectionAdjusted();
//    }

//    @Override public double getDirectionAdjusted() { // [-1 ... 1]
//        Direction direction = m_smoochedIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
//        return (direction == null) ? 0 : (direction.isForward() ? 1 : -1);
//    }

//    @Override public double getDirectionAdjusted() { // [-1 ... 1]
//        ChartPoint lastPoint = m_smoochedIndicator.getLastPoint();
//        return (lastPoint == null) ? 0 : lastPoint.m_value;
//    }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        return getDirectionAdjustedByPeakWatchers(m_andIndicator);
    }

    // --------------------------------------------------------------------------------------------
    @Override public Direction getDirection() { return m_andIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN

//    @Override public Direction getDirection() {
//        return m_smoochedIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
//    } // UP/DOWN

//    @Override public Direction getDirection() {
//        return Direction.get(getDirectionAdjusted());
//    } // UP/DOWN

//    @Override public Direction getDirection() {
//        return m_directionIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
//    } // UP/DOWN



    // ======================================================================================
    private class AroonIndicatorInt extends AroonIndicator {
        public Double m_lastValue;

        public AroonIndicatorInt(String name, double barRatio) {
            super(name, AroonAlgo.this, barRatio);
        }

//            @Override protected boolean countHalfPeaks() { return false; }

        @Override public void addBar(ChartPoint chartPoint) {
            super.addBar(chartPoint);
            ChartPoint lastPoint = getLastPoint();
            if (lastPoint != null) {
                double value = lastPoint.m_value;
                if ((m_lastValue == null) || (value != m_lastValue)) {
                    notifyListener();
                    m_lastValue = value;

                    double directionValue = getAverageDirectionAdjusted();
                    long millis = lastPoint.m_millis;
                    ChartPoint andPoint = new ChartPoint(millis, directionValue);
                    m_directionIndicator.addBar(andPoint);
                }
            }
        }
    }


    // ======================================================================================
    public static class DirectionIndicator extends TresIndicator {
        public DirectionIndicator(TresAlgo algo) {
            super("+", PEAK_TOLERANCE2, algo);
        }

//        @Override protected boolean countPeaks() { return false; }
        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.red; }
        @Override public Color getPeakColor() { return Color.red; }
    }


    // ======================================================================================
    public static class AndIndicator extends TresIndicator {
        public AndIndicator(TresAlgo algo) {
            super("+", PEAK_TOLERANCE4, algo);
        }
        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.CYAN; }
        @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
            double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
            minMaxCalculator.m_minValue = -max;
            minMaxCalculator.m_maxValue = max;
        }
    }


    // ======================================================================================
    public static class AroonSharpAlgo extends AroonAlgo {
        public AroonSharpAlgo(TresExchData exchData) {
            super("ARO!", exchData);
        }

        @Override public double getDirectionAdjusted() { // [-1 ... 1]
            Direction direction = m_andIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
            return (direction == null) ? 0 : (direction.isForward() ? 1 : -1);
        }

        // --------------------------------------------------------------------------------------------
        @Override public Direction getDirection() { return m_andIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN
    }
}
