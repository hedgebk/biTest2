package bthdg.tres;

import bthdg.Log;
import bthdg.calc.MaCalculator;

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
                phaseData.m_exchData.m_tres.getBarOffset(phaseIndex),
                MaType.CLOSE, phaseData.m_exchData.m_tres.m_ma);
        m_phaseData = phaseData;
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

    @Override protected void finishCurrentBar(long barStart, long barEnd, long time, double price) {
        super.finishCurrentBar(barStart, barEnd, time, price);

        double currentMa = m_tick.m_ma;
        boolean currentPriceHigher = (price > currentMa);

        if ((m_lastPriceHigher != null) && (m_lastPriceHigher != currentPriceHigher)) {
            onMaCross(time, price, currentPriceHigher);
        }
        m_lastPriceHigher = currentPriceHigher;
    }

    @Override protected void endMaBar(long barEnd, double ma, long time, double price) {
        if ((m_lastMaCrossUp != null) && (m_lastOscUp != null) && (m_lastMaCrossUp != m_lastOscUp)) {
            Boolean oscUp = m_phaseData.calcOscDirection(false, time);
            if (oscUp != null) { // if direction defined
                if (m_lastMaCrossUp == oscUp) {
                    addNewMaCrossData(time, price, oscUp);
                }
            }
        }
    }

    private void onMaCross(long time, double price, boolean maCrossUp) {
        m_tick.m_maCrossed = true;
        Boolean oscUp = m_phaseData.calcOscDirection(true, time);
        if (oscUp != null) { // if direction defined
            addNewMaCrossData(time, price, oscUp);
        }
        m_lastMaCrossUp = maCrossUp;
    }

    private void addNewMaCrossData(long time, double price, Boolean oscUp) {
        MaCrossData maCrossData = new MaCrossData(time, oscUp, price);
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
        for (MaCrossData maCrossData : m_maCrossDatas) {
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
