package bthdg.triplet;

import bthdg.exch.AccountData;
import bthdg.exch.TopsData;

import java.util.ArrayList;
import java.util.TreeMap;

public class TrianglesCalcData extends ArrayList<TriangleCalcData> {
    boolean m_mktCrossLvl; // cross 3-mkt level

    public TrianglesCalcData(int length) {
        super(length);
    }

    static TrianglesCalcData calc(TopsData tops, AccountData account) {
        boolean mktCrossLvl = false;
        TrianglesCalcData ret = new TrianglesCalcData(Triplet.TRIANGLES.length);
        for (Triangle tr : Triplet.TRIANGLES) {
            TriangleCalcData calc = TriangleCalcData.calc(tops, tr, account);
            ret.add(calc);
            mktCrossLvl |= calc.mktCrossLvl();
        }
        ret.m_mktCrossLvl = mktCrossLvl;
        return ret;
    }

    public String str() {
        StringBuilder sb = new StringBuilder();
        for (TriangleCalcData t : this) {
            sb.append(t.str());
        }
        if (m_mktCrossLvl) {
            sb.append("\t******************************");
        }
        sb.append("\n");
        boolean mktCrossLvl2 = false;
        boolean mktCrossLvl3 = false;
        for (TriangleCalcData t : this) {
            sb.append(t.str2());
            mktCrossLvl2 |= t.mktCrossLvl2();
            mktCrossLvl3 |= t.mktCrossLvl3();
        }
        if (mktCrossLvl2) {
            sb.append("\t::::::::::::::::::::::::::::::");
        } else {
            if (mktCrossLvl3) {
                sb.append("\t..............................");
            }
        }
        return sb.toString();
    }

    public TreeMap<Double, OnePegCalcData> findBestMap() {
        TreeMap<Double, OnePegCalcData> ret = new TreeMap<Double, OnePegCalcData>();
        for (TriangleCalcData triangle : this) {
            triangle.findBestMap(ret);
        }
        return ret;
    }

    public void checkMkt() {
        for (TriangleCalcData triangle : this) {
            triangle.checkMkt();
        }
    }
}
