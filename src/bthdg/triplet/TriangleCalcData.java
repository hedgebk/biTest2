package bthdg.triplet;

import bthdg.Pair;
import bthdg.exch.TopData;

import java.util.Map;
import java.util.TreeMap;

public class TriangleCalcData {
    private TriangleRotationCalcData m_forward;
    private TriangleRotationCalcData m_backward;

    public TriangleCalcData(TriangleRotationCalcData forward, TriangleRotationCalcData backward) {
        m_forward = forward;
        m_backward = backward;
    }

    static TriangleCalcData calc(Map<Pair, TopData> tops, Triangle triangle) {
        return new TriangleCalcData(TriangleRotationCalcData.calc(tops, triangle, true),
                                    TriangleRotationCalcData.calc(tops, triangle, false));
    }

    public String str() {
        return m_forward.str() + " " + m_backward.str() + " " +
                (m_forward.midCrossLvl() || m_backward.midCrossLvl() ? "*" : "|") + " ";
    }

    public boolean mktCrossLvl() {
        return m_forward.mktCrossLvl() || m_backward.mktCrossLvl();
    }

    public void findBestMap(TreeMap<Double, OnePegCalcData> ret) {
        m_forward.findBestMap(ret);
        m_backward.findBestMap(ret);
    }
}
