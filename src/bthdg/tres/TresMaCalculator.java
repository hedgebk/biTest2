package bthdg.tres;

import bthdg.Log;
import bthdg.exch.TradeData;
import bthdg.osc.OscTick;

import java.util.LinkedList;

public class TresMaCalculator extends MaCalculator {
    private final PhaseData m_phaseData;
    final LinkedList<MaTick> m_maTicks = new LinkedList<MaTick>();
    final LinkedList<MaCrossData> m_maCrossDatas = new LinkedList<MaCrossData>();
    private MaTick m_tick;
    private Boolean m_lastPriceHigher;

    private static void log(String s) { Log.log(s); }

    public TresMaCalculator(PhaseData phaseData, int phaseIndex) {
        super(phaseData.m_exchData, phaseIndex, MaType.CLOSE, phaseData.m_exchData.m_tres.m_ma);
        m_phaseData = phaseData;
    }

    @Override protected void startMaBar(long barEnd) {
        m_tick = new MaTick(barEnd);
        m_maTicks.add(m_tick);
    }

    @Override protected void updateMaBar(double ma) {
        m_tick.m_ma = ma;
    }

    @Override public boolean update(TradeData tdata) {
        boolean ret = super.update(tdata);

        double currentPrice = tdata.m_price;
        double currentMa = m_tick.m_ma;
        boolean currentPriceHigher = (currentPrice > currentMa);

        if (m_lastPriceHigher != null) {
            if (m_lastPriceHigher != currentPriceHigher) {
                onMaCross(tdata);
            }
        }
        m_lastPriceHigher = currentPriceHigher;
        return ret;
    }

    private void onMaCross(TradeData tdata) {
        m_tick.m_maCrossed = true;
        long timestamp = tdata.m_timestamp;
        double price = tdata.m_price;
        OscTick lastFineTick = m_phaseData.m_oscCalculator.m_lastFineTick;
        if (lastFineTick != null) {
            double val1 = lastFineTick.m_val1;
            double val2 = lastFineTick.m_val2;
            boolean oscUp = (val1 > val2);
            MaCrossData maCrossData = new MaCrossData(timestamp, oscUp, price);
            m_maCrossDatas.add(maCrossData);
        }
    }

    public static class MaCrossData {
        final long m_timestamp;
        final boolean m_oscUp;
        final double m_price;

        public MaCrossData(long timestamp, boolean oscUp, double price) {
            m_timestamp = timestamp;
            m_oscUp = oscUp;
            m_price = price;
        }
    }

    public static class MaTick {
        protected final long m_barEnd;
        protected double m_ma;
        public boolean m_maCrossed;

        public MaTick(long barEnd) {
            m_barEnd = barEnd;
        }
    }
}
