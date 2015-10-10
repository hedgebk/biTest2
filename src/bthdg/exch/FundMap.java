package bthdg.exch;

import bthdg.util.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class FundMap {
    public static final Map<Exchange,Map<Currency,Double>> s_map = new HashMap<Exchange,Map<Currency,Double>>();

    public static OrderData test(AccountData account, TopsData tops, Exchange exchange, double amountPart) {
        Map<Currency, Double> ratioMap = getDistributeRatio(exchange);
        Currency[] currencies = exchange.supportedCurrencies();

        return test(account, tops, exchange, ratioMap, currencies, amountPart);
    }

    public static OrderData test(AccountData account, TopsData tops, Exchange exchange, Map<Currency, Double> ratioMap,
                                 Currency[] currencies, double amountPart) {
        Map<Currency, Double> valuateMap = new HashMap<Currency, Double>();
        for (Currency inCurrency : currencies) {
            double valuate = account.evaluateAll(tops, inCurrency, exchange);
            valuateMap.put(inCurrency, valuate);
        }

        Currency currencyOut = Currency.BTC; // compare all values in BTC
        double valuateBtc = valuateMap.get(currencyOut);

        TreeMap<Double, Currency> difMap = new TreeMap<Double, Currency>();
        TreeMap<Double, Currency> difMapBtc = new TreeMap<Double, Currency>();
        for (Currency currencyIn : currencies) {
            Double inValue = account.getAllValue(currencyIn);
            Double weight = ratioMap.get(currencyIn);
            Double convertedBtc = (currencyIn == currencyOut)
                    ? inValue :
                    tops.convert(currencyIn, currencyOut, inValue, exchange);
            if (convertedBtc != null) {
                double expectedBtc = weight * valuateBtc;

                double diffBtc = convertedBtc - expectedBtc;
                difMapBtc.put(diffBtc, currencyIn);

                double diff = inValue - valuateBtc;
                difMap.put(diff, currencyIn);
            }
        }

        Map.Entry<Double, Currency> first = difMapBtc.firstEntry();
        Map.Entry<Double, Currency> last = difMapBtc.lastEntry();
        System.out.println("first=" + first + ";  last=" + last);

        Currency from = last.getValue();
        Currency to = first.getValue();

        System.out.println("from=" + from + ";  to=" + to);
        double fromValueHave = account.getAllValue(from);
        Double fromWeight = ratioMap.get(from);
        double fromValuate = valuateMap.get(from);
        double fromExpected = fromWeight * fromValuate;
        double fromExtra = fromValueHave - fromExpected;
        System.out.println(" ValueHave=" + fromValueHave + " " + from +
                "; Weight=" + fromWeight +
                "; Valuate=" + fromValuate + " " + from +
                "; Expected=" + fromExpected + " " + from +
                "; Extra=" + Utils.format5(fromExtra) + " " + from);
        if (fromExtra > 0) {
            double fromExtraConverted = tops.convert(from, to, fromExtra, exchange);
            System.out.println(" ExtraConverted=" + Utils.format5(fromExtraConverted) + " " + to);
            double toValueHave = account.getAllValue(to);
            Double toWeight = ratioMap.get(to);
            double toValuate = valuateMap.get(to);
            double toExpected = toWeight * toValuate;
            double toNeed = toExpected - toValueHave;
            System.out.println(" ValueHave=" + toValueHave + " " + to +
                    "; Weight=" + toWeight +
                    "; Valuate=" + toValuate + " " + to +
                    "; Expected=" + toExpected + " " + to  +
                    "; Need=" + Utils.format5(toNeed) + " " + to);

            if (toNeed > 0) {
                double amount = Math.min(fromExtraConverted, toNeed);
                System.out.println("amount(min)=" + Utils.format5(amount) + " " + to);
                if (exchange.supportPair(from, to)) {
                    PairDirection pd = PairDirection.get(from, to);
                    Pair pair = pd.m_pair;
                    System.out.println("PairDirection=" + pd + ";  pair=" + pair);

                    if(amountPart != 1) {
                        amount *= amountPart;
                        System.out.println(" amount*" + amountPart + "=" + Utils.format5(amount) + " " + to);
                    }

                    boolean forward = pd.isForward();
                    OrderSide side = pd.getSide();
                    System.out.println(" forward=" + forward + ";  side=" + side);

                    TopData top = tops.get(pair);
                    System.out.println("top=" + top);
                    double step = exchange.minOurPriceStep(pair);
                    double exchPriceStep = exchange.minExchPriceStep(pair);
                    Currency baseCurrency = pair.currencyFrom(false);
                    System.out.println("minOurPriceStep=" + exchange.roundAmountStr(step, pair) +
                                       "; minExchPriceStep=" + exchange.roundAmountStr(exchPriceStep, pair) + " (" + baseCurrency + ")");
                    double limitPrice = side.pegPrice(top, step, exchPriceStep);
                    System.out.println("price(peg)=" + exchange.roundPriceStr(limitPrice, pair));
                    if (!forward) {
                        amount = amount / limitPrice;
                        System.out.println("!forward -> amount corrected=" + exchange.roundAmountStr(amount, pair) + " " + from);
                    }

                    double minOrderSize = exchange.minOrderToCreate(pair);
                    System.out.println(" minOrderSize=" + minOrderSize + " " + baseCurrency);

                    if (amount >= minOrderSize) { // do not move very small amounts
                        double roundAmount = exchange.roundAmount(amount, pair);
                        String roundAmountStr = exchange.roundAmountStr(roundAmount, pair);
                        System.out.println("roundAmount=" + roundAmountStr);

                        OrderData orderData = new OrderData(pair, side, limitPrice, roundAmount);
                        System.out.println("confirm orderData=" + orderData);
                        System.out.println("order " + side.m_name + " " + roundAmountStr + " " + pair.m_to + " for " + pair.m_from + " @ mkt");
                        return orderData;
                    } else {
                        System.out.println("amount is small to move: " + Utils.format5(amount) + " " + to + "; minOrderSize=" + minOrderSize);
                    }
                } else {
                    System.out.println("not support direction " + from + " -> " + to);
                }
            } else {
                System.out.println("ERROR: toNeed < 0");
            }
        } else {
            System.out.println("ERROR: fromExtra < 0");
        }
        return null;
    }

    public static Map<Currency, Double> getDistributeRatio(Exchange exchange) {
        Map<Currency, Double> distributeRatio = s_map.get(exchange);
        if (distributeRatio == null) {
            throw new RuntimeException("no funds distributeRatio defined for exchange " + exchange);
        }
        return distributeRatio;
    }
}
