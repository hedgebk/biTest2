package bthdg.tres;

import bthdg.Log;

import java.util.LinkedList;

public class TresMaCalculator extends BaseTresMaCalculator {
    protected final PhaseData m_phaseData;
    final LinkedList<MaCrossData> m_maCrossDatas = new LinkedList<MaCrossData>();

    private static void log(String s) { Log.log(s); }

    public TresMaCalculator(PhaseData phaseData, int phaseIndex) {
        super(phaseData.m_exchData, phaseIndex);
        m_phaseData = phaseData;
    }

    @Override protected void addNewMaCrossData(MaCrossData maCrossData) {
        synchronized (m_maCrossDatas) {
            m_maCrossDatas.add(maCrossData);
        }
        super.addNewMaCrossData(maCrossData);
    }

    @Override protected Boolean calcOscDirection(boolean useLastFineTick, long time) {
        return m_phaseData.calcOscDirection(useLastFineTick, time);
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
}
