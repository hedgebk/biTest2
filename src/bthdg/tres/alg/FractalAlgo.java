package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.FractalIndicator;

import java.awt.*;

public class FractalAlgo extends TresAlgo {
    private final FractalIndicator m_fractalIndicator;

    public FractalAlgo(TresExchData tresExchData) {
        super("FRA", tresExchData);

        m_fractalIndicator = new FractalIndicator(this) {
            @Override protected void onPhaseDirectionChanged() { }

            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
//                ChartPoint lastPoint = getLastPoint();
//                if (lastPoint != null) {
//                    double value = getDirectionAdjustedByPeakWatchers(m_fractalIndicator);
//                    long millis = lastPoint.m_millis;
//                    ChartPoint andPoint = new ChartPoint(millis, value);
//                    m_directionIndicator.addBar(andPoint);
//
//                    double value2 = getDirectionAdjusted();
//                    ChartPoint andPoint2 = new ChartPoint(millis, value2);
//                    m_directionIndicator2.addBar(andPoint2);
//
////                    Direction direction = getDirection();
////                    if (direction != m_lastDirection) {
 notifyListener();
////                    }
////                    m_lastDirection = direction;
//                }
            }
        };
        m_indicators.add(m_fractalIndicator);

    }

    @Override public double lastTickPrice() { return m_fractalIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_fractalIndicator.lastTickTime(); }
    @Override public Color getColor() { return Color.BLUE; }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        return m_fractalIndicator.getDirectionAdjusted();
    }
    @Override public Direction getDirection() { return Direction.get(getDirectionAdjusted()); } // UP/DOWN

    @Override public String getRunAlgoParams() {
        return "FRA";
    }

}
