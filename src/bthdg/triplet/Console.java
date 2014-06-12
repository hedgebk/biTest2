package bthdg.triplet;

import bthdg.*;
import bthdg.exch.*;
import bthdg.exch.Currency;
import bthdg.util.ConsoleReader;
import bthdg.util.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class Console {
    private static Exchange s_exchange = Exchange.BTCE;

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
        String exchName = line.substring(2);
        System.err.println("requested change to exchange "+exchName);
        Exchange[] exchanges = Exchange.values();
        for(Exchange exchange: exchanges) {
            String name = exchange.name();
            if( name.equalsIgnoreCase(exchName) ) {
                s_exchange = exchange;
                System.err.println(" current exchange is changed successfully to "+name);
                return;
            }
        }
        System.err.println("exchange '"+exchName+"' not found. supported: " + Arrays.asList(exchanges));
    }

    private static void doOrders() throws Exception {
        OrdersData od = Fetcher.fetchOrders(s_exchange, null);
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

        String s = "          ";
        for (Currency inCurrency : Currency.values()) {
            double valuate = account.evaluate(tops, inCurrency);
            valuateMap.put(inCurrency, valuate);
            s += Utils.padLeft(inCurrency.toString(), 32);
        }
        System.out.println(s);

        for (Currency currencyIn : Currency.values()) {
            double inValue = account.getAllValue(currencyIn);
            String str = Utils.padLeft(Utils.X_YYYYY.format(inValue), 9) + " " + currencyIn + " ";
            Double rate = FundMap.s_distributeRatio.get(currencyIn);
            for (Currency currencyOut : Currency.values()) {
                double converted = (currencyIn == currencyOut)
                        ? inValue :
                        tops.convert(currencyIn, currencyOut, inValue);
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
        for (Currency outCurrency : Currency.values()) {
            double valuate = valuateMap.get(outCurrency);
            s +=  Utils.padRight(Utils.padLeft(Utils.X_YYYYY.format(valuate), 9), 29) + " | ";
        }
        System.out.println(s);
        FundMap.test(account, tops);
    }

    private static void printHelp() {
        System.out.println("supported commands:\n help; exit; account; tops; order; cancel");
    }

    private static void doTops() throws Exception {
        if(s_exchange.supportsMultiplePairsRequest()) {
            DeepsData deeps = Fetcher.fetchDeeps(s_exchange, s_exchange.supportedPairs());
            TopsData tops = deeps.getTopsDataAdapter();
            printTops(tops);

            tops = Fetcher.fetchTops(s_exchange, s_exchange.supportedPairs());
            printTops(tops);
        } else {
            System.out.println("MultiplePairsRequest not supported by " + s_exchange);
        }
    }

    private static void doTop(String line) throws Exception {
        String pairName = line.substring(4);
        Pair[] pairs = s_exchange.supportedPairs();
        for (Pair pair : pairs) {
            if (pair.name().equalsIgnoreCase(pairName)) {
                TopData top = Fetcher.fetchTop(s_exchange, pair);
                System.out.println(" " + pair + " : " + top);
                return;
            }
        }
        System.out.println("pair '" + pairName + "' not supported by " + s_exchange + ". supported pairs: " + Arrays.asList(pairs));
    }

    private static void printTops(TopsData tops) {
        for (Map.Entry<Pair, TopData> entry : tops.entrySet()) {
            Pair pair = entry.getKey();
            TopData top = entry.getValue();
            System.out.println(" " + pair + " : " + top);
        }
    }

    private static void doCancel(String line) throws Exception {
        // cancel 12345
        StringTokenizer tok = new StringTokenizer(line.toLowerCase());
        int tokensNum = tok.countTokens();
        if (tokensNum == 2) {
            String t1 = tok.nextToken();
            String orderId = tok.nextToken();
            CancelOrderData coData = Fetcher.calcelOrder(s_exchange, orderId);
            System.out.println("cancel order '" + orderId + "' result: " + coData);
            return;
        }
        System.err.println("invalid 'cancel' command: use followed format: cancel orderId");
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
                    } else if (priceStr.equals("peg")) { // place peg
                        TopData top = Fetcher.fetchTop(s_exchange, pair);
                        double step = s_exchange.minOurPriceStep(pair);
                        limitPrice = side.pegPrice(top, step);
                    } else if (priceStr.equals("mid")) { // place mid
                        TopData top = Fetcher.fetchTop(s_exchange, pair);
                        limitPrice = top.getMid();
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
