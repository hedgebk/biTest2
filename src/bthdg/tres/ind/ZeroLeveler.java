package bthdg.tres.ind;

import bthdg.tres.alg.TresAlgo;

public class ZeroLeveler {
    private final double m_startLevel;
    private Boolean m_direction;
    private Double m_level;
    private boolean m_active;

    public ZeroLeveler(double startLevel) {
        m_startLevel = startLevel;
    }

    public double update(double value) {
        double ret;
        if (m_direction == null) { // not yet started
            if (value > m_startLevel) {
                m_direction = Boolean.TRUE;
                m_level = value;
            } else if (value < -m_startLevel) {
                m_direction = Boolean.FALSE;
                m_level = value;
            }
            ret = 0;
        } else {
            if (m_direction) { // up
                if (value > m_level) {
                    m_level = value;
                }
                ret = TresAlgo.valueToBounds(value, m_level, 0);
                if (!m_active && (ret < 0)) {
                    m_active = true; // activate on first zero cross
                }
                if (value < -m_startLevel) {
                    m_direction = Boolean.FALSE;
                    m_level = value;
                }
            } else { // down
                if (value < m_level) {
                    m_level = value;
                }
                ret = TresAlgo.valueToBounds(value, 0, m_level);
                if (!m_active && (ret > 0)) {
                    m_active = true; // activate on first zero cross
                }
                if (value > m_startLevel) {
                    m_direction = Boolean.TRUE;
                    m_level = value;
                }
            }
        }
        return m_active ? ret : 0;
    }
}
