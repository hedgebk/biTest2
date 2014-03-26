package bthdg.triplet;

import bthdg.*;
import bthdg.exch.TopData;

import java.util.Map;

public class OnePegCalcData {
    public int m_indx;
    public double m_max;
    public TriangleRotationCalcData m_parent;
    public double m_price1;
    public double m_price2;
    protected double m_price3;
    public PairDirection m_pair1;
    public PairDirection m_pair2;
    public PairDirection m_pair3;
    public double m_need;

    public OnePegCalcData(int indx, double max,
                          PairDirection pair1, double price1,
                          PairDirection pair2, double price2,
                          PairDirection pair3, double price3) {
        m_indx = indx;
        m_max = max;
        m_price1 = price1;
        m_price2 = price2;
        m_price3 = price3;
        m_pair1 = pair1;
        m_pair2 = pair2;
        m_pair3 = pair3;
        m_need = (Triplet.s_level / 100) / price2() / price3();
        if (m_pair1.m_forward) {
            m_need = 1 / m_need;
        }
    }

    private double price2() { return m_pair2.m_forward ? 1/m_price2 : m_price2; }
    private double price3() { return m_pair3.m_forward ? 1/m_price3 : m_price3; }

    public String name() {
        StringBuilder sb = new StringBuilder();
        sb.append(m_pair1.getName());
        sb.append(";");
        sb.append(m_pair2.getName());
        sb.append(";");
        sb.append(m_pair3.getName());
        return sb.toString();
    }

    @Override public String toString() {
        return "OnePegCalcData{" +
                "indx=" + m_indx +
                ", max=" + m_max +
                ", need=" + m_need +
                ", price1=" + m_price1 +
                ", pair1=" + m_pair1 +
                ", price2=" + m_price2 +
                ", pair2=" + m_pair2 +
                ", price3=" + m_price3 +
                ", pair3=" + m_pair3 +
                '}';
    }

    public String str() {
        if (m_max > Triplet.s_level) {
            return m_indx + ":" + Triplet.formatAndPad(m_max);
        }
        return "        ";
    }

    @Override public boolean equals(Object obj) {
        if (obj == this) { return true; }
        if (obj instanceof OnePegCalcData) {
            OnePegCalcData other = (OnePegCalcData) obj;
            if (m_indx == other.m_indx) {
                return m_parent.equals(other.m_parent);
            }
        }
        return false;
    }

    public double calcPegPrice(Map<Pair, TopData> tops) {
        Pair pair = m_pair1.m_pair;
        OrderSide side = m_pair1.m_forward ? OrderSide.BUY : OrderSide.SELL;
        return side.pegPrice(tops.get(pair), pair);
    }

    public double mktRatio2(Map<Pair, TopData> tops, AccountData account) {
        return mktRatio(tops, account, m_pair2);
    }

    public double mktRatio3(Map<Pair, TopData> tops, AccountData account) {
        return mktRatio(tops, account, m_pair3);
    }

    private double mktRatio(Map<Pair, TopData> tops, AccountData account, PairDirection pd) {
        OrderSide side = pd.m_forward ? OrderSide.BUY : OrderSide.SELL;
        double mktPrice = side.mktPrice(tops.get(pd.m_pair));
        return (side.isBuy() ? 1 / mktPrice : mktPrice) * (1 - account.m_fee); // deduct commissions
    }
}
