package bthdg.tres;

import bthdg.Log;
import bthdg.exch.TradeData;
import bthdg.util.Utils;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;

public class TresExchData implements ITradesListener {
    private static final boolean IGNORE_PAST_TICKS = false;

    final Tres m_tres;
    final IWs m_ws;
    final PhaseData[] m_phaseDatas;
    double m_lastPrice;
    private boolean m_updated;
    private long m_lastTimestamp;

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

    long m_tickCount;
    long m_tickIgnoredCount;
    Utils.DoubleDoubleAverageCalculator m_avgTimeDiffCalc = new Utils.DoubleDoubleAverageCalculator();

    @Override public void onTrade(TradeData tdata) {
        m_tickCount++;
        if (!m_tres.m_silentConsole) {
            log("onTrade[" + m_ws.exchange() + "]: " + tdata);
        }
        m_lastPrice = tdata.m_price;
        long timestamp = tdata.m_timestamp;
        long timeDiff = m_lastTimestamp - timestamp;
        if ((!IGNORE_PAST_TICKS && (timeDiff <= 30000)) || (timeDiff <= 0)) { // time runs only forward
            m_lastTimestamp = timestamp;
            m_updated = false;
            for (PhaseData phaseData : m_phaseDatas) {
                boolean updated = phaseData.update(tdata);
                if (updated) {
                    m_updated = true;
                }
            }
            if (m_updated) {
                m_tres.onTrade(tdata);
            }
        } else {
            if(!m_tres.m_silentConsole) {
                m_tickIgnoredCount++;
                m_avgTimeDiffCalc.addValue((double)timeDiff);
                double avgTimeDiff = m_avgTimeDiffCalc.getAverage();
                double ignoreRate = ((double)m_tickIgnoredCount) /m_tickCount;
                log("onTrade[" + m_ws.exchange() + "]: ignored past tick " + tdata + ", lastTimestamp=" + m_lastTimestamp +
                        ", m_tickIgnoredCount=" + m_tickIgnoredCount + "; m_tickCount=" + m_tickCount + ", ignoreRate=" + ignoreRate +
                        ", timeDiff=" + timeDiff + ", avgTimeDiff=" + avgTimeDiff);
            }
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

    public TresExchData cloneClean() {
        return new TresExchData(m_tres, m_ws);
    }
}
