package bthdg;

import bthdg.exch.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

public class Console {
    static final Pair[] PAIRS = {Pair.LTC_BTC, Pair.BTC_USD, Pair.LTC_USD, Pair.BTC_EUR, Pair.LTC_EUR, Pair.EUR_USD};

    public static void main(String[] args) {
        System.out.println("Started.");
        printHelp();
        Fetcher.LOG_LOADING = true;
        Fetcher.MUTE_SOCKET_TIMEOUTS = true;

        try {
            Properties keys = BaseExch.loadKeys();
            Btce.init(keys);

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            while(true) {
                System.out.print(">");
                String line = reader.readLine();
                try {
                    boolean exit = process(line);
                    if(exit) {
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("error for command '"+line+"': " + e);
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("error: " + e);
            e.printStackTrace();
        }
    }

    private static boolean process(String line) throws Exception {
        if( line.equals("exit")) {
            return true;
        } else if( line.equals("account")) {
            doAccount();
        } else if( line.equals("orders")) {
            doOrders();
        } else if( line.equals("tops")) {
            doTops();
        } else if( line.startsWith("order ")) {
            doOrder(line);
        } else if( line.equals("help")) {
            printHelp();
        } else {
            System.err.println("unknown command '" + line + "'");
            printHelp();
        }
        return false;
    }

    private static void doOrders() throws Exception {
        OrdersData od = Fetcher.fetchOrders(Exchange.BTCE, null);
        System.out.println("ordersData=" + od);
        String error = od.m_erorr;
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

    private static void doAccount() throws Exception {
        AccountData account = Fetcher.fetchAccount(Exchange.BTCE);

        Map<Pair, TopData> tops = Fetcher.fetchTops(Exchange.BTCE, PAIRS);
        double valuate = account.evaluate(tops);

        System.out.println("account=" + account + "; valuate=" + valuate + " eur");
    }

    private static void printHelp() {
        System.out.println("supported commands:\n help; exit; account; tops; order");
    }

    private static void doTops() throws Exception {
        Map<Pair,TopData> tops = Fetcher.fetchTops(Exchange.BTCE, PAIRS);
        for(Map.Entry<Pair, TopData> entry:tops.entrySet()) {
            Pair pair = entry.getKey();
            TopData top = entry.getValue();
            System.out.println(" " + pair + " : " + top);
        }
    }

    private static void doOrder(String line) throws Exception {
        // order [buy|sell] 0.1 eur for btc @ [11.5|mkt|peg]
        StringTokenizer tok = new StringTokenizer(line.toLowerCase());
        int tokensNum = tok.countTokens();
        if(tokensNum == 8) {
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

                boolean forward = pd.m_forward;
                if(!forward) {
                    side = side.opposite();
                }

                String sep2 = tok.nextToken();
                if(sep2.equals("@")) {
                    String priceStr = tok.nextToken();
                    System.out.println(" order: side=" + side + "; amount=" + amount + "; fromCurrency=" + fromCurrency +
                            "; toCurrency=" + toCurrency + "; priceStr=" + priceStr + "; pair=" + pd);
                    if (priceStr.equals("mkt")) {
                        // place mkt
                        System.out.println("not implemented - place mkt");
                    } else if (priceStr.equals("peg")) {
                        // place peg
                        System.out.println("not implemented - place peg");
                    } else {
                        double limitPrice = Double.parseDouble(priceStr);
                        if(!forward) {
                            limitPrice = 1/limitPrice;
                            amount = amount/limitPrice;
                        }
                        OrderData orderData = new OrderData(pair, side, limitPrice, amount);
                        System.out.println("confirm orderData=" + orderData);
                        if(confirm()) {
                            Map<Pair, TopData> tops = Fetcher.fetchTops(Exchange.BTCE, PAIRS);
                            TopData top = tops.get(pair);
                            if (confirmLmtPrice(limitPrice, top)) {
                                PlaceOrderData poData = Fetcher.placeOrder(orderData, Exchange.BTCE);
                                System.out.println("order place result: " + poData);
                            } else {
                                System.out.println(" limit price not confirmed");
                            }
                        } else {
                            System.out.println(" orderData not confirmed");
                        }
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
        if(line.equals("y")) {
            return true;
        }
        return false;
    }
}
