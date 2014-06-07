package bthdg.exch;

public class OkCoin extends BaseExch {
    @Override public String getNextNonce() {
        return null;
    }

    @Override protected String getCryproAlgo() {
        return null;
    }

    @Override protected String getSecret() {
        return null;
    }

    @Override protected String getApiEndpoint() {
        return null;
    }

    @Override public double roundPrice(double price, Pair pair) {
        return 0;
    }

    @Override public String roundPriceStr(double price, Pair pair) {
        return null;
    }

    @Override public double roundAmount(double amount, Pair pair) {
        return 0;
    }

    @Override public String roundAmountStr(double amount, Pair pair) {
        return null;
    }
}
