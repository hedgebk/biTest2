package bthdg.tres.alg;

import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.LinearRegressionPowerIndicator;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class LinearRegressionPowersAlgo extends TresAlgo {
    public static int LEN_STEPS_NUM = 5;
    public static int LEN_STEP_START = 10;
    public static int LEN_STEP = 10;

    private final List<LinearRegressionPowerIndicatorInt> m_lrpaIndicators = new ArrayList<LinearRegressionPowerIndicatorInt>();
    protected final long m_barSizeMillis;
    protected final int m_phases;
    private double m_lastAvg;
    private final ValueIndicator m_directionIndicator;

    @Override public double lastTickPrice() { return m_lrpaIndicators.get(0).lastTickPrice(); }
    @Override public long lastTickTime() { return m_lrpaIndicators.get(0).lastTickTime(); }
    @Override public Color getColor() { return Colors.BROWN; }
    @Override public double getDirectionAdjusted() { return 0; }

    LinearRegressionPowersAlgo(TresExchData exchData) {
        super("LRPAs", exchData);

        m_barSizeMillis = exchData.m_tres.m_barSizeMillis;
        m_phases = exchData.m_tres.m_phases;

        int len = LEN_STEP_START;
        for (int i = 0; i < LEN_STEPS_NUM; i++) {
            LinearRegressionPowerIndicatorInt ind = new LinearRegressionPowerIndicatorInt(len);
            m_indicators.add(ind);
            m_lrpaIndicators.add(ind);
            len += LEN_STEP;
        }

        m_directionIndicator = new ValueIndicator(this, "a", Color.blue);
        m_indicators.add(m_directionIndicator);
    }

    private double getAverageDirectionAdjusted() {
        double sum = 0;
        for (LinearRegressionPowerIndicatorInt indicator : m_lrpaIndicators) {
            double direction = indicator.getDirectionAdjusted();
            sum += direction;
        }
        return sum / LEN_STEPS_NUM;
    }

    @Override public void postUpdate(TradeDataLight tdata) {
        double avg = getAverageDirectionAdjusted();
        if (m_lastAvg != avg) {
            m_lastAvg = avg;
            long millis = tdata.m_timestamp;
            ChartPoint andPoint = new ChartPoint(millis, avg);
            m_directionIndicator.addBar(andPoint);
            notifyListener();
        }
    }

    @Override public String getRunAlgoParams() {
        return "LRPs: barSize=" + m_barSizeMillis +
                "; phases=" + m_phases +
                "; len=[sta=" + LEN_STEP_START + ", ste=" + LEN_STEP + ", num=" + LEN_STEPS_NUM + "]" +
                "; power=" + Utils.format5(LinearRegressionPowerIndicator.POW);
    }

    // ------------------------------------------------------------------------------------
    private class LinearRegressionPowerIndicatorInt extends LinearRegressionPowerIndicator {
        public LinearRegressionPowerIndicatorInt(int len) {
            super(LinearRegressionPowersAlgo.this, len);
        }

        @Override protected boolean drawZeroLine() { return true; }
        @Override protected boolean useValueAxe() { return true; }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
            return new PhasedLinearRegressionPowerIndicator(this, exchData, phaseIndex) {
                @Override public double getDirectionAdjusted() {
                    return m_calculator.getVal();
                }
            };
        }

    }
}
