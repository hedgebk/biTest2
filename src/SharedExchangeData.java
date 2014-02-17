public class SharedExchangeData {
    final Exchange m_exchange;
    private long m_lastProcessedTradesTime;
    TopData m_lastTop;
    public final Utils.AverageCounter m_averageCounter = new Utils.AverageCounter(Fetcher.MOVING_AVERAGE);

    public SharedExchangeData(Exchange exchange) {
        m_exchange = exchange;
    }

    public TradesData filterOnlyNewTrades(TradesData trades) {
        TradesData newTrades = trades.newTrades(m_lastProcessedTradesTime);
        for (TradesData.TradeData trade : newTrades.m_trades) {
            long timestamp = trade.m_timestamp;
            if (timestamp > m_lastProcessedTradesTime) {
                m_lastProcessedTradesTime = timestamp;
            }
        }
        return newTrades;
    }

    public TopData fetchTopOnce() throws Exception {
        TopData top = Fetcher.fetchTopOnce(m_exchange);
        if( top != null ) { // we got fresh top data
            m_lastTop = top; // update top
            m_averageCounter.add(System.currentTimeMillis(), m_lastTop.getMid());
        } else {
            if(m_lastTop != null) {
                m_lastTop.setObsolete();
            }
        }
        return m_lastTop;
    }
} // SharedExchangeData
