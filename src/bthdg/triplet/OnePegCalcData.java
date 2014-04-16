package bthdg.triplet;

import bthdg.*;
import bthdg.exch.TopData;
import bthdg.exch.TopsData;

import java.util.Map;

public class OnePegCalcData {
    public int m_indx;
    public double m_max; // peg -> mkt -> mkt
    public double m_max10; // peg -> mkt-10 -> mkt-10
    public TriangleRotationCalcData m_parent;
    public double m_price1;
    public double m_price2;
    public double m_price2minus; // mkt-10
    public double m_price3;
    public double m_price3minus; // mkt-10
    public PairDirection m_pair1;
    public PairDirection m_pair2;
    public PairDirection m_pair3;
    public double m_need;

    public OnePegCalcData(int indx, double max, double max10,
                          PairDirection pair1, double price1,
                          PairDirection pair2, double price2, double price2minus,
                          PairDirection pair3, double price3, double price3minus) {
        m_indx = indx;
        m_max = max;
        m_max10 = max10;
        m_price1 = price1;
        m_price2 = price2;
        m_price2minus = price2minus;
        m_price3 = price3;
        m_price3minus = price3minus;
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

    public String str2() {
        if (m_max10 > Triplet.s_level) {
            return m_indx + ":" + Triplet.formatAndPad(m_max10);
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

    public double calcPegPrice(TopsData tops) {
        TopData top = tops.get(m_pair1.m_pair);
        double price = Triangle.pegPrice(top, m_pair1);
        return price;
    }

    public double calcMktPrice(TopsData tops, int indx) {
        PairDirection pd = (indx == 0) ? m_pair2 : m_pair3;
        return calcMktPrice(tops, pd);
    }

    private double calcMktPrice(TopsData tops, PairDirection pd) {
        TopData top = tops.get(pd.m_pair);
        double price = Triangle.mktPrice(top, pd);
        return price;
    }

    public double calcMktPrice(TopsData tops, int indx, double offset) {
        PairDirection pd = (indx == 0) ? m_pair2 : m_pair3;
        return calcMktPrice(tops, pd, offset);
    }

    private double calcMktPrice(TopsData tops, PairDirection pd, double offset) {
        TopData top = tops.get(pd.m_pair);
        double price = Triangle.mktPrice(top, pd, offset);
        return price;
    }

    public double pegRatio1(TopsData tops, AccountData account) {
        double pegPrice = calcPegPrice(tops);
        OrderSide side = m_pair1.getSide();
        return (side.isBuy() ? 1 / pegPrice : pegPrice) * (1 - account.m_fee); // deduct commissions
    }

    public double mktRatio2(TopsData tops, AccountData account) {
        return mktRatio(tops, account, m_pair2);
    }

    public double mktRatio2(TopsData tops, AccountData account, double offset) {
        return mktRatio(tops, account, m_pair2, offset);
    }

    public double mktRatio3(TopsData tops, AccountData account) {
        return mktRatio(tops, account, m_pair3);
    }

    public double mktRatio3(TopsData tops, AccountData account, double offset) {
        return mktRatio(tops, account, m_pair3, offset);
    }

    private double mktRatio(TopsData tops, AccountData account, PairDirection pd) {
        double mktPrice = calcMktPrice(tops, pd);
        OrderSide side = pd.getSide();
        return (side.isBuy() ? 1 / mktPrice : mktPrice) * (1 - account.m_fee); // deduct commissions
    }

    private double mktRatio(TopsData tops, AccountData account, PairDirection pd, double offset) {
        double mktPrice = calcMktPrice(tops, pd, offset);
        OrderSide side = pd.getSide();
        return (side.isBuy() ? 1 / mktPrice : mktPrice) * (1 - account.m_fee); // deduct commissions
    }
}
