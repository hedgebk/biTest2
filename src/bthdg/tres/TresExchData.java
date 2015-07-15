package bthdg.tres;

import bthdg.Log;
import bthdg.exch.TradeData;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;

public class TresExchData implements ITradesListener {
    final Tres m_tres;
    final IWs m_ws;
    private final TresOscCalculator[] m_oscCalculators;
    private boolean m_updated;
    private double m_lastPrice;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    public TresExchData(Tres tres, IWs ws) {
        m_tres = tres;
        m_ws = ws;
        m_oscCalculators = new TresOscCalculator[tres.m_phases];
        for (int i = 0; i < tres.m_phases; i++) {
            m_oscCalculators[i] = new TresOscCalculator(this, i);
        }
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
