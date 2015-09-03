package bthdg.tres;

import bthdg.Log;
import bthdg.calc.MaCalculator;
import bthdg.exch.TradeData;

import java.util.Iterator;
import java.util.LinkedList;

public class TresMaCalculator extends MaCalculator {
    protected final PhaseData m_phaseData;
    private MaTick m_tick;
    final LinkedList<MaTick> m_maTicks = new LinkedList<MaTick>();

    final LinkedList<MaCrossData> m_maCrossDatas = new LinkedList<MaCrossData>();
    private Boolean m_lastPriceHigher;
    private Boolean m_lastMaCrossUp;
    private Boolean m_lastOscUp;

    private static void log(String s) { Log.log(s); }

    public TresMaCalculator(PhaseData phaseData, int phaseIndex) {
        //super(phaseData.m_exchData, phaseIndex, MaType.CLOSE, phaseData.m_exchData.m_tres.m_ma);
        super(phaseData.m_exchData.m_tres.m_barSizeMillis,
                getOffset(phaseIndex, phaseData.m_exchData.m_tres),
                MaType.CLOSE, phaseData.m_exchData.m_tres.m_ma);
        m_phaseData = phaseData;
    }

    private static long getOffset(int index, Tres tres) {
        return tres.m_barSizeMillis * (index % tres.m_phases) / tres.m_phases;
    }

    @Override protected void startMaBar(long barEnd) {
        m_tick = new MaTick(barEnd);
        synchronized (m_maTicks) {
            m_maTicks.add(m_tick);
        }
    }

    @Override protected void updateMaBar(double ma) {
        m_tick.m_ma = ma;
    }

    @Override public boolean update(TradeData tdata) {
        boolean ret = super.update(tdata);
        if (ret) {
            double currentPrice = tdata.m_price;
            double currentMa = m_tick.m_ma;
            boolean currentPriceHigher = (currentPrice > currentMa);

            if ((m_lastPriceHigher != null) && (m_lastPriceHigher != currentPriceHigher)) {
                onMaCross(tdata, currentPriceHigher);
            }
            m_lastPriceHigher = currentPriceHigher;
        }
        return ret;
    }

    @Override protected void endMaBar(long barEnd, double ma, TradeData tdata) {
        if ((m_lastMaCrossUp != null) && (m_lastOscUp != null) && (m_lastMaCrossUp != m_lastOscUp)) {
            long timestamp = tdata.m_timestamp;
            Boolean oscUp = m_phaseData.calcOscDirection(false, timestamp);
            if (oscUp != null) { // if direction defined
                if (m_lastMaCrossUp == oscUp) {
                    addNewMaCrossData(tdata, oscUp);
                }
            }
        }
    }

    private void onMaCross(TradeData tdata, boolean maCrossUp) {
        m_tick.m_maCrossed = true;
        long timestamp = tdata.m_timestamp;
        Boolean oscUp = m_phaseData.calcOscDirection(true, timestamp);
        if (oscUp != null) { // if direction defined
            addNewMaCrossData(tdata, oscUp);
        }
        m_lastMaCrossUp = maCrossUp;
    }

    private void addNewMaCrossData(TradeData tdata, Boolean oscUp) {
        long timestamp = tdata.m_timestamp;
        double price = tdata.m_price;
        MaCrossData maCrossData = new MaCrossData(timestamp, oscUp, price);
        synchronized (m_maCrossDatas) {
            m_maCrossDatas.add(maCrossData);
        }
        m_lastOscUp = oscUp;

        TresExchData exchData = m_phaseData.m_exchData;
        if(!exchData.m_tres.m_logProcessing) {
            exchData.m_executor.postRecheckDirection();
        }
    }

    public double calcToTal() {
        Boolean lastOscUp = null;
        Double lastPrice = null;
        double totalPriceRatio = 1;
        LinkedList<TresMaCalculator.MaCrossData> maCrossDatas = new LinkedList<TresMaCalculator.MaCrossData>();
        synchronized (m_maCrossDatas) { // avoid ConcurrentModificationException - use local copy
            maCrossDatas.addAll(m_maCrossDatas);
        }
        for (Iterator<MaCrossData> iterator = maCrossDatas.iterator(); iterator.hasNext(); ) {
            TresMaCalculator.MaCrossData maCrossData = iterator.next();
            boolean oscUp = maCrossData.m_oscUp;
            double price = maCrossData.m_price;
            if (lastOscUp != null) {
                if (lastOscUp != oscUp) {
                    if (lastPrice != null) {
                        double priceRatio = price / lastPrice;
                        if (!lastOscUp) {
                            priceRatio = 1 / priceRatio;
                        }
                        totalPriceRatio *= priceRatio;
                    }
                    lastOscUp = oscUp;
                    lastPrice = price;
                }
            } else { // first
                lastOscUp = oscUp;
                lastPrice = price;
            }
        }
        return totalPriceRatio;
    }

    public static class MaCrossData {
        final long m_timestamp;
        final boolean m_oscUp;
        final double m_price;

        public MaCrossData(long timestamp, boolean oscUp, double price) {
            m_timestamp = timestamp;
            m_oscUp = oscUp;
            m_price = price;
        }
    }

    public static class MaTick {
        protected final long m_barEnd;
        protected double m_ma;
        public boolean m_maCrossed;

        public MaTick(long barEnd) {
            m_barEnd = barEnd;
        }
    }
}
