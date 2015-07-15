package bthdg.ws;

import bthdg.exch.Exchange;

import java.util.Properties;

public class WsFactory {
    public static IWs get(String exchName, Properties keys) {
        Exchange exchange = Exchange.getExchange(exchName);
        switch (exchange) {
            case HUOBI:
                return HuobiWs.create(keys);
            case OKCOIN:
                return OkCoinWs.create(keys);
            default:
                throw new RuntimeException("not supported exchange: " + exchName);
        }
    }
}
