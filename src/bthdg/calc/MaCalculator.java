package bthdg.calc;

import bthdg.exch.TradeData;

import java.util.ArrayList;
import java.util.List;

public class MaCalculator extends BarCalculator {
    private final MaType m_type;
    private final int m_maSize;
    private BaseMaBarData m_barData;
    private List<BaseMaBarData> m_barDatas = new ArrayList<BaseMaBarData>();

    protected void startMaBar(long barEnd) {}
    protected void updateMaBar(double ma) {}
    protected void endMaBar(long barEnd, double ma, TradeData tdata) {}

    public MaCalculator(long barSizeMillis, long barsMillisOffset, MaType type, int maSize) {
        super(barSizeMillis, barsMillisOffset);
        m_type = type;
        m_maSize = maSize;
    }

    @Override protected void finishCurrentBar(long barStart, long barEnd, TradeData tdata) {
        double ma = calcMa();
        endMaBar(barEnd, ma, tdata);
    }

    private double calcMa() {
        int size = m_barDatas.size();
        if (size > 0) {
            double sum = 0;
            for (BaseMaBarData barData : m_barDatas) {
                double value = barData.value();
                sum += value;
            }
            return sum / size;
        }
        return 0;
    }

    @Override protected boolean updateCurrentBar(TradeData tdata) {
        boolean updated = m_type.updateBarData(m_barData, tdata);
        if (updated) {
            double ma = calcMa();
            updateMaBar(ma);
        }
        return updated;
    }

    @Override protected void startNewBar(long barStart, long barEnd) {
        m_barData = m_type.newBarData();
        m_barDatas.add(m_barData);
        if (m_barDatas.size() > m_maSize) {
            m_barDatas.remove(0);
        }
        startMaBar(barEnd);
    }

    public enum MaType {
        CLOSE {
            @Override public BaseMaBarData newBarData() {
                return new BaseMaBarData();
            }

            @Override public boolean updateBarData(BaseMaBarData barData, TradeData tdata) {
                boolean updated = barData.m_value != tdata.m_price;
                barData.m_value = tdata.m_price;
                return updated;
            }
        };
        public BaseMaBarData newBarData() { return null; }
        public boolean updateBarData(BaseMaBarData barData, TradeData tdata) { return false; }
    }

    public static class BaseMaBarData {
        protected double m_value;

        public double value() {
            return m_value;
        }
    }
}
