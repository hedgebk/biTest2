package bthdg.tres.alg;

import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.LinearRegressionPowerIndicator;
import bthdg.tres.ind.SmoochedIndicator;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;

public class LinearRegressionPowerAlgo extends TresAlgo {
    private final LinearRegressionPowerIndicator m_indicator;
    protected final long m_barSizeMillis;
    protected final int m_phases;
    private double m_direction;

    @Override public double lastTickPrice() { return m_indicator.lastTickPrice(); }
    @Override public long lastTickTime() { return m_indicator.lastTickTime(); }
    @Override public Color getColor() { return Colors.BROWN; }
    @Override public double getDirectionAdjusted() { return m_direction; }

    LinearRegressionPowerAlgo(TresExchData exchData) {
        super("LRPA", exchData);

        m_barSizeMillis = exchData.m_tres.m_barSizeMillis;
        m_phases = exchData.m_tres.m_phases;

        m_indicator = new LinearRegressionPowerIndicator(this) {
            @Override protected boolean drawZeroLine() { return true; }
            @Override protected boolean useValueAxe() { return true; }
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    double direction = lastPoint.m_value;
                    if (direction != m_direction) {
                        m_direction = direction;
                        onDirectionChanged(lastPoint);
                    }
                }
            }

        };
        m_indicators.add(m_indicator);
    }

    protected void onDirectionChanged(ChartPoint lastPoint) {
        notifyListener();
    }

    @Override public String getRunAlgoParams() {
        return "LRP: barSize=" + m_barSizeMillis +
                "; phases=" + m_phases +
                "; len=" + LinearRegressionPowerIndicator.LENGTH +
                "; power=" + Utils.format5(LinearRegressionPowerIndicator.POW);
    }


    // --------------------------------------------------------------------------
    public static class Smoothed extends LinearRegressionPowerAlgo {
        public static double SMOOCH_RATE = 2;  // "lrps.smooth"

        private final SmoochedIndicator m_smoochedSumIndicator;
        private double m_lastSmotchedValue;

        @Override public double getDirectionAdjusted() { return m_lastSmotchedValue; }

        @Override public String getRunAlgoParams() {
            return "LRPS: barSize=" + m_barSizeMillis +
                    "; phases=" + m_phases +
                    "; len=" + LinearRegressionPowerIndicator.LENGTH +
                    "; power=" + Utils.format5(LinearRegressionPowerIndicator.POW) +
                    "; smooth=" + Utils.format5(SMOOCH_RATE) ;
        }

        Smoothed(TresExchData exchData) {
            super(exchData);

            final long barSizeMillis = exchData.m_tres.m_barSizeMillis;
            m_smoochedSumIndicator = new SmoochedIndicator(this, "ss", (long) (SMOOCH_RATE * barSizeMillis)) {
                @Override protected boolean countPeaks() { return false; }
                @Override protected ILineColor getLineColor() { return ILineColor.PRICE; }
                @Override protected boolean useValueAxe() { return true; }
                @Override public void addBar(ChartPoint chartPoint) {
                    super.addBar(chartPoint);
                    m_lastSmotchedValue = getLastPoint().m_value;
                }
            };
            m_indicators.add(m_smoochedSumIndicator);
        }

        @Override protected void onDirectionChanged(ChartPoint lastPoint) {
            m_smoochedSumIndicator.addBar(lastPoint);
            notifyListener();
        }
    }
}
