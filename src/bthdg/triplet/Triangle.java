package bthdg.triplet;

import bthdg.*;
import bthdg.exch.Btce;
import bthdg.exch.TopData;
import bthdg.exch.TopsData;

import java.util.ArrayList;
import java.util.Map;

public class Triangle extends ArrayList<PairDirection> {
    public Triangle(Pair pair1, boolean forward1, Pair pair2, boolean forward2, Pair pair3, boolean forward3) {
        add(new PairDirection(pair1, forward1));
        add(new PairDirection(pair2, forward2));
        add(new PairDirection(pair3, forward3));
    }

    public double calcMkt(TopsData tops, boolean forward) {
        return forward
                ? calcMkt(tops, get(0).get(forward), get(1).get(forward), get(2).get(forward))
                : calcMkt(tops, get(2).get(forward), get(1).get(forward), get(0).get(forward));
    }

    public double calcMkt(TopsData tops, boolean forward, double offset) {
        return forward
                ? calcMkt(tops, get(0).get(forward), get(1).get(forward), get(2).get(forward), offset)
                : calcMkt(tops, get(2).get(forward), get(1).get(forward), get(0).get(forward), offset);
    }

    private static double calcMkt(TopsData tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
        return mulMkt(mulMkt(mulMkt((double) 100, tops.get(pair1.m_pair), pair1), tops.get(pair2.m_pair), pair2), tops.get(pair3.m_pair), pair3);
    }

    private static double calcMkt(TopsData tops, PairDirection pair1, PairDirection pair2, PairDirection pair3, double offset) {
        return mulMkt(mulMkt(mulMkt((double) 100, tops.get(pair1.m_pair), pair1, offset), tops.get(pair2.m_pair), pair2, offset), tops.get(pair3.m_pair), pair3, offset);
    }

    public double calcMid(TopsData tops, boolean forward) {
        return forward
                ? calcMid(tops, get(0).get(forward), get(1).get(forward), get(2).get(forward))
                : calcMid(tops, get(2).get(forward), get(1).get(forward), get(0).get(forward));
    }

    private static double calcMid(TopsData tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
        return mulMid(mulMid(mulMid((double) 100, tops.get(pair1.m_pair), pair1), tops.get(pair2.m_pair), pair2), tops.get(pair3.m_pair), pair3);
    }

    public String name() {
        StringBuilder sb = new StringBuilder();
        for (PairDirection pd : this) {
            String name = pd.getName();
            sb.append(name);
            sb.append(";");
        }
        return sb.toString();
    }

    public OnePegCalcData[] calcPegs(TopsData tops, boolean forward) {
        PairDirection pd0 = get(0).get(forward);
        PairDirection pd1 = get(1).get(forward);
        PairDirection pd2 = get(2).get(forward);
        return forward ? calcPegs(tops, pd0, pd1, pd2) : calcPegs(tops, pd2, pd1, pd0);
    }

    private static OnePegCalcData[] calcPegs(TopsData tops, PairDirection pair1, PairDirection pair2, PairDirection pair3) {
        TopData top1 = tops.get(pair1.m_pair);
        TopData top2 = tops.get(pair2.m_pair);
        TopData top3 = tops.get(pair3.m_pair);
        double offset = Triplet.MINUS_MKT_OFFSET;

        return new OnePegCalcData[] {
                new OnePegCalcData(0,
                        mulMkt(mulMkt(mulPeg(100.0, top1, pair1), top2, pair2), top3, pair3),
                        mulMkt(mulMkt(mulPeg(100.0, top1, pair1), top2, pair2, offset), top3, pair3, offset),
                        pair1, pegPrice(top1, pair1),
                        pair2, mktPrice(top2, pair2), mktPrice(top2, pair2, offset),
                        pair3, mktPrice(top3, pair3), mktPrice(top3, pair3, offset)),
                new OnePegCalcData(1,
                        mulMkt(mulPeg(mulMkt(100.0, top1, pair1), top2, pair2), top3, pair3),
                        mulMkt(mulPeg(mulMkt(100.0, top1, pair1, offset), top2, pair2), top3, pair3, offset),
                        pair2, pegPrice(top2, pair2),
                        pair3, mktPrice(top3, pair3), mktPrice(top3, pair3, offset),
                        pair1, mktPrice(top1, pair1), mktPrice(top1, pair1, offset)),
                new OnePegCalcData(2,
                        mulPeg(mulMkt(mulMkt(100.0, top1, pair1), top2, pair2), top3, pair3),
                        mulPeg(mulMkt(mulMkt(100.0, top1, pair1, offset), top2, pair2, offset), top3, pair3),
                        pair3, pegPrice(top3, pair3),
                        pair1, mktPrice(top1, pair1), mktPrice(top1, pair1, offset),
                        pair2, mktPrice(top2, pair2), mktPrice(top2, pair2, offset))
        };
    }

    public static double mulMid(double in, TopData top, PairDirection pd) {
        double mid = midPrice(top, pd);
        double ret = pd.m_forward ? in / mid : in * mid;
        return ret;
    }

    public static double midPrice(TopData top, PairDirection pd) {
        double price = top.getMid();
        // the price is changed from quoted by exchange - need to be rounded
        double ret = Exchange.BTCE.roundPrice(price, pd.m_pair);
        return ret;
    }

    public static double mulMkt(double in, TopData top, PairDirection pd) {
        double mkt = mktPrice(top, pd);
        double ret = pd.m_forward ? in / mkt : in * mkt; // ASK > BID
        return ret;
    }
    public static double mktPrice(TopData top, PairDirection pd) {
        double price = pd.getSide().mktPrice(top); // ASK > BID
        return price;
    }

    public static double mulMkt(double in, TopData top, PairDirection pd, double offset) {
        double mktPrice = mktPrice(top, pd, offset);
        double ret = pd.m_forward ? in / mktPrice : in * mktPrice; // ASK > BID
        return ret;
    }

    public static double mktPrice(TopData top, PairDirection pd, double offset) {
        double delta = (top.m_ask - top.m_bid) * offset;
        OrderSide side = pd.getSide();
        double mktPrice = side.mktPrice(top);
        double price = pd.m_forward ? mktPrice - delta : mktPrice + delta; // ASK > BID
        // the price is changed from quoted by exchange - need to be rounded
        double ret = Exchange.BTCE.roundPrice(price, pd.m_pair);
        return ret;
    }

    public static double mulPeg(double in, TopData top, PairDirection pd) {
        double pegPrice = pegPrice(top, pd);
        double ret = pd.m_forward ? in / pegPrice : in * pegPrice; // ASK > BID
        return ret;
    }

    public static double pegPrice(TopData top, PairDirection pd) {
        Pair pair = pd.m_pair;
        double minPriceStep = Btce.minOurPriceStep(pair);
        OrderSide side = pd.getSide(); // ASK > BID
        double price = side.pegPrice(top, minPriceStep);
        // the price is changed from quoted by exchange - need to be rounded
        double ret = Exchange.BTCE.roundPrice(price, pair);
        return ret;
    }
} // Triangle
