package bthdg.duplet;

import bthdg.exch.TopData;

class TopDatas {
    public final TopData m_top1;
    public final TopData m_top2;

    public TopDatas(TopData top1, TopData top2) {
        m_top1 = top1;
        m_top2 = top2;
    }

    public boolean bothFresh() {
        return top1fresh() && top2fresh();
    }

    boolean top2fresh() {
        return TopData.isLive(m_top2);
    }

    boolean top1fresh() {
        return TopData.isLive(m_top1);
    }

    public TopData.TopDataEx calculateDiff() {  // top1 - top2
        return TopData.calcDiff(m_top1, m_top2);
    }
} // TopDatas
