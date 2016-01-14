package bthdg.tres.alg;

import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.AroonIndicator;

import java.awt.*;

public class AroonAlgo extends TresAlgo {
    private final AroonIndicator m_aroonIndicator;

    @Override public double lastTickPrice() { return m_aroonIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_aroonIndicator.lastTickTime(); }
    @Override public Color getColor() { return AroonIndicator.COLOR; }

    public AroonAlgo(TresExchData exchData) {
        super("ARO", exchData);
        m_aroonIndicator = new AroonIndicator(this) {
            public Double m_lastValue;

            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    double value = lastPoint.m_value;
                    if ((m_lastValue == null) || (value != m_lastValue)) {
                        notifyListener();
                        m_lastValue = value;
                    }
                }
            }
        };
        m_indicators.add(m_aroonIndicator);
    }

    @Override public String getRunAlgoParams() { return "Aroon"; }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        return m_aroonIndicator.getDirectionAdjusted();
    }
    @Override public Direction getDirection() {
        return Direction.get(getDirectionAdjusted());
    } // UP/DOWN
}
