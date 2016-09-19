package bthdg.tres.ind;

import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.LinearRegressionPowersAlgo;
import bthdg.tres.alg.TresAlgo;

import java.awt.*;

public class SlidingGainerIndicator extends TresAlgo.ValueIndicator {
    private final LinearRegressionPowersAlgo.SlidingGainer m_gainer;
    private double m_lastGainerValue = Double.MAX_VALUE;

    public SlidingGainerIndicator(TresAlgo algo, String name, Color color, double gainPower) {
        super(algo, name, color);
        m_gainer = new LinearRegressionPowersAlgo.SlidingGainer(gainPower);
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
    @Override public Color getColor() { return Color.green; }
    @Override public Color getPeakColor() { return Color.green; }
    @Override public double getDirectionAdjusted() { return m_lastGainerValue; }

    @Override public void addBar(ChartPoint chartPoint) {
        if (chartPoint != null) {
            double value = chartPoint.m_value;
            double gainerValue = m_gainer.update(value);
            if (gainerValue != m_lastGainerValue) {
                m_lastGainerValue = gainerValue;

                long millis = chartPoint.m_millis;
                ChartPoint gainPoint = new ChartPoint(millis, gainerValue);
                super.addBar(gainPoint);
            }
        }
    }
}

