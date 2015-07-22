package bthdg.tres;

import bthdg.exch.TradeData;

public class PhaseData {
    final TresExchData m_exchData;
    final int m_phaseIndex;
    final TresOscCalculator m_oscCalculator;
    final TresOHLCCalculator m_ohlcCalculator;
    final TresMaCalculator m_maCalculator;

    public PhaseData(TresExchData exchData, int phaseIndex) {
        m_exchData = exchData;
        m_phaseIndex = phaseIndex;
        m_oscCalculator = new TresOscCalculator(exchData, phaseIndex);
        m_ohlcCalculator = new TresOHLCCalculator(exchData.m_tres, phaseIndex);
        m_maCalculator = new TresMaCalculator(this, phaseIndex);
    }

    public boolean update(TradeData tdata) {
        long timestamp = tdata.m_timestamp;
        double price = tdata.m_price;
        m_oscCalculator.update(timestamp, price);
        boolean updated1 = m_ohlcCalculator.update(tdata);
        boolean updated2 = m_maCalculator.update(tdata);
        return updated1 || updated2;
    }

    public void getState(StringBuilder sb) {
        m_oscCalculator.getState(sb);
    }
}
