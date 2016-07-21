package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.SarIndicator;
import bthdg.util.Colors;

import java.awt.*;

public class SarAlgo extends TresAlgo {
    private final SarIndicator m_sarIndicator;

    public SarAlgo(TresExchData tresExchData) {
        super("SAR", tresExchData);
        m_sarIndicator = new SarIndicator(this) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
//                    recalc(lastPoint.m_millis);
                    notifyListener();
                }
            }
        };
        m_indicators.add(m_sarIndicator);
    }

    @Override public double lastTickPrice() { return m_sarIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_sarIndicator.lastTickTime(); }
    @Override public Color getColor() { return Colors.BEGIE; }
    @Override public double getDirectionAdjusted() { return m_sarIndicator.getDirectionAdjusted(); }
    @Override public Direction getDirection() { return Direction.get(getDirectionAdjusted()); } // UP/DOWN
    @Override public String getRunAlgoParams() { return "SAR"; }
}
