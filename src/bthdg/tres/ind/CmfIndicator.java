package bthdg.tres.ind;

import bthdg.calc.CmfCalculator;
import bthdg.exch.TradeDataLight;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.TresLogProcessor;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;

import java.awt.*;

public class CmfIndicator extends TresIndicator {
    public static double LEVEL = 0.05;
    public static int LENGTH = 20;
    public static int LENGTH2 = 25;

    private final int m_length;

    public CmfIndicator(double peakTolerance, TresAlgo algo) {
        this(LENGTH, peakTolerance, algo);
    }
    public CmfIndicator(int length, double peakTolerance, TresAlgo algo) {
        super("CMF", peakTolerance, algo);
        m_length = length;
        TresLogProcessor.PARSE_FULL_TRADES = true;
    }

    @Override public Color getColor() { return Colors.BEGIE; }
    @Override protected boolean countPeaks() { return false; }
    @Override protected boolean countHalfPeaks() { return false; }
    @Override protected boolean drawZeroLine() { return true; }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedCmfIndicator(m_length, this, exchData, phaseIndex);
    }


    // ======================================================================================
    private static class PhasedCmfIndicator extends TresPhasedIndicator {
        private final CmfCalculator m_calculator;

        PhasedCmfIndicator(int length, CmfIndicator cmfIndicator, TresExchData exchData, int phaseIndex) {
            super(cmfIndicator, exchData, phaseIndex, null);
            long barSize = exchData.m_tres.m_barSizeMillis;
            long barOffset = exchData.m_tres.getBarOffset(phaseIndex);
            m_calculator = new CmfCalculator(length, barSize, barOffset) {
                @Override protected void finishCurrentBar(long time, double price) {
                    super.finishCurrentBar(time, price);
                    if (m_lastCmf != null) {
                        ChartPoint tick = new ChartPoint(m_currentBarEnd, m_lastCmf);
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
            Double lastCmf = m_calculator.m_lastCmf;
            return (lastCmf == null) ? 0 : (lastCmf >= LEVEL) ? 1 : (lastCmf <= -LEVEL) ? -1 : lastCmf / LEVEL;
        }
    }
}
