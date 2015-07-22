package bthdg.tres;

import bthdg.Log;
import bthdg.exch.TradeData;

import java.util.LinkedList;

public class TresMaCalculator extends MaCalculator {
    final LinkedList<MaTick> m_maTicks = new LinkedList<MaTick>();
    private MaTick m_tick;
    private Boolean m_lastPriceHigher;

    private static void log(String s) { Log.log(s); }

    public TresMaCalculator(TresExchData exchData, int phaseIndex) {
        super(exchData, phaseIndex, MaType.CLOSE, exchData.m_tres.m_ma);
    }

    @Override protected void startMaBar(long barEnd) {
        m_tick = new MaTick(barEnd);
        m_maTicks.add(m_tick);
        m_lastPriceHigher = null; // reset for new tick
//        log(" startMaBar");
    }

    @Override protected void updateMaBar(double ma) {
        m_tick.m_ma = ma;
//        log(" updateMaBar ma=" + ma);
    }

    @Override public boolean update(TradeData tdata) {
        boolean ret = super.update(tdata);

        double currentPrice = tdata.m_price;
        double currentMa = m_tick.m_ma;
        boolean currentPriceHigher = (currentPrice > currentMa);
//        log(" currentPriceHigher=" + currentPriceHigher + ": currentPrice=" + currentPrice + "; currentMa=" + currentMa);
//        log("  m_lastPriceHigher=" + m_lastPriceHigher);

        if (m_lastPriceHigher != null) {
            if (m_lastPriceHigher != currentPriceHigher) {
//                log("   onMaCross()");
                onMaCross();
            }
        }
        m_lastPriceHigher = currentPriceHigher;
        return ret;
    }

    private void onMaCross() {
        m_tick.m_maCrossed = true;
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
