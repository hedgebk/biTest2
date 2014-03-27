package bthdg.triplet;

import bthdg.Pair;
import bthdg.exch.TopData;

import java.util.Map;
import java.util.TreeMap;

public class TriangleRotationCalcData {
    public Triangle m_triangle;
    public boolean m_forward;
    private double m_mid;
    private double m_mkt;
    private OnePegCalcData[] m_pegs;
    private OnePegCalcData m_peg;

    @Override public boolean equals(Object obj) {
        if(obj == this) { return true; }
        if(obj instanceof TriangleRotationCalcData) {
            TriangleRotationCalcData other = (TriangleRotationCalcData) obj;
            if(m_forward == other.m_forward) {
                return m_triangle.equals(other.m_triangle);
            }
        }
        return false;
    }

    public TriangleRotationCalcData(Triangle triangle, boolean forward, double mid, double mkt, OnePegCalcData[] pegs) {
        m_triangle = triangle;
        m_forward = forward;
        m_mid = mid;
        m_mkt = mkt;
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

    static TriangleRotationCalcData calc(Map<Pair, TopData> tops, Triangle triangle, boolean forward) {
//            log(" rotate forward=" + forward + " on Triangle: " + triangle.name());
        return new TriangleRotationCalcData(triangle, forward,
                                            triangle.calcMid(tops, forward),
                                            triangle.calcMkt(tops, forward),
                                            triangle.calcPegs(tops, forward));
    }

    public String str() {
        return Triplet.formatAndPad(m_mid) + " " + Triplet.formatAndPad(m_mkt) + " " + m_peg.str();
    }

    public boolean midCrossLvl() {
        return (m_mid > Triplet.LVL);
    }

    public boolean mktCrossLvl() {
        return (m_mkt > Triplet.LVL);
    }

    public void findBestMap(TreeMap<Double, OnePegCalcData> ret) {
        findBestMap(ret, 0);
        findBestMap(ret, 1);
        findBestMap(ret, 2);
    }

    private void findBestMap(TreeMap<Double, OnePegCalcData> ret, int indx) {
        OnePegCalcData peg = m_pegs[indx];
        double key = peg.m_max;
        ret.put(1 / key, peg); // 1/key - to make items appears from high to low
    }
}