package bthdg.tres;

import bthdg.Log;
import bthdg.osc.OscCalculator;
import bthdg.osc.OscTick;

import java.util.LinkedList;

public class TresOscCalculator extends OscCalculator {
    private TresExchData m_exchData;
    private final int m_phaseIndex;
    public int m_barNum;
    OscTick m_lastFineTick;
    OscTick m_lastBar;
    LinkedList<OscTick> m_oscBars = new LinkedList<OscTick>();
    private boolean m_updated;

    private static void log(String s) { Log.log(s); }

    public TresOscCalculator(TresExchData exchData, int phaseIndex) {
        super(exchData.m_tres.m_len1, exchData.m_tres.m_len2, exchData.m_tres.m_k, exchData.m_tres.m_d, exchData.m_tres.m_barSizemMillis, getOffset(phaseIndex, exchData.m_tres));
        m_exchData = exchData;
        m_phaseIndex = phaseIndex;
    }

    private static long getOffset(int index, Tres tres) {
        return tres.m_barSizemMillis * (index % tres.m_phases) / tres.m_phases;
    }

    @Override protected void update(long stamp, boolean finishBar) {
        m_updated = false;
        super.update(stamp, finishBar);
        if (finishBar) {
            if (m_barNum++ < m_exchData.m_tres.m_preheatBarsNum) {
                log("update[" + m_exchData.m_ws.exchange() + "][" + m_phaseIndex + "]: PREHEATING step=" + m_barNum + " from " + m_exchData.m_tres.m_preheatBarsNum);
                m_updated = true;
            }
        }
        if( m_updated ) {
            m_exchData.setUpdated();
            m_updated = false;
        }
    }

    @Override public void fine(long stamp, double stoch1, double stoch2) {
        log("fine[" + m_exchData.m_ws.exchange() + "][" + m_phaseIndex + "]: stamp=" + stamp + "; stoch1=" + stoch1 + "; stoch2=" + stoch2);
        m_lastFineTick = new OscTick(stamp, stoch1, stoch2);
        m_updated = true;
    }

    @Override public void bar(long barStart, double stoch1, double stoch2) {
        log("bar[" + m_exchData.m_ws.exchange() + "][" + m_phaseIndex + "]: barStart=" + barStart + "; stoch1=" + stoch1 + "; stoch2=" + stoch2);
        OscTick osc = new OscTick(barStart, stoch1, stoch2);
        m_lastBar = osc;
        m_oscBars.add(osc); // add to the end
        m_updated = true;
    }

    public void getState(StringBuilder sb) {
        sb.append(" [" + m_phaseIndex + "] ");
        if (m_barNum++ < m_exchData.m_tres.m_preheatBarsNum) {
            sb.append("PREHEATING step=" + m_barNum + " from " + m_exchData.m_tres.m_preheatBarsNum);
        } else {
            dumpTick(sb, "FINE", m_lastFineTick);
            dumpTick(sb, "BAR", m_lastBar);
        }
    }

    private void dumpTick(StringBuilder sb, String prefix, OscTick tick) {
        sb.append(" " + prefix + " ");
        if (tick == null) {
            sb.append("null");
        } else {
            sb.append(String.format("%.4f %.4f", tick.m_val1, tick.m_val1));
        }
    }
}
