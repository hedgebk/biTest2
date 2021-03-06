package bthdg.triplet;

import bthdg.exch.*;

public class OnePegCalcData {
    public final int m_indx;
    public final double m_level;
    public final double m_max; // peg -> mkt -> mkt
    public final double m_max10; // peg -> mkt-10 -> mkt-10
    public TriangleRotationCalcData m_parent;
    public final double m_price1;
    public final double m_price2;
    public final double m_price2minus; // mkt-10
    public final double m_price3;
    public final double m_price3minus; // mkt-10
    public final PairDirection m_pair1;
    public final PairDirection m_pair2;
    public final PairDirection m_pair3;
    public final double m_need;
    public final double m_bracketPrice;

    public OnePegCalcData(int indx, double level, double max, double max10,
                          PairDirection pair1, double price1,
                          PairDirection pair2, double price2, double price2minus,
                          PairDirection pair3, double price3, double price3minus) {
        m_indx = indx;
        m_level = level;
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
        double need = (level / 100) / price2() / price3();
        double bracketPrice = ((level + Triplet.BRACKET_LEVEL_EXTRA) / 100) / price2() / price3();
        if (m_pair1.isForward()) {
            m_need = 1 / need;
            m_bracketPrice = 1 / bracketPrice;
        } else {
            m_need = need;
            m_bracketPrice = bracketPrice;
        }
    }

    private double price2() { return m_pair2.isForward() ? 1/m_price2 : m_price2; }
    private double price3() { return m_pair3.isForward() ? 1/m_price3 : m_price3; }
    public boolean mktCrossLvl() { return m_parent.mktCrossLvl(); }
    public double level() { return m_parent.level(); }

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
                ", bracket=" + m_bracketPrice +
                ", price1=" + m_price1 +
                ", pair1=" + m_pair1 +
                ", price2=" + m_price2 +
                ", pair2=" + m_pair2 +
                ", price3=" + m_price3 +
                ", pair3=" + m_pair3 +
                '}';
    }

    public String str() {
        double level = level();
        if (m_max > level) {
            return m_indx + ":" + Triplet.formatAndPad(m_max);
        }
        return "        ";
    }

    public String str2() {
        double level = level();
        if (m_max10 > level) {
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
        PairDirection pd = getPd(indx);
        return calcMktPrice(tops, pd);
    }

    public double calcMidPrice(TopsData tops, int indx) {
        PairDirection pd = getPd(indx);
        TopData top = tops.get(pd.m_pair);
        return top.getMid();
    }

    public double calcMktPrice(TopsData tops, PairDirection pd) {
        TopData top = tops.get(pd.m_pair);
        double price = Triangle.mktPrice(top, pd);
        return price;
    }

    public double calcFollowPrice(TopsData tops, int indx) {
        PairDirection pd = getPd(indx);
        TopData top = tops.get(pd.m_pair);
        double price = Triangle.followPrice(top, pd);
        return price;
    }

    public double calcMktPrice(TopsData tops, int indx, double offset) {
        PairDirection pd = getPd(indx);
        return calcMktPrice(tops, pd, offset);
    }

    private PairDirection getPd(int indx) {
        return (indx == 0)
                    ? m_pair1
                    : ((indx == 1)
                        ? m_pair2
                        : m_pair3);
    }

    public double calcMktPrice(TopsData tops, PairDirection pd, double offset) {
        TopData top = tops.get(pd.m_pair);
        double price = Triangle.mktPrice(top, pd, offset);
        return price;
    }

    /** commissions deducted */
    public double pegRatio1(TopsData tops, AccountData account) {
        double pegPrice = calcPegPrice(tops);
        return pegRatio1(account, pegPrice);
    }

    /** commissions deducted */
    public double pegRatio1(AccountData account, double pegPrice) {
        OrderSide side = m_pair1.getSide();
        return calcRatio(account, pegPrice, side, m_pair1.m_pair);
    }

    /** commissions deducted */
    public double mktRatio2(TopsData tops, AccountData account) {
        return mktRatio(tops, account, m_pair2);
    }

    /** commissions deducted */
    public double mktRatio2(TopsData tops, AccountData account, double offset) {
        return mktRatio(tops, account, m_pair2, offset);
    }

    /** commissions deducted */
    public double mktRatio3(TopsData tops, AccountData account) {
        return mktRatio(tops, account, m_pair3);
    }

    /** commissions deducted */
    public double mktRatio3(TopsData tops, AccountData account, double offset) {
        return mktRatio(tops, account, m_pair3, offset);
    }

    /** commissions deducted */
    private double mktRatio(TopsData tops, AccountData account, PairDirection pd) {
        double mktPrice = calcMktPrice(tops, pd);
        OrderSide side = pd.getSide();
        return calcRatio(account, mktPrice, side, pd.m_pair);
    }

    public double calcRatio(AccountData account, double price, OrderSide side, Pair pair) {
        double fee = account.getFee(Triplet.s_exchange, pair);
        return (side.isBuy() ? 1 / price : price) * (1 - fee); // deduct commissions
    }

    /** commissions deducted */
    private double mktRatio(TopsData tops, AccountData account, PairDirection pd, double offset) {
        double mktPrice = calcMktPrice(tops, pd, offset);
        OrderSide side = pd.getSide();
        return calcRatio(account, mktPrice, side, pd.m_pair);
    }

    public double getBracketDistance(TopsData tops) {
        TopData topData = tops.get(m_pair1.m_pair);
        double mid = topData.getMid();
        double bidAskDiff = topData.getBidAskDiff();
        double distance = (bidAskDiff >= 0.00000001)
                            ? Math.abs(m_bracketPrice - mid) / (bidAskDiff / 2)
                            : 1; // BID == ASK;  todo: calc average bidAskDiffs
        return distance;
    }
}
