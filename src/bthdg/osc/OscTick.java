package bthdg.osc;

public class OscTick implements Comparable<OscTick>{
    public final long m_startTime;
    public final double m_val1;
    public final double m_val2;

    public OscTick(long startTime, double val1, double val2) {
        m_startTime = startTime;
        m_val1 = val1;
        m_val2 = val2;
    }

    @Override public int compareTo(OscTick other) {
        return Long.compare(m_startTime, other.m_startTime);
    }
}
