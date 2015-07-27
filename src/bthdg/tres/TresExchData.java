package bthdg.tres;

import bthdg.Log;
import bthdg.exch.TradeData;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;

public class TresExchData implements ITradesListener {
    final Tres m_tres;
    final IWs m_ws;
    final PhaseData[] m_phaseDatas;
    double m_lastPrice;
    private boolean m_updated;


    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    public TresExchData(Tres tres, IWs ws) {
        m_tres = tres;
        m_ws = ws;
        int phasesNum = tres.m_phases;
        m_phaseDatas = new PhaseData[phasesNum];
        for (int i = 0; i < phasesNum; i++) {
            m_phaseDatas[i] = new PhaseData(this, i);
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
        m_lastPrice = tdata.m_price;
        for (PhaseData phaseData : m_phaseDatas) {
            boolean updated = phaseData.update(tdata);
            if (updated) {
                m_updated = true;
            }
        }
        if (m_updated) {
            m_tres.onTrade(tdata);
        }
    }

    public void stop() {
        m_ws.stop();
    }

    public void setUpdated() { m_updated = true; }

    public void getState(StringBuilder sb) {
        sb.append("[" + m_ws.exchange() + "]: last=" + m_lastPrice);
        for (PhaseData phaseData : m_phaseDatas) {
            phaseData.getState(sb);
        }
    }
}
