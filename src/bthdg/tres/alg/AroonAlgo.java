package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.AroonIndicator;
import bthdg.tres.ind.SmoochedIndicator;
import bthdg.tres.ind.TresIndicator;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class AroonAlgo extends TresAlgo {
    public static double PEAK_TOLERANCE2 = 0.48; // DirectionIndicator tolerance
    public static double PEAK_TOLERANCE3 = 0.049200; // SmoochedIndicator tolerance

    public static double BAR_RATIOS_STEP = 1.065;
    public static int BAR_RATIOS_STEP_NUM = 6;
    public static double SMOOTH_RATE = 2.0;

    private final List<AroonIndicator> m_aroonIndicators = new ArrayList<AroonIndicator>();
    private final DirectionIndicator m_directionIndicator;
    protected final SmoochedIndicator m_smoochedIndicator;

    @Override public double lastTickPrice() { return m_aroonIndicators.get(0).lastTickPrice(); }
    @Override public long lastTickTime() { return m_aroonIndicators.get(0).lastTickTime(); }
    @Override public Color getColor() { return AroonIndicator.COLOR; }

    protected void onSmoochedBar() {}

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

        long barSizeMillis = exchData.m_tres.m_barSizeMillis;
        double rate = Math.pow(BAR_RATIOS_STEP, BAR_RATIOS_STEP_NUM - 1) * SMOOTH_RATE;
        long frameSizeMillis = (long) (rate * barSizeMillis);
        m_smoochedIndicator = new SmoochedIndicator(this, "sm", frameSizeMillis, PEAK_TOLERANCE3) {
            @Override public Color getColor() { return Color.lightGray; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                onSmoochedBar();
            }
        };
        m_indicators.add(m_smoochedIndicator);
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

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        ChartPoint lastPoint = m_smoochedIndicator.getLastPoint();
        return (lastPoint == null) ? 0 : lastPoint.m_value;
    }

    // --------------------------------------------------------------------------------------------
    @Override public Direction getDirection() {
        return m_smoochedIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
    } // UP/DOWN

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
    protected static class DirectionIndicator extends TresIndicator {
        public DirectionIndicator(TresAlgo algo) {
            super("+", PEAK_TOLERANCE2, algo);
        }

//        @Override protected boolean countPeaks() { return false; }
        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.red; }
        @Override public Color getPeakColor() { return Color.red; }
    }


    // ======================================================================================
    public static class AroonFasterAlgo extends AroonAlgo {
        public AroonFasterAlgo(TresExchData exchData) {
            super("AROf", exchData);
        }

        @Override public double getDirectionAdjusted() { // [-1 ... 1]
            return getDirectionAdjustedByPeakWatchers(m_smoochedIndicator);
        }

        @Override public Direction getDirection() { return m_smoochedIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN
    }
}
