package bthdg.run;

import bthdg.Exchange;
import bthdg.exch.Pair;
import bthdg.exch.TopData;

import java.util.HashMap;
import java.util.Map;

public class MdStorage {
    private Map<Exchange, Map<Pair, TopDataHolder>> m_map = new HashMap<Exchange, Map<Pair, TopDataHolder>>();

    public void put(Exchange exchange, Pair pair, TopData topData) {
        Map<Pair, TopDataHolder> exchMap = m_map.get(exchange);
        if (exchMap == null) {
            exchMap = new HashMap<Pair, TopDataHolder>();
            m_map.put(exchange, exchMap);
        }
        TopDataHolder topDataHolder = exchMap.get(pair);
        if (topDataHolder == null) {
            topDataHolder = new TopDataHolder();
            exchMap.put(pair, topDataHolder);
        }
        topDataHolder.set(topData);
    }

    public TopDataHolder get(Exchange exchange, Pair pair) {
        Map<Pair, TopDataHolder> exchMap = m_map.get(exchange);
        if (exchMap != null) {
            return exchMap.get(pair);
        }
        return  null;
    }
}
