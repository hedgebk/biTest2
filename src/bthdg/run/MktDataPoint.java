package bthdg.run;

import bthdg.exch.DeepData;
import bthdg.exch.TopData;

public class MktDataPoint {
    public final TopData m_topData;
    public final DeepData m_deeps;

    public MktDataPoint(TopData topData, DeepData deeps) {
        m_topData = topData;
        m_deeps = deeps;
    }
}
