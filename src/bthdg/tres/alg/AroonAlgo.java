package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.AroonIndicator;
import bthdg.tres.ind.TresIndicator;

import java.awt.*;

public class AroonAlgo extends TresAlgo {
    private final AroonIndicator m_aroonIndicator;
    private final DirectionIndicator m_directionIndicator;
    private final AroonIndicatorInt m_aroonIndicatorFast;
    private final AroonIndicatorInt m_aroonIndicatorSlow;

    @Override public double lastTickPrice() { return m_aroonIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_aroonIndicator.lastTickTime(); }
    @Override public Color getColor() { return AroonIndicator.COLOR; }

    public AroonAlgo(TresExchData exchData) {
        super("ARO", exchData);
        m_aroonIndicator = new AroonIndicatorInt(1.0);
        m_indicators.add(m_aroonIndicator);

        m_aroonIndicatorFast = new AroonIndicatorInt(1.5);
        m_indicators.add(m_aroonIndicatorFast);

        m_aroonIndicatorSlow = new AroonIndicatorInt(0.7);
        m_indicators.add(m_aroonIndicatorSlow);

        m_directionIndicator = new DirectionIndicator(this);
        m_indicators.add(m_directionIndicator);
    }

    @Override public String getRunAlgoParams() { return "Aroon"; }


//    @Override public double getDirectionAdjusted() { // [-1 ... 1]
//        return m_aroonIndicator.getDirectionAdjusted();
//    }

//    @Override public double getDirectionAdjusted() { // [-1 ... 1]
//        Direction direction = m_aroonIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
//        return (direction == null) ? 0 : (direction.isForward() ? 1 : -1);
//    }

//    @Override public double getDirectionAdjusted() { // [-1 ... 1]
//        double direction = getDirectionAdjustedByPeakWatchers(m_aroonIndicator);
//        double directionFast = getDirectionAdjustedByPeakWatchers(m_aroonIndicatorFast);
//        double directionSlow = getDirectionAdjustedByPeakWatchers(m_aroonIndicatorSlow);
//        return (direction + directionFast + directionSlow) / 3;
//    }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        double direction = m_aroonIndicator.getDirectionAdjusted();
        double directionFast = m_aroonIndicatorFast.getDirectionAdjusted();
        double directionSlow = m_aroonIndicatorSlow.getDirectionAdjusted();
        return (direction + directionFast + directionSlow) / 3;
    }


//    @Override public Direction getDirection() {
//        return Direction.get(getDirectionAdjusted());
//    } // UP/DOWN


    @Override public Direction getDirection() {
        return m_directionIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
    } // UP/DOWN

//    @Override public Direction getDirection() { // UP/DOWN
//        Direction direction = m_aroonIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
//        Direction directionFast = m_aroonIndicatorFast.m_peakWatcher.m_avgPeakCalculator.m_direction;
//        Direction directionSlow = m_aroonIndicatorSlow.m_peakWatcher.m_avgPeakCalculator.m_direction;
//        if ((direction == null) || (directionFast == null) || (directionSlow == null)) {
//            return null;
//        }
//        boolean up = direction.isForward();
//        boolean upFast = directionFast.isForward();
//        boolean upSlow = directionSlow.isForward();
//
//        int upCount = 0;
//        if (up) {
//            upCount++;
//        }
//        if (upFast) {
//            upCount++;
//        }
//        if (upSlow) {
//            upCount++;
//        }
//        return Direction.get((upCount / 3 - 0.5) * 2);
//    }


    // ======================================================================================
    private class AroonIndicatorInt extends AroonIndicator {
        public Double m_lastValue;

        public AroonIndicatorInt(double barRatio) {
            super(AroonAlgo.this, barRatio);
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

                    double directionValue = AroonAlgo.this.getDirectionAdjusted();
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
            super("+", 0.5, algo);
        }

//        @Override protected boolean countPeaks() { return false; }
        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.red; }
        @Override public Color getPeakColor() { return Color.red; }
    }
}
