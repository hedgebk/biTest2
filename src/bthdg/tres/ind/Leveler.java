package bthdg.tres.ind;

import bthdg.tres.alg.TresAlgo;

public class Leveler {
    private Boolean m_direction;
    private double m_upper = 1;
    private double m_upperUp = 1;
    private double m_lower = -1;
    private double m_lowerLow = -1;
    private double m_lastScaled;

    public double update(double value, double top, double bottom) {
        double spread = top - bottom;
        double scaled = (value - bottom) * 2 / spread - 1;
        if (value > top) {
            m_direction = Boolean.TRUE;
            m_lower = -1;
            if (scaled > m_upper) {
                m_upper = scaled;
                m_upperUp = scaled;
            }
        } else if (value < bottom) {
            m_direction = Boolean.FALSE;
            m_upper = 1;
            if (scaled < m_lower) {
                m_lower = scaled;
                m_lowerLow = scaled;
            }
        } else {
            if (m_direction != null) {
                if (m_direction && (scaled < m_lastScaled)) {
                    double rate = (value - bottom) / spread; // [1 -> 0]
                    m_upper = 1 + (m_upperUp - 1) * rate;
                } else if (!m_direction && (scaled > m_lastScaled)) {
                    double rate = (top - value) / spread; // [1 -> 0]
                    m_lower = -1 - (-1 - m_lowerLow) * rate;
                }
            }
        }
        m_lastScaled = scaled;
        return (m_direction == null)
                    ? TresAlgo.valueToBounds(value, top, bottom)
                    : TresAlgo.valueToBounds(scaled, m_upper, m_lower);
    }
}
