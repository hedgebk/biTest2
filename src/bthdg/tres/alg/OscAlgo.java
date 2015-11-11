package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.TresOscCalculator;
import bthdg.tres.ind.OscIndicator;
import bthdg.tres.ind.TresIndicator;

import java.awt.*;

public class OscAlgo extends TresAlgo {
    final OscIndicator m_oscIndicator;
    private final DirectionIndicator m_directionIndicator;

    public OscAlgo(TresExchData exchData) {
        super("OSC", exchData);
        m_oscIndicator = new OscIndicator(this) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    double value = getDirectionAdjustedByPeakWatchers(m_oscIndicator);
                    long millis = lastPoint.m_millis;
                    ChartPoint andPoint = new ChartPoint(millis, value);
                    m_directionIndicator.addBar(andPoint);

//                    Direction direction = getDirection();
//                    if (direction != m_lastDirection) {
//                        notifyListener();
//                    }
//                    m_lastDirection = direction;
                }
            }

        };
        m_indicators.add(m_oscIndicator);

        m_directionIndicator = new DirectionIndicator(this);
        m_indicators.add(m_directionIndicator);
    }

    @Override public String getRunAlgoParams() {
        return "OSC: tolerance=" + m_oscIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance
                + " oskLock=" + TresOscCalculator.LOCK_OSC_LEVEL;
    }

    @Override public double lastTickPrice() { return m_oscIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_oscIndicator.lastTickTime(); }


    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        return getDirectionAdjustedByPeakWatchers(m_oscIndicator);
    }
    @Override public Direction getDirection() { return m_oscIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN


    public static class DirectionIndicator extends TresIndicator {
        public static double PEAK_TOLERANCE = 0.06470;

        public DirectionIndicator(TresAlgo algo) {
            super("+", PEAK_TOLERANCE, algo);
        }

        @Override protected boolean countPeaks() { return false; }
        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.red; }
        @Override public Color getPeakColor() { return Color.red; }
    }
}
