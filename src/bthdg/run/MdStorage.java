package bthdg.run;

import bthdg.exch.DeepData;
import bthdg.exch.Exchange;
import bthdg.exch.Pair;
import bthdg.exch.TopData;

import java.util.HashMap;
import java.util.Map;

public class MdStorage {
    private Map<Exchange, Map<Pair, MktDataHolder>> m_map = new HashMap<Exchange, Map<Pair, MktDataHolder>>();

    public void put(Exchange exchange, Pair pair, DeepData deeps, TopData topData) {
        Map<Pair, MktDataHolder> exchMap = m_map.get(exchange);
        if (exchMap == null) {
            exchMap = new HashMap<Pair, MktDataHolder>();
            m_map.put(exchange, exchMap);
        }
        MktDataHolder topDataHolder = exchMap.get(pair);
        if (topDataHolder == null) {
            topDataHolder = new MktDataHolder();
            exchMap.put(pair, topDataHolder);
        }
        MktDataPoint mdPoint = new MktDataPoint(topData, deeps);
        topDataHolder.set(mdPoint);
    }

    public MktDataHolder get(Exchange exchange, Pair pair) {
        Map<Pair, MktDataHolder> exchMap = m_map.get(exchange);
        if (exchMap != null) {
            return exchMap.get(pair);
        }
        return  null;
    }
}
