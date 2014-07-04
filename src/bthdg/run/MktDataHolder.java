package bthdg.run;

import bthdg.exch.TopData;

public class MktDataHolder {
    public MktDataPoint m_mdPoint;
    public MktDataPoint m_prevMdPoint;

    public void set(MktDataPoint mdPoint) {
        m_prevMdPoint = m_mdPoint;
        m_mdPoint = mdPoint;
    }

    public TopData topData() { return m_mdPoint.m_topData; }
}
