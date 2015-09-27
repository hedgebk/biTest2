package bthdg.tres;

import bthdg.calc.OHLCCalculator;
import bthdg.calc.OHLCTick;

import java.util.LinkedList;

public class TresOHLCCalculator extends OHLCCalculator {
    private final Tres m_tres;
    final LinkedList<OHLCTick> m_ohlcTicks = new LinkedList<OHLCTick>();

    public TresOHLCCalculator(Tres tres, int phaseIndex) {
        super(tres.m_barSizeMillis, tres.getBarOffset(phaseIndex));
        m_tres = tres;
    }

    @Override protected void startNewBar(long barStart, long barEnd) {
        super.startNewBar(barStart, barEnd);
        if (m_tres.m_collectPoints) {
            synchronized (m_ohlcTicks) {
                m_ohlcTicks.add(m_tick);
            }
        }
    }
}
