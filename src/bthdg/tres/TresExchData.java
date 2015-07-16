package bthdg.tres;

import bthdg.Log;
import bthdg.exch.TradeData;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;

import java.util.LinkedList;

public class TresExchData implements ITradesListener {
    final Tres m_tres;
    final IWs m_ws;
    final TresOscCalculator[] m_oscCalculators;
    private final OHLCCalculator m_ohlcCalc;
    LinkedList<OHLCTick> m_ohlcTicks = new LinkedList<OHLCTick>();
    double m_lastPrice;
    private boolean m_updated;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    public TresExchData(Tres tres, IWs ws) {
        m_tres = tres;
        m_ws = ws;
        m_oscCalculators = new TresOscCalculator[tres.m_phases];
        for (int i = 0; i < tres.m_phases; i++) {
            m_oscCalculators[i] = new TresOscCalculator(this, i);
        }
        m_ohlcCalc = new OHLCCalculator(m_tres.m_barSizeMillis){
            @Override protected void finishBar(OHLCTick tick) {
                m_ohlcTicks.add(tick);
            }
        };
    }

    public void start() {
        try {
            m_ws.subscribeTrades(Tres.PAIR, this);
        } catch (Exception e) {
            err("error subscribeTrades[" + m_ws.exchange() + "]: " + e, e);
        }
    }

    @Override public void onTrade(TradeData tdata) {
        log("onTrade[" + m_ws.exchange() + "]: " + tdata);
        m_updated = false;
        long timestamp = tdata.m_timestamp;
        double price = tdata.m_price;
        m_lastPrice = price;
        for (int i = 0; i < m_tres.m_phases; i++) {
            m_oscCalculators[i].update(timestamp, price);
        }
        boolean updated = m_ohlcCalc.update(tdata);
        m_updated = updated || m_updated;
        if (m_updated) {
            m_tres.fireUpdated();
        }
    }

    public void stop() {
        m_ws.stop();
    }

    public void setUpdated() { m_updated = true; }

    public void getState(StringBuilder sb) {
        sb.append("[" + m_ws.exchange() + "]: last=" + m_lastPrice);
        for (int i = 0; i < m_tres.m_phases; i++) {
            m_oscCalculators[i].getState(sb);
        }
    }

}
