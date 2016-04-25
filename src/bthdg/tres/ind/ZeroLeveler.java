package bthdg.tres.ind;

import bthdg.tres.alg.TresAlgo;

public class ZeroLeveler {
    private final double m_startLevel;
    private double m_maxLevel;
    private double m_minLevel;

    public ZeroLeveler(double startLevel) {
        m_startLevel = startLevel;
        m_maxLevel = startLevel;
        m_minLevel = -startLevel;
    }

    public double update(double value) {
        if (value > m_maxLevel) {
            m_maxLevel = value;
        } else if (value < m_minLevel) {
            m_minLevel = value;
        }

        double ret;
        if (value > 0) {
            m_minLevel = -m_startLevel;
            ret = TresAlgo.valueToBounds(value, m_maxLevel, -m_maxLevel);
        } else if (value < 0) {
            m_maxLevel = m_startLevel;
            ret = TresAlgo.valueToBounds(value, -m_minLevel, m_minLevel);
        } else {
            ret = 0;
        }
        return ret;
    }
}
