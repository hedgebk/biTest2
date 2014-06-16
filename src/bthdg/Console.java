package bthdg;

import bthdg.exch.*;
import bthdg.exch.Currency;
import bthdg.triplet.FundMap;
import bthdg.util.ConsoleReader;
import bthdg.util.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class Console {
    private static Exchange s_exchange = Exchange.BTCE; // by default btce

    public static void main(String[] args) {
        System.out.println("Started.");
        printHelp();
        Fetcher.LOG_LOADING = true;
        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
        Btce.LOG_PARSE = true;

        System.out.println("Default exchange is " + s_exchange);

        try {
            Properties keys = BaseExch.loadKeys();
            Btce.init(keys);
            OkCoin.init(keys);
            Btcn.init(keys);

            new ConsoleReader() {
                @Override protected void beforeLine() {
                    System.out.print(">");
                }
                @Override protected boolean processLine(String line) throws Exception {
                    return process(line);
                }
            }.run();
        } catch (Exception e) {
            System.err.println("error: " + e);
            e.printStackTrace();
        }
    }

    private static boolean process(String line) throws Exception {
        if( line.equals("exit")) {
            return true;
        } else if( line.startsWith("account")) {
            doAccount(line);
        } else if( line.startsWith("orders ")) {
            doOrders(line);
        } else if( line.equals("orders")) {
            doOrders();
        } else if( line.equals("tops")) {
            doTops();
        } else if( line.startsWith("top ")) {
            doTop(line);
        } else if( line.startsWith("order ")) {
            doOrder(line);
        } else if( line.startsWith("cancel ")) {
            doCancel(line);
        } else if( line.equals("am")) {
            AccountData account = Fetcher.fetchAccount(s_exchange);
            if (account != null) {
                TopsData tops = fetchTops();
                doAccountMap(account, tops);
            }
        } else if( line.startsWith("e ")) {
            switchExchange(line);
        } else if( line.equals("help")) {
            printHelp();
        } else {
            System.err.println("unknown command '" + line + "'");
            printHelp();
        }
        return false;
    }

    private static void switchExchange(String line) {
        String exchName = line.substring(2).toUpperCase();
        List<Exchange> startsWith = Exchange.resolveExchange(exchName);
        if(startsWith.size() > 1) {
            System.err.println("multiple exchanges starts with '"+exchName+"' : " + startsWith);
        } else {
            if(startsWith.size() == 1) {
                s_exchange = startsWith.get(0);
                String name = s_exchange.name();
                if(name.equalsIgnoreCase(exchName)) {
                    System.err.println(" current exchange is changed successfully to "+name);
                } else {
                    System.err.println(" current exchange is guessed and changed successfully to "+ name);
                }
            } else {
                System.err.println("exchange '"+exchName+"' not found. supported: " + Arrays.asList(Exchange.values()));
            }
        }

//        System.err.println("requested change to exchange "+exchName);
//        List<Exchange> startsWith = new ArrayList<Exchange>();
//        for(Exchange exchange: Exchange.values()) {
//            String name = exchange.name();
//            if( name.equalsIgnoreCase(exchName) ) {
//                s_exchange = exchange;
//                System.err.println(" current exchange is changed successfully to "+name);
//                return;
//            }
//            if( name.startsWith(exchName) ) {
//                startsWith.add(exchange);
//            }
//        }
//        if(startsWith.size() == 1) {
//            s_exchange = startsWith.get(0);
//            System.err.println(" current exchange is guessed and changed successfully to "+s_exchange.name());
//            return;
//        }
//        if(startsWith.size() > 1) {
//            System.err.println("multiple exchanges starts with '"+exchName+"' : " + startsWith);
//        } else {
//            System.err.println("exchange '"+exchName+"' not found. supported: " + Arrays.asList(Exchange.values()));
//        }
    }

    private static void doOrders() throws Exception {
        OrdersData od = Fetcher.fetchOrders(s_exchange);
        onOrders(od);
    }

    private static void doOrders(String line) throws Exception {
        String pairName = line.substring(7);
        Pair pair = Pair.resolvePair(pairName, s_exchange);
        if (pair == null) {
            System.out.println("pair '" + pairName + "' not supported by " + s_exchange + ". supported pairs: " + Arrays.asList(s_exchange.supportedPairs()));
        } else {
            OrdersData od = Fetcher.fetchOrders(s_exchange, pair);
            onOrders(od);
        }
    }

    private static void onOrders(OrdersData od) {
        System.out.println("ordersData=" + od);
        String error = od.m_error;
        if (error == null) {
            if (od.m_ords.isEmpty()) {
                System.out.println(" no live order");
            } else {
                for (OrdersData.OrdData ord : od.m_ords.values()) {
                    System.out.println(" next order: " + ord);
                }
            }
        } else {
            System.err.println("error: " + error);
        }
    }

    private static void doAccount(String line) throws Exception {
        AccountData account = Fetcher.fetchAccount(s_exchange);
        if (account != null) {
            if (line.equals("account map")) {
                TopsData tops = fetchTops();
                doAccountMap(account, tops);
            } else {
                System.out.println("account=" + account);
//                double valuateEur = account.evaluateEur(tops);
//                double valuateUsd = account.evaluateUsd(tops);
//                System.out.println("account=" + account + "; valuateEur=" + valuateEur + " EUR; valuateUsd=" + valuateUsd + " USD");
            }
        } else {
            System.err.println("account request error");
        }
    }

    private static TopsData fetchTops() throws Exception {
        return Fetcher.fetchTops(s_exchange, s_exchange.supportedPairs());
    }

    private static void doAccountMap(AccountData account, TopsData tops) {
        Map<Currency, Double> valuateMap = new HashMap<Currency, Double>();

        Currency[] supportedCurrencies = s_exchange.supportedCurrencies();
        String s = "          ";
        for (Currency inCurrency : supportedCurrencies) {
            double valuate = account.evaluate(tops, inCurrency, s_exchange);
            valuateMap.put(inCurrency, valuate);
            s += Utils.padLeft(inCurrency.toString(), 32);
        }
        System.out.println(s);

        for (Currency currencyIn : supportedCurrencies) {
            double inValue = account.getAllValue(currencyIn);
            String str = Utils.padLeft(Utils.X_YYYYY.format(inValue), 9) + " " + currencyIn + " ";
            Map<Currency, Double> distributeRatio = FundMap.distributeRatio(s_exchange);
            Double rate = distributeRatio.get(currencyIn);
            for (Currency currencyOut : supportedCurrencies) {
                double converted = (currencyIn == currencyOut)
                        ? inValue :
                        tops.convert(currencyIn, currencyOut, inValue, s_exchange);
                str += Utils.padLeft(Utils.X_YYYYY.format(converted), 9) + " ";

                double valuate = valuateMap.get(currencyOut);
                double expected = rate * valuate;
                str += Utils.padLeft(Utils.X_YYYYY.format(expected), 9) + " ";

                double diff = converted - expected;
                str += Utils.padLeft(Utils.X_YYYYY.format(diff), 9) + " | ";
            }
            System.out.println(str);
        }

        s = "             ";
        for (Currency outCurrency : supportedCurrencies) {
            double valuate = valuateMap.get(outCurrency);
            s +=  Utils.padRight(Utils.padLeft(Utils.X_YYYYY.format(valuate), 9), 29) + " | ";
        }
        System.out.println(s);
        FundMap.test(account, tops, s_exchange);
    }

    private static void printHelp() {
        System.out.println("supported commands:\n help; exit; account; tops; order; cancel");
    }

    private static void doTops() throws Exception {
        TopsData tops;
//            DeepsData deeps = Fetcher.fetchDeeps(s_exchange, s_exchange.supportedPairs());
//            tops = deeps.getTopsDataAdapter();
//            printTops(tops);

        tops = Fetcher.fetchTops(s_exchange, s_exchange.supportedPairs());
        printTops(tops);
    }

    private static void doTop(String line) throws Exception {
        String pairName = line.substring(4);
        Pair pair = Pair.resolvePair(pairName, s_exchange);
        if (pair == null) {
            System.out.println("pair '" + pairName + "' not supported by " + s_exchange + ". supported pairs: " + Arrays.asList(s_exchange.supportedPairs()));
        } else {
            TopData top = Fetcher.fetchTop(s_exchange, pair);
            System.out.println(" " + pair + " : " + top.toString(s_exchange, pair));
        }
    }

    private static void printTops(TopsData tops) {
        for (Map.Entry<Pair, TopData> entry : tops.entrySet()) {
            Pair pair = entry.getKey();
            TopData top = entry.getValue();
            System.out.println(" " + pair + " : " + top);
        }
    }

    private static void doCancel(String line) throws Exception {
        // cancel 12345 [LTC_CNH]
        StringTokenizer tok = new StringTokenizer(line.toLowerCase());
        int tokensNum = tok.countTokens();
        if(s_exchange.requirePairForCancel()) {
            if (tokensNum == 3) {
                tok.nextToken();
                String orderId = tok.nextToken();
                String pairName = tok.nextToken();
                Pair pair = Pair.resolvePair(pairName, s_exchange);
                if (pair == null) {
                    System.out.println("pair '" + pairName + "' not supported by " + s_exchange + ". supported pairs: " + Arrays.asList(s_exchange.supportedPairs()));
                } else {
                    CancelOrderData coData = Fetcher.calcelOrder(s_exchange, orderId, pair);
                    System.out.println("cancel order '" + orderId + "' result: " + coData);
                }
            } else {
                System.err.println("invalid 'cancel' command: use followed format: cancel orderId pair. supported pairs: " + Arrays.asList(s_exchange.supportedPairs()));
            }
        } else {
            if (tokensNum >= 2) {
                tok.nextToken();
                String orderId = tok.nextToken();
                CancelOrderData coData = Fetcher.calcelOrder(s_exchange, orderId, null);
                System.out.println("cancel order '" + orderId + "' result: " + coData);
            } else {
                System.err.println("invalid 'cancel' command: use followed format: cancel orderId");
            }
        }
    }

    private static void doOrder(String line) throws Exception {
        // order [buy|sell] 0.1 eur for btc [@|at] [11.5|mkt|peg]
        StringTokenizer tok = new StringTokenizer(line.toLowerCase());
        int tokensNum = tok.countTokens();
        if(tokensNum >= 8) {
            String t1 = tok.nextToken();
            String sideStr = tok.nextToken();
            OrderSide side = OrderSide.getByName(sideStr);
            String amountStr = tok.nextToken();
            double amount = Double.parseDouble(amountStr);
            String to = tok.nextToken();
            Currency toCurrency = Currency.getByName(to);
            String sep1 = tok.nextToken();
            if(sep1.equals("for")) {
                String from = tok.nextToken();
                Currency fromCurrency = Currency.getByName(from);
                PairDirection pd = PairDirection.get(fromCurrency, toCurrency);
                Pair pair = pd.m_pair;

                boolean forward = pd.isForward();
                if(!forward) {
                    side = side.opposite();
                }

                String sep2 = tok.nextToken();
                if(sep2.equals("@") || sep2.equals("at")) {
                    String priceStr = tok.nextToken();
                    System.out.println(" order: side=" + side + "; amount=" + amount + "; fromCurrency=" + fromCurrency +
                            "; toCurrency=" + toCurrency + "; priceStr=" + priceStr + "; pair=" + pd);
                    double limitPrice;
                    if (priceStr.equals("mkt")) { // place mkt
                        TopData top = Fetcher.fetchTop(s_exchange, pair);
                        limitPrice = side.mktPrice(top);
                    } else if (priceStr.startsWith("mkt-")) { // place mkt  minus x%
                        double perc = Double.parseDouble(priceStr.substring(4));
                        TopData top = Fetcher.fetchTop(s_exchange, pair);
                        double dif = top.m_ask - top.m_bid;
                        double offset = dif * perc/100;
                        limitPrice = side.isBuy() ? top.m_ask - offset : top.m_bid + offset;
                        limitPrice = s_exchange.roundPrice(limitPrice, pair);
                        System.out.println(" bid="+s_exchange.roundPriceStr(top.m_bid, pair) +
                                           ", ask="+s_exchange.roundPriceStr(top.m_bid, pair) +
                                           ", limit="+s_exchange.roundPriceStr(limitPrice, pair));
                    } else if (priceStr.equals("peg")) { // place peg
                        TopData top = Fetcher.fetchTop(s_exchange, pair);
                        double step = s_exchange.minOurPriceStep(pair);
                        limitPrice = side.pegPrice(top, step);
                    } else if (priceStr.equals("mid")) { // place mid
                        TopData top = Fetcher.fetchTop(s_exchange, pair);
                        limitPrice = top.getMid();
                        limitPrice = s_exchange.roundPrice(limitPrice, pair);
                    } else {
                        limitPrice = Double.parseDouble(priceStr);
                    }

                    if(!forward) {
                        amount = amount/limitPrice;
                    }

                    OrderData orderData = new OrderData(pair, side, limitPrice, amount);
                    System.out.println("confirm orderData=" + orderData);
                    if(confirm()) {
                        TopData top = Fetcher.fetchTop(s_exchange, pair);
                        if (confirmLmtPrice(limitPrice, top)) {
                            PlaceOrderData poData = Fetcher.placeOrder(orderData, s_exchange);
                            System.out.println("order place result: " + poData);
                        } else {
                            System.out.println(" limit price not confirmed");
                        }
                    } else {
                        System.out.println(" orderData not confirmed");
                    }

                    return;
                }
            }
        }
        System.err.println("invalid 'order' command: use followed format: order [buy|sell] 0.1 eur for btc @ [11.5|mkt|peg]");
    }

    private static boolean confirmLmtPrice(double limitPrice, TopData top) throws IOException {
        if( (limitPrice > top.m_ask) || (top.m_bid > limitPrice) ) { // ASK > BID
            System.err.println("order price " + limitPrice + " is out of market " + top + "; please confirm");
            if(confirm()) {
                System.err.println("order price confirmed");
                return true;
            }
            return false;
        }
        System.out.println("order price " + limitPrice + " is within the market " + top + "; OK");
        return true;
    }

    private static boolean confirm() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line = reader.readLine();
        if (line.equals("y")) {
            return true;
        }
        return false;
    }

}
