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
                 notifyListener();
            }
        };
        m_indicators.add(m_fractalIndicator);

    }

    @Override public double lastTickPrice() { return m_fractalIndicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_fractalIndicator.lastTickTime(); }
    @Override public Color getColor() { return Color.BLUE; }
    @Override public String getRunAlgoParams() { return "FRA"; }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        return m_fractalIndicator.getDirectionAdjusted();
    }
    @Override public Direction getDirection() { return Direction.get(getDirectionAdjusted()); } // UP/DOWN
}
