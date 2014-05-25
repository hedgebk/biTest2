package bthdg.triplet;

import bthdg.*;
import bthdg.exch.*;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class FundMap {
    static final Map<Currency,Double> s_distributeRatio = new HashMap<Currency, Double>();
    static{
        s_distributeRatio.put(Currency.LTC, 0.30);
        s_distributeRatio.put(Currency.EUR, 0.27);
        s_distributeRatio.put(Currency.BTC, 0.23);
        s_distributeRatio.put(Currency.USD, 0.20);
        s_distributeRatio.put(Currency.PPC, 0.0);
    }

    public static void test(AccountData account, TopsData tops) {
        Map<Currency, Double> valuateMap = new HashMap<Currency, Double>();
        for (Currency inCurrency : Currency.values()) {
            double valuate = account.evaluate(tops, inCurrency);
            valuateMap.put(inCurrency, valuate);
        }

        Currency currencyOut = Currency.BTC; // compare all values in BTC
        double valuateBtc = valuateMap.get(currencyOut);

        TreeMap<Double, Currency> difMap = new TreeMap<Double, Currency>();
        TreeMap<Double, Currency> difMapBtc = new TreeMap<Double, Currency>();
        for (Currency currencyIn : Currency.values()) {
            double inValue = account.getAllValue(currencyIn);
            Double weight = FundMap.s_distributeRatio.get(currencyIn);
            double convertedBtc = (currencyIn == currencyOut)
                    ? inValue :
                    tops.convert(currencyIn, currencyOut, inValue);

            double expectedBtc = weight * valuateBtc;

            double diffBtc = convertedBtc - expectedBtc;
            difMapBtc.put(diffBtc, currencyIn);

            double diff = inValue - valuateBtc;
            difMap.put(diff, currencyIn);
        }

        Map.Entry<Double, Currency> first = difMapBtc.firstEntry();
        Map.Entry<Double, Currency> last = difMapBtc.lastEntry();
        System.out.println("first=" + first);
        System.out.println("last=" + last);
        // first=-0.006596838672291869=EUR
        // last=0.006052955754570488=LTC

        Currency from = last.getValue();
        Currency to = first.getValue();

        System.out.println("from=" + from);
        double fromValueHave = account.getAllValue(from);
        System.out.println(" fromValueHave=" + fromValueHave + " " + from);
        Double fromWeight = FundMap.s_distributeRatio.get(from);
        System.out.println(" fromWeight=" + fromWeight);
        double fromValuate = valuateMap.get(from);
        System.out.println(" fromValuate=" + fromValuate + " " + from);
        double fromExpected = fromWeight * fromValuate;
        System.out.println(" fromExpected=" + fromExpected + " " + from);
        double fromExtra = fromValueHave - fromExpected;
        System.out.println(" fromExtra=" + fromExtra + " " + from);
        if (fromExtra > 0) {
            double fromExtraConverted = tops.convert(from, to, fromExtra);
            System.out.println(" fromExtraConverted=" + fromExtraConverted + " " + to);

            System.out.println("to=" + to);
            double toValueHave = account.getAllValue(to);
            System.out.println(" toValueHave=" + toValueHave + " " + to);
            Double toWeight = FundMap.s_distributeRatio.get(to);
            System.out.println(" toWeight=" + toWeight);
            double toValuate = valuateMap.get(to);
            System.out.println(" toValuate=" + toValuate + " " + to);
            double toExpected = toWeight * toValuate;
            System.out.println(" toExpected=" + toExpected + " " + to);
            double toNeed = toExpected - toValueHave;
            System.out.println(" toNeed=" + toNeed + " " + to);

            if (toNeed > 0) {
                double amount = Math.min(fromExtraConverted, toNeed);
                System.out.println("amount(min)=" + amount + " " + to);
                if (amount > toExpected / 8) { // do not move very small amounts
                    amount *= 0.9;
                    System.out.println(" amount*0.9=" + amount + " " + to);
                    if (PairDirection.support(from, to)) {
                        PairDirection pd = PairDirection.get(from, to);
                        System.out.println("PairDirection=" + pd);
                        Pair pair = pd.m_pair;
                        System.out.println(" pair=" + pair);
                        boolean forward = pd.isForward();
                        System.out.println(" forward=" + forward);
                        OrderSide side = pd.getSide();
                        System.out.println("  side=" + side);

                        TopData top = tops.get(pair);
                        System.out.println("top=" + top);
                        double step = Btce.minOurPriceStep(pair);
                        System.out.println("minOurPriceStep=" + step);
                        double limitPrice = side.pegPrice(top, step);
                        System.out.println("price(peg)=" + limitPrice);
                        if (!forward) {
                            amount = amount / limitPrice;
                            System.out.println("amount corrected=" + amount);
                        }

                        double roundAmount = Exchange.BTCE.roundAmount(amount, pair);
                        System.out.println("roundAmount=" + roundAmount);

                        OrderData orderData = new OrderData(pair, side, limitPrice, roundAmount);
                        System.out.println("confirm orderData=" + orderData);
                        System.out.println("order " + side.m_name + " " + roundAmount + " " + pair.m_to + " for " + pair.m_from + " @ mkt");
                    } else {
                        System.out.println("not support direction " + from + " -> " + to);
                    }
                } else {
                    System.out.println("amount is small to move");
                }
            } else {
                System.out.println("ERROR: toNeed < 0");
            }
        } else {
            System.out.println("ERROR: fromExtra < 0");
        }
    }
}
