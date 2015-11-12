package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CciIndicator;
import bthdg.tres.ind.TresIndicator;

import java.awt.*;

public class CciAlgo extends TresAlgo {
    final CciIndicator m_cciIndicator;
    private final DirectionIndicator m_directionIndicator;
    private final DirectionIndicator m_directionIndicator2;

    public CciAlgo(TresExchData tresExchData) {
        super("CCI", tresExchData);
        m_cciIndicator = new CciIndicator(this) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    double value = getDirectionAdjustedByPeakWatchers(m_cciIndicator);
                    long millis = lastPoint.m_millis;
                    ChartPoint andPoint = new ChartPoint(millis, value);
                    m_directionIndicator.addBar(andPoint);

                    double value2 = getDirectionAdjusted();
                    ChartPoint andPoint2 = new ChartPoint(millis, value2);
                    m_directionIndicator2.addBar(andPoint2);

//                    Direction direction = getDirection();
//                    if (direction != m_lastDirection) {
//                        notifyListener();
//                    }
//                    m_lastDirection = direction;
                }
            }
        };
        m_indicators.add(m_cciIndicator);

        m_directionIndicator = new DirectionIndicator(this);
        m_indicators.add(m_directionIndicator);

        m_directionIndicator2 = new DirectionIndicator(this) {
            @Override public Color getColor() { return Color.orange; }
            @Override public Color getPeakColor() { return Color.orange; }
        };
        m_indicators.add(m_directionIndicator2);
    }

    @Override public double lastTickPrice() { return m_cciIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_cciIndicator.lastTickTime(); }
    @Override public Color getColor() { return Color.red; }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        return getDirectionAdjustedByPeakWatchers(m_cciIndicator);
    }
    @Override public Direction getDirection() { return m_cciIndicator.m_peakWatcher.m_avgPeakCalculator.m_direction; } // UP/DOWN

    @Override public String getRunAlgoParams() {
        return "CCI.tolerance=" + m_cciIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance;
    }


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
