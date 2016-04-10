package bthdg.tres.ind;

import bthdg.calc.FractalCalculator;
import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;

import java.awt.*;

public class FractalIndicator extends TresIndicator {
    public FractalIndicator(TresAlgo algo) {
        super("fra", 0, algo);
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedFractalIndicator(this, exchData, phaseIndex);
    }

    @Override public Color getColor() { return Color.blue; }

    protected void onPhaseDirectionChanged() {}

    // ======================================================================================
    public static class PhasedFractalIndicator extends TresPhasedIndicator {
        private final FractalCalculator m_calculator;

        @Override public void onBar(ChartPoint lastBar) {
            super.onBar(lastBar);
        }

        public PhasedFractalIndicator(FractalIndicator fractalIndicator, TresExchData exchData, int phaseIndex) {
            super(fractalIndicator, exchData, phaseIndex, null);
            m_calculator = new FractalCalculator(exchData.m_tres.m_barSizeMillis, exchData.m_tres.getBarOffset(phaseIndex)) {
                @Override protected void onDirectionChanged(long time, double direction) {
                    super.onDirectionChanged(time, direction);
                    ChartPoint tick = new ChartPoint(time, direction);
                    collectPointIfNeeded(tick);
                    onBar(tick);
                    ((FractalIndicator)m_indicator).onPhaseDirectionChanged();
                }

                @Override protected void finishCurrentBar(long time, double price) {
                    super.finishCurrentBar(time, price);
                    ChartPoint tick = new ChartPoint(time, m_direction);
                    onBar(tick);
                }
            };
        }

        @Override public boolean update(TradeDataLight tdata) {
            long timestamp = tdata.m_timestamp;
            double price = tdata.m_price;
            return m_calculator.update(timestamp, price);
        }

        @Override public Color getColor() { return Color.BLUE; }
        @Override public double lastTickPrice() { return m_calculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_calculator.m_lastTickTime; }
        @Override public double getDirectionAdjusted() { return m_calculator.m_direction; }
    }
}
