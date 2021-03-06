package bthdg.triplet;

import bthdg.exch.AccountData;
import bthdg.exch.Direction;
import bthdg.exch.TopsData;

import java.util.TreeMap;

public class TriangleCalcData {
    TriangleRotationCalcData m_forward;
    TriangleRotationCalcData m_backward;

    public TriangleCalcData(TriangleRotationCalcData forward, TriangleRotationCalcData backward) {
        m_forward = forward;
        m_backward = backward;
    }

    static TriangleCalcData calc(TopsData tops, Triangle triangle, AccountData account) {
        double level = triangle.level(account);
        return new TriangleCalcData(TriangleRotationCalcData.calc(tops, triangle, Direction.FORWARD,  account, level),
                                    TriangleRotationCalcData.calc(tops, triangle, Direction.BACKWARD, account, level));
    }

    public String str() {
        return m_forward.str() + " " + (m_forward.midCrossLvl() ? "*" : ".") +
               m_backward.str() + " " + (m_backward.midCrossLvl() ? "*" : "|") +
               " ";
    }

    public String str2() {
        return m_forward.str2() + " " + m_backward.str2() + "| ";
    }

    public boolean mktCrossLvl() {
        return m_forward.mktCrossLvl() || m_backward.mktCrossLvl();
    }

    public boolean mktCrossLvl2() {
        return m_forward.mktCrossLvl2() || m_backward.mktCrossLvl2();
    }

    public boolean mktCrossLvl3() {
        return m_forward.mktCrossLvl3() || m_backward.mktCrossLvl3();
    }

    public void findBestMap(TreeMap<Double, OnePegCalcData> ret) {
        m_forward.findBestMap(ret);
        m_backward.findBestMap(ret);
    }

    public void checkMkt() {
        m_forward.checkMkt();
        m_backward.checkMkt();
    }
}
