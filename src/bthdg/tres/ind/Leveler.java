package bthdg.tres.ind;

import bthdg.tres.alg.TresAlgo;

public class Leveler {
    private Boolean m_direction;
    private double m_diff;
    private double m_diffMax;

    public double update(double value, double top, double bottom) {
        double spread = top - bottom;
        if (value > top) {
            double diff = value - bottom;
            if ((m_direction == null) || !m_direction) { // up started
                m_direction = Boolean.TRUE;
                m_diff = diff;
                m_diffMax = diff;
            } else {
                if (diff > m_diff) {
                    m_diff = diff;
                    m_diffMax = diff;
                }
            }
        } else if (value < bottom) {
            double diff = top - value;
            if ((m_direction == null) || m_direction) { // down started
                m_direction = Boolean.FALSE;
                m_diff = diff;
                m_diffMax = diff;
            } else {
                if (diff > m_diff) {
                    m_diff = diff;
                    m_diffMax = diff;
                }
            }
        } else {
            if (m_direction != null) {
                if (m_direction) {
                    if (value < top) {
                        double rate = (value - bottom) / spread;
                        double diff = (m_diffMax - spread) * rate + spread;
                        if (diff < m_diff) {
                            m_diff = diff;
                        }
                    }
                } else {
                    if (value > bottom) {
                        double rate = (top - value) / spread;
                        double diff = (m_diffMax - spread) * rate + spread;
                        if (diff < m_diff) {
                            m_diff = diff;
                        }
                    }
                }
                if(spread > m_diff) {
                    m_diff = spread;
                }
            }
        }
        return (m_direction == null)
                    ? TresAlgo.valueToBounds(value, top, bottom)
                    : m_direction
                        ? TresAlgo.valueToBounds(value - bottom, m_diff, 0)
                        : TresAlgo.valueToBounds(value - top, 0, -m_diff);
    }
}
