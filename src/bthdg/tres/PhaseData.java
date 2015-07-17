package bthdg.tres;

import bthdg.exch.TradeData;

import java.util.LinkedList;

public class PhaseData {
    final TresExchData m_exchData;
    final int m_phaseIndex;
    final TresOscCalculator m_oscCalculator;
    final TresOHLCCalculator m_ohlcCalculator;
    final LinkedList<OHLCTick> m_ohlcTicks = new LinkedList<OHLCTick>();

    public PhaseData(TresExchData exchData, int phaseIndex) {
        m_exchData = exchData;
        m_phaseIndex = phaseIndex;
        m_oscCalculator = new TresOscCalculator(exchData, phaseIndex);
        m_ohlcCalculator = new TresOHLCCalculator(this, phaseIndex);
    }

    public boolean update(TradeData tdata) {
        long timestamp = tdata.m_timestamp;
        double price = tdata.m_price;
        m_oscCalculator.update(timestamp, price);
        return m_ohlcCalculator.update(tdata);
    }

    public void getState(StringBuilder sb) {
        m_oscCalculator.getState(sb);
    }

    public void onOHLCTickStart(OHLCTick tick) {
        m_ohlcTicks.add(tick);
    }
}
