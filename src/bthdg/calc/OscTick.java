package bthdg.calc;

import bthdg.Log;
import bthdg.util.Utils;

public class OscTick implements Comparable<OscTick>{
    public final long m_startTime;
    public final double m_val1;
    public final double m_val2;

    private static void log(String s) { Log.log(s); }

    public OscTick(long startTime, double val1, double val2) {
        m_startTime = startTime;
        if (Double.isNaN(val1)) {
            log("OscTick.val1=" + val1);
        }
        m_val1 = val1;
        if(Double.isNaN(val2)) {
            log("OscTick.val2=" + val2);
        }
        m_val2 = val2;
    }

    public double getMid() { return (m_val1 + m_val2) / 2; }

    @Override public int compareTo(OscTick other) {
        return Utils.compare(m_startTime, other.m_startTime);
    }

    @Override public String toString() {
        return "OscTick{startTime=" + m_startTime + ", val1=" + m_val1 + ", val2=" + m_val2 + "}";
    }
}
