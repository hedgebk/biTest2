package bthdg.triplet;

import bthdg.exch.TopsData;

import java.util.TreeMap;

public class TriangleRotationCalcData {
    public Triangle m_triangle;
    public Direction m_direction;
    private double m_mid;
    double m_mkt; // tri-mkt
    private double m_mktMinus10;  // mkt-10
    private double m_mktMinus20; // mkt-20
    private OnePegCalcData[] m_pegs;
    private OnePegCalcData m_peg;

    @Override public boolean equals(Object obj) {
        if(obj == this) { return true; }
        if(obj instanceof TriangleRotationCalcData) {
            TriangleRotationCalcData other = (TriangleRotationCalcData) obj;
            if(m_direction == other.m_direction) {
                return m_triangle.equals(other.m_triangle);
            }
        }
        return false;
    }

    public TriangleRotationCalcData(Triangle triangle, Direction direction, double mid, double mkt, double mktMinus10, double mktMinus20, OnePegCalcData[] pegs) {
        m_triangle = triangle;
        m_direction = direction;
        m_mid = mid;
        m_mkt = mkt;
        m_mktMinus10 = mktMinus10;
        m_mktMinus20 = mktMinus20;
        m_pegs = pegs;
        m_peg = findBestPeg();
        pegs[0].m_parent = this;
        pegs[1].m_parent = this;
        pegs[2].m_parent = this;
    }

    private OnePegCalcData findBestPeg() {
        double d1 = m_pegs[0].m_max;
        double d2 = m_pegs[1].m_max;
        double d3 = m_pegs[2].m_max;
        int indx = (d1 > d2) ? ((d3 > d1) ? 2 : 0) : ((d3 > d2) ? 2 : 1);
        return m_pegs[indx];
    }

    static TriangleRotationCalcData calc(TopsData tops, Triangle triangle, Direction direction) {
//            log(" rotate forward=" + forward + " on Triangle: " + triangle.name());
        return new TriangleRotationCalcData(triangle, direction,
                                            triangle.calcMid(tops, direction),
                                            triangle.calcMkt(tops, direction),
                                            triangle.calcMkt(tops, direction, 0.1d),
                                            triangle.calcMkt(tops, direction, 0.2d),
                                            triangle.calcPegs(tops, direction));
    }

    public String str() {
        return Triplet.formatAndPad(m_mid) + " " + Triplet.formatAndPad(m_mkt) + " " + m_peg.str();
    }

    public String str2() {
        return Triplet.formatAndPad(m_mktMinus10) + " " + Triplet.formatAndPad(m_mktMinus20) + " " + m_peg.str2()
                + ((m_mktMinus10 > Triplet.LVL)
                      ? "$"
                      : (m_mktMinus20 > Triplet.LVL) ? "*" : " "
                  );
    }

    public boolean midCrossLvl() {
        return (m_mid > Triplet.LVL);
    }

    public boolean mktCrossLvl() {
        return (m_mkt > Triplet.LVL);
    }

    public boolean mktCrossLvl2() {
        return (m_mktMinus10 > Triplet.LVL);
    }

    public boolean mktCrossLvl3() {
        return (m_mktMinus20 > Triplet.LVL);
    }

    public void findBestMap(TreeMap<Double, OnePegCalcData> ret) {
        findBestMap(ret, 0);
        findBestMap(ret, 1);
        findBestMap(ret, 2);
    }

    private void findBestMap(TreeMap<Double, OnePegCalcData> ret, int indx) {
        OnePegCalcData peg = m_pegs[indx];
        double max = peg.m_max;
        double key = 1 / max; // 1/key - to make items appears from high to low
        if (mktCrossLvl()) {
            key = -m_mkt; // mktCrossed are first
        }
        ret.put(key, peg);
    }

    public void checkMkt() {
        //
    }
}
