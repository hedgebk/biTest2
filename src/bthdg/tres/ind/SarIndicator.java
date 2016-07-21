package bthdg.tres.ind;

import bthdg.calc.SarCalculator;
import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;

import java.awt.*;

public class SarIndicator extends TresIndicator {
    public SarIndicator(TresAlgo algo) {
        super("SAR", 0, algo);
    }

    @Override public Color getColor() { return Colors.BEGIE; }
    @Override protected boolean countPeaks() { return false; }
    @Override protected boolean countHalfPeaks() { return false; }
    @Override protected boolean usePriceAxe() { return true; }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedSarIndicator(this, exchData, phaseIndex);
    }


    // ======================================================================================
    private static class PhasedSarIndicator extends TresPhasedIndicator {
        private final SarCalculator m_calculator;

        PhasedSarIndicator(SarIndicator indicator, TresExchData exchData, int phaseIndex) {
            super(indicator, exchData, phaseIndex, null);
            long barSize = exchData.m_tres.m_barSizeMillis;
            long barOffset = exchData.m_tres.getBarOffset(phaseIndex);
            m_calculator = new SarCalculator(barSize, barOffset) {
                @Override protected void finishCurrentBar(long time, double price) {
                    super.finishCurrentBar(time, price);
                    if (m_lastSar != null) {
                        ChartPoint tick = new ChartPoint(m_currentBarEnd, m_lastSar);
                        collectPointIfNeeded(tick);
                        onBar(tick);
                    }
                }
            };
        }

        @Override public Color getColor() { return Colors.BEGIE; }
        @Override public double lastTickPrice() { return m_calculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_calculator.m_lastTickTime; }
        @Override public boolean update(TradeDataLight tdata) { return m_calculator.update(tdata); }

        @Override public double getDirectionAdjusted() {  // [-1 ... 1]
            return m_calculator.getDirectionAdjusted();
        }
    }
}
