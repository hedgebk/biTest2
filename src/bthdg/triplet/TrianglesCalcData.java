package bthdg.triplet;

import bthdg.Pair;
import bthdg.exch.TopData;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

public class TrianglesCalcData extends ArrayList<TriangleCalcData> {
    public TrianglesCalcData(int length) {
        super(length);
    }

    static TrianglesCalcData calc(Map<Pair, TopData> tops) {
        TrianglesCalcData ret = new TrianglesCalcData(Triplet.TRIANGLES.length);
        for (Triangle tr : Triplet.TRIANGLES) {
//                log("Triangle: " + tr.name());
            TriangleCalcData calc = TriangleCalcData.calc(tops, tr);
            ret.add(calc);
        }
        return ret;
    }

    public String str() {
        StringBuilder sb = new StringBuilder();
        boolean mktCrossLvl = false;
        for (TriangleCalcData t : this) {
            sb.append(t.str());
            mktCrossLvl |= t.mktCrossLvl();
        }
        if (mktCrossLvl) {
            sb.append("\t******************************");
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
}
