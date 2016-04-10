package bthdg.tres.ind;

import bthdg.calc.CmfCalculator;
import bthdg.exch.TradeDataLight;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;

import java.awt.*;

public class CmfIndicator extends TresIndicator {
    public static int LENGTH = 20;


    public CmfIndicator(double peakTolerance, TresAlgo algo) {
        super("CMF", peakTolerance, algo);
    }

    @Override public Color getColor() { return Colors.BEGIE; }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) {
        return new PhasedCmfIndicator(LENGTH, this, exchData, phaseIndex);
    }


    // ======================================================================================
    private static class PhasedCmfIndicator extends TresPhasedIndicator {
        private final CmfCalculator m_calculator;

        PhasedCmfIndicator(int length, CmfIndicator cmfIndicator, TresExchData exchData, int phaseIndex) {
            super(cmfIndicator, exchData, phaseIndex, 0.0);
            long barSize = exchData.m_tres.m_barSizeMillis;
            long barOffset = exchData.m_tres.getBarOffset(phaseIndex);
            m_calculator = new CmfCalculator(length, barSize, barOffset);
        }

        @Override public Color getColor() { return Colors.BEGIE; }
        @Override public double lastTickPrice() { return m_calculator.m_lastTickPrice; }
        @Override public long lastTickTime() { return m_calculator.m_lastTickTime; }
        @Override public boolean update(TradeDataLight tdata) {
            long timestamp = tdata.m_timestamp;
            double price = tdata.m_price;
            return m_calculator.update(timestamp, price);
        }
    }
}
