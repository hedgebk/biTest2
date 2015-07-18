package bthdg.tres;

import java.util.LinkedList;

public class TresMaCalculator extends MaCalculator {
    final LinkedList<MaTick> m_maTicks = new LinkedList<MaTick>();
    private MaTick m_tick;

    public TresMaCalculator(TresExchData exchData, int phaseIndex) {
        super(exchData, phaseIndex, MaType.CLOSE, exchData.m_tres.m_ma);
    }

    @Override protected void startMaBar(long barEnd) {
        m_tick = new MaTick(barEnd);
        m_maTicks.add(m_tick);
    }

    @Override protected void updateMaBar(double ma) {
        m_tick.m_ma = ma;
    }

    public static class MaTick {
        protected final long m_barEnd;
        protected double m_ma;

        public MaTick(long barEnd) {
            m_barEnd = barEnd;
        }
    }
}
