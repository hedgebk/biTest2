package bthdg.calc;

public class SarCalculator extends OHLCCalculator {
    public static Double INIT_AF = 0.02;
    public static Double DELTA_AF = 0.02;
    public static Double MAX_AF = 0.2;

    protected Double m_lastSar;
    private Double m_lastSar_;
    private Double m_tentativeSAR;
    private Double m_ep;
    private Double m_af;
    private Double m_high;
    private Double m_low;
    private Double m_oldHigh;
    private Double m_oldLow;

    public SarCalculator(long barSize, long barsMillisOffset) {
        super(barSize, barsMillisOffset);
    }

    @Override public boolean updateCurrentBar(long time, double price) {
        return super.updateCurrentBar(time, price);
    }

    @Override protected void startNewBar(long barStart, long barEnd) {
        super.startNewBar(barStart, barEnd);
    }

    @Override protected void finishCurrentBar(long time, double price) {
        double low = m_tick.m_low;
        double high = m_tick.m_high;

        if (m_high != null) {
            if (m_lastSar != null) {
                // Formula:
                // If downtrend, tentative SAR is largest of previous two highs and calculated SAR.
                // If uptrend, tentative SAR is smallest of previous two lows and calculated SAR.
                // Based on wiki:
                // If the next period’s SAR value is inside (or beyond) the current period or the previous period’s price range,
                // the SAR must be set to the closest price bound. For example, if in an upward trend, the new SAR value is
                // calculated and if it results to be more than today’s or yesterday’s lowest price, it
                // must be set equal to that lower boundary.
                m_tentativeSAR = (m_lastSar_ < 0)
                        ? Math.max(m_lastSar + m_af * (m_ep - m_lastSar), Math.max(m_high, m_oldHigh))
                        : Math.min(m_lastSar + m_af * (m_ep - m_lastSar), Math.min(m_low, m_oldLow));
            }

            // Initial SAR# is downtrend.
            //  Guess: Presumably determined arbitrarily by looking at chart of data
            // Formula:
            // If downtrend, then if tentative SAR less than daily high, start uptrend (SAR# becomes 1); otherwise,
            // "increment" SAR# (subtract 1).
            // If uptrend, then if tentative SAR greater than daily low, start downtrend (SAR# becomes -1; otherwise, increment SAR#.
            // Based on wiki:
            // If the next period’s SAR value is inside (or beyond) the next period’s price range, a new trend direction is
            // then signaled. The SAR must then switch sides.
            m_lastSar_ = (m_lastSar_ == null)
                    ? -1
                    : (m_lastSar_ < 0)
                        ? (m_tentativeSAR < high) ? 1 : m_lastSar_ - 1
                        : (m_tentativeSAR > low) ? -1 : m_lastSar_ + 1;

            // Formula:
            // If downtrend, initial SAR is previous high.
            // If uptrend, initial SAR is previous low.
            // Based on wiki:
            // Upon a trend switch, the first SAR value for this new trend is set to the last EP recorded on the prior trend
            // Formula:
            // For new downtrend, first SAR is larger of previous EP and current high.
            // For new uptrend, first SAR is smaller of previous EP and current low.
            // Otherwise, set SAR to tentative SAR.
            // Based on wiki:
            // Upon a trend switch, the first SAR value for this new trend is set to the last EP recorded on the prior trend.
            // Note:
            // That rule suggests a simpler formula, namely:
            // =IF(ABS(A12)=1,H11,F12)
            // But on 3/22, for example, that would cause the new downtrend SAR to be less than that day's high.  Normally,
            // that would trigger a reverse trend again; an infinite loop(!).  To avoid that, we altered the rule.
            // This is consistent with the online chart for the "concept example" worksheet.
            // Also DanaD cites an observation on the code used in Trade Station Securities, to wit:
            // At the close of the bar on 22 Mar, the High hit a new high, but the low also went below the stop loss that was
            // calculated the previous trading day (19 Mar).  The Stop Loss takes priority, and therefore it reverses.
            // The new SAR value becomes the High for that day (48.11).
            m_lastSar = (m_lastSar == null)
                    ? (m_lastSar_ < 0) ? m_high : m_low
                    : (m_lastSar_ == -1)
                        ? Math.max(m_ep, high)
                        : (m_lastSar_ == 1)
                            ? Math.min(m_ep, low)
                            : m_tentativeSAR;

            // EP = extreme point.
            //  For downtrend, highest high.
            //  For uptrend, lowest low
            // Formula:
            // If downtrend, initial EP is current low; otherwise, smaller of current low and previous EP.
            // If uptrend, initial EP is current high; otherwise, larger of current high and previous EP.
            // Based on wiki:
            // During each period, if a new maximum (or minimum) is observed, the EP is updated with that value.
            // Upon a trend switch, [...] EP is then reset accordingly to this period’s maximum [or minimum]
            Double  ep = m_ep;
            m_ep = (m_lastSar_ < 0)
                    ? (m_lastSar_ == -1) ? low : Math.min(low, m_ep)
                    : (m_lastSar_ == 1) ? high : Math.max(high, m_ep);

            // AF = acceleration factor (alpha)
            // Formula:
            // =IF(ABS(A11)=1,$B$4,IF(H10=H11,I10,MIN($B$6,I10+$B$5)))
            // If start of new trend, AF is initial AF.
            //  Otherwise, if current and previous EP are equal, AF is previous AF.
            //  If current and previous EP are different, AF is previous AF plus AF increment up to max AF.
            // Based on wiki:
            // Upon a trend switch, [...] the acceleration factor is reset to its initial value of 0.02.
            //  This factor is increased by 0.02 each time a new EP is recorded.
            // To prevent it from getting too large, a maximum value for the acceleration factor is normally set to 0.20.
            //  Generally, it is preferable in stocks trading to set the acceleration factor to 0.01, so that is not too
            //  sensitive to local decreases. For commodity or currency trading, the preferred value is 0.02.
            //  NOTE: We choose 0.02 here to be consistent with the "concept examples"
            m_af = (Math.abs(m_lastSar_) == 1)
                    ? INIT_AF
                    : (ep.equals(m_ep))
                        ? m_af
                        : Math.min(MAX_AF, m_af + DELTA_AF);
        }

        m_oldHigh = m_high;
        m_oldLow = m_low;

        m_high = high;
        m_low = low;
    }


    public double getDirectionAdjusted() { // [-1 ... 1]
        return (m_lastSar_ == null) ? 0 : Math.signum(m_lastSar_);
    }
}
