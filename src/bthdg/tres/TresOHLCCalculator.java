package bthdg.tres;

import bthdg.calc.OHLCCalculator;
import bthdg.calc.OHLCTick;

import java.util.LinkedList;

public class TresOHLCCalculator extends OHLCCalculator {
    final LinkedList<OHLCTick> m_ohlcTicks = new LinkedList<OHLCTick>();

    public TresOHLCCalculator(Tres tres, int phaseIndex) {
        super(tres.m_barSizeMillis, getOffset(phaseIndex, tres));
    }

    private static long getOffset(int index, Tres tres) {
        return tres.m_barSizeMillis * (index % tres.m_phases) / tres.m_phases;
    }

    @Override protected void onBarStarted(OHLCTick tick) {
        synchronized (m_ohlcTicks) {
            m_ohlcTicks.add(tick);
        }
    }
}
