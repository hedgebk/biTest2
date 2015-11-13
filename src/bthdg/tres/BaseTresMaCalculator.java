package bthdg.tres;

import bthdg.calc.MaCalculator;

import java.util.LinkedList;

public abstract class BaseTresMaCalculator extends MaCalculator {
    private final TresExchData m_exchData;
    private MaTick m_tick;
    private Boolean m_lastPriceHigher;
    private Boolean m_lastMaCrossUp;
    private Boolean m_lastOscUp;
    final LinkedList<MaTick> m_maTicks = new LinkedList<MaTick>();
    private MaCrossData m_lastMaCrossData;

    public MaCrossData lastMaCrossData() { return m_lastMaCrossData; }

    protected abstract Boolean calcOscDirection(boolean useLastFineTick, long time);

    @Override protected void updateMaBar(double ma) {
        m_tick.m_ma = ma;
    }

    public BaseTresMaCalculator(TresExchData exchData, int phaseIndex) {
        super(exchData.m_tres.m_barSizeMillis,
              exchData.m_tres.getBarOffset(phaseIndex),
              MaType.CLOSE, exchData.m_tres.m_ma);
        m_exchData = exchData;
    }

    @Override protected void startMaBar(long barEnd) {
        m_tick = new MaTick(barEnd);
        if (m_exchData.m_tres.m_collectPoints) {
            synchronized (m_maTicks) {
                m_maTicks.add(m_tick);
            }
        }
    }

    @Override protected void finishCurrentBar(long time, double price) {
        super.finishCurrentBar(time, price);
        if (m_tick != null) {
            double currentMa = m_tick.m_ma;
            boolean currentPriceHigher = (price > currentMa);

            if ((m_lastPriceHigher != null) && (m_lastPriceHigher != currentPriceHigher)) {
                onMaCross(time, price, currentPriceHigher);
            }
            m_lastPriceHigher = currentPriceHigher;
        }
    }

    private void onMaCross(long time, double price, boolean maCrossUp) {
        m_tick.m_maCrossed = true;
        Boolean oscUp = calcOscDirection(true, time);
        if (oscUp != null) { // if direction defined
            addNewMaCrossData(time, price, oscUp);
        }
        m_lastMaCrossUp = maCrossUp;
    }

    protected void addNewMaCrossData(long time, double price, Boolean oscUp) {
        MaCrossData maCrossData = new MaCrossData(time, oscUp, price);
        addNewMaCrossData(maCrossData);
    }

    protected void addNewMaCrossData(MaCrossData maCrossData) {
        m_lastMaCrossData = maCrossData;
        m_lastOscUp = maCrossData.m_oscUp;

        if (!m_exchData.m_tres.m_logProcessing) {
            m_exchData.m_executor.postRecheckDirection();
        }
    }

    @Override protected void endMaBar(long barEnd, double ma, long time, double price) {
        if ((m_lastMaCrossUp != null) && (m_lastOscUp != null) && (m_lastMaCrossUp != m_lastOscUp)) {
            Boolean oscUp = calcOscDirection(false, time);
            if (oscUp != null) { // if direction defined
                if (m_lastMaCrossUp == oscUp) {
                    addNewMaCrossData(time, price, oscUp);
                }
            }
        }
    }

    //==========================================================================================
    public static class MaCrossData {
        public final long m_timestamp;
        final boolean m_oscUp;
        final double m_price;

        public MaCrossData(long timestamp, boolean oscUp, double price) {
            m_timestamp = timestamp;
            m_oscUp = oscUp;
            m_price = price;
        }
    }

    //==========================================================================================
    public static class MaTick {
        protected final long m_barEnd;
        protected double m_ma;
        public boolean m_maCrossed;

        public MaTick(long barEnd) {
            m_barEnd = barEnd;
        }
    }
}
