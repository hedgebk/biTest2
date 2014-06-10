package bthdg.run;

import bthdg.exch.TopData;

public class TopDataHolder {
    public TopData m_topData;
    public TopData m_prevTopData;

    public void set(TopData topData) {
        m_prevTopData = m_topData;
        m_topData = topData;
    }
}
