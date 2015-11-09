package bthdg.tres;

import bthdg.Log;
import bthdg.calc.OscCalculator;
import bthdg.calc.OscTick;
import bthdg.osc.TrendWatcher;

import java.util.LinkedList;

public class TresOscCalculator extends OscCalculator {
    public static final double PEAK_TOLERANCE = 0.001;
    public static final int INIT_BARS_BEFORE = 6;
    public static double LOCK_OSC_LEVEL = 0.09;

    protected TresExchData m_exchData;
    private final int m_phaseIndex;
    public int m_barNum;
    OscTick m_lastFineTick;
    public OscTick m_blendedLastFineTick;
    public OscTick m_lastBar;
    public OscTick m_prevBar;
    LinkedList<OscTick> m_oscBars = new LinkedList<OscTick>();
    final LinkedList<OscTick> m_oscPeaks = new LinkedList<OscTick>();
    TrendWatcher<OscTick> m_peakCalculator = new TrendWatcher<OscTick>(PEAK_TOLERANCE) {
        @Override protected double toDouble(OscTick oscTick) { return oscTick.getMid(); }
        @Override protected void onNewPeak(OscTick peak, OscTick last) {
            if (m_exchData.m_tres.m_collectPoints) {
                synchronized (m_oscPeaks) {
                    m_oscPeaks.add(peak);
                }
            }
        }
    };
    private boolean m_updated;

    private static void log(String s) { Log.log(s); }

    public TresOscCalculator(TresExchData exchData, int phaseIndex) {
        super(exchData.m_tres.m_len1, exchData.m_tres.m_len2, exchData.m_tres.m_k, exchData.m_tres.m_d, exchData.m_tres.m_barSizeMillis,
                exchData.m_tres.getBarOffset(phaseIndex));
        m_exchData = exchData;
        m_phaseIndex = phaseIndex;
    }

    @Override protected void updateCurrentBar(long stamp, boolean finishBar) {
        m_updated = false;
        super.updateCurrentBar(stamp, finishBar);
        if (finishBar) {
            Tres tres = m_exchData.m_tres;
            int preheatBarsNum = tres.getPreheatBarsNum();
            if (m_barNum++ < preheatBarsNum) {
                if (!tres.m_silentConsole) {
                    log("update[" + m_exchData.m_ws.exchange() + "][" + m_phaseIndex + "]: PREHEATING step=" + m_barNum + " from " + preheatBarsNum);
                }
                m_updated = true;
            } else {
                m_exchData.setFeeding();
            }
        }
        if( m_updated ) {
            m_exchData.setUpdated();
            m_updated = false;
        }
    }

    @Override public void fine(long stamp, double stoch1, double stoch2) {
        //log("fine[" + m_exchData.m_ws.exchange() + "][" + m_phaseIndex + "]: stamp=" + stamp + "; stoch1=" + stoch1 + "; stoch2=" + stoch2);
        m_lastFineTick = new OscTick(stamp, stoch1, stoch2);
        if (m_lastBar != null) {
            long barSizeMillis = m_exchData.m_tres.m_barSizeMillis;
            long lastBarStartTime = m_lastBar.m_startTime;
            long lastBarEndTime = lastBarStartTime + barSizeMillis;
            long ms = stamp - lastBarEndTime;
            double newRate = ((double) ms) / barSizeMillis;
            if (newRate < 0) {
                newRate = 0;
            } else if (newRate > 1) {
                newRate = 1;
            }
            double oldRate = 1 - newRate;
            double stoch1old = m_lastBar.m_val1;
            double stoch2old = m_lastBar.m_val2;

            double val1 = stoch1old * oldRate + stoch1 * newRate;
            double val2 = stoch2old * oldRate + stoch2 * newRate;
            m_blendedLastFineTick = new OscTick(stamp, val1, val2);
        }
        m_updated = true;
    }

    @Override public void bar(long barStart, double stoch1, double stoch2) {
        // log("bar[" + m_exchData.m_ws.exchange() + "][" + m_phaseIndex + "]: barStart=" + barStart + "; stoch1=" + stoch1 + "; stoch2=" + stoch2);
        OscTick osc = new OscTick(barStart, stoch1, stoch2);
        m_prevBar = m_lastBar;
        m_lastBar = osc;
        if (m_exchData.m_tres.m_collectPoints) {
            m_oscBars.add(osc); // add to the end
        }
        m_updated = true;
        m_peakCalculator.update(osc);
    }

    public void getState(StringBuilder sb) {
        sb.append(" [").append(m_phaseIndex).append("] ");
        int preheatBarsNum = m_exchData.m_tres.getPreheatBarsNum();
        if (m_barNum < preheatBarsNum) {
            sb.append("PREHEATING step=").append(m_barNum).append(" from ").append(preheatBarsNum);
        } else {
            dumpTick(sb, "FINE", m_lastFineTick);
            dumpTick(sb, "BAR", m_lastBar);
        }
    }

    private void dumpTick(StringBuilder sb, String prefix, OscTick tick) {
        sb.append(" ").append(prefix).append(" ");
        if (tick == null) {
            sb.append("null");
        } else {
            sb.append(String.format("%.4f %.4f", tick.m_val1, tick.m_val1));
        }
    }
}
