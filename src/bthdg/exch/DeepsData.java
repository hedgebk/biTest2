package bthdg.exch;

import bthdg.Pair;
import bthdg.PairDirection;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DeepsData extends HashMap<Pair,DeepData> {
    private TopsDataAdapter m_topsAdapter;

    public TopsDataAdapter getTopsDataAdapter() {
        if( m_topsAdapter == null ) {
            m_topsAdapter = new TopsDataAdapter();
        }
        return m_topsAdapter;
    }

    public double getMktAmount(PairDirection pd) {
        DeepData.Deep deep = getMktDeep(pd);
        return deep.m_size;
    }

    public DeepData.Deep getMktDeep(PairDirection pd) {
        Pair pair = pd.m_pair;
        DeepData deepData = get(pair);
        return pd.m_forward ? deepData.getAsk() : deepData.getBid();
    }

    public class TopsDataAdapter extends TopsData {
        private boolean m_synced;

        @Override public TopData get(Pair pair) {
            TopData topData = super.get(pair);
            if(topData == null) {
                DeepData deepData = DeepsData.this.get(pair);
                topData = deepData.getTopDataAdapter();
                put(pair, topData);
            }
            return topData;
        }

        @Override public Set<Map.Entry<Pair, TopData>> entrySet() {
            if(!m_synced) {
                for( Pair pair: DeepsData.this.keySet() ) {
                    get(pair);
                }
                m_synced = true;
            }
            return super.entrySet();
        }

        public DeepsData getDeeps() {
            return DeepsData.this;
        }
    }
}
