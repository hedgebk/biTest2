public class SharedExchangeData {
    final Exchange m_exchange;
    private long m_lastProcessedTradesTime;
    TopData m_lastTop;
    public final Utils.AverageCounter m_averageCounter = new Utils.AverageCounter(Fetcher.MOVING_AVERAGE);
    // to calc average diff between bid and ask on exchange
    public final Utils.DoubleAverageCalculator<Double> m_bidAskDiffCalculator = new Utils.DoubleAverageCalculator<Double>() {
        @Override public double getDoubleValue(Double tick) { return tick; } // ASK > BID
    };
    //bitstamp avgBidAskDiff1=2.6743, btc-e avgBidAskDiff2=1.3724
    //bitstamp avgBidAskDiff1=2.1741, btc-e avgBidAskDiff2=1.2498
    //bitstamp avgBidAskDiff1=1.9107, btc-e avgBidAskDiff2=1.3497

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
            double bidAskDiff = (m_lastTop.m_ask - m_lastTop.m_bid);
            m_bidAskDiffCalculator.addValue(bidAskDiff);
        } else {
            if(m_lastTop != null) {
                m_lastTop.setObsolete();
            }
        }
        return m_lastTop;
    }

    public double calcAmountToOpen() {
        // could be different depending on exchange since the price is different
        return 1.0 /*BTC*/ ;  // todo: get this based on both exch account info
    }
} // SharedExchangeData
