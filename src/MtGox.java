public class MtGox {
}

//    http://data.mtgox.com/api/2/BTCUSD/money/ticker_fast
//{"result":"success",
// "data":{"last_local":{"value":"933.79999","value_int":"93379999","display":"$933.80","display_short":"$933.80","currency":"USD"},
//         "last":{"value":"933.79999","value_int":"93379999","display":"$933.80","display_short":"$933.80","currency":"USD"},
//         "last_orig":{"value":"672.50001","value_int":"67250001","display":"672.50\u00a0\u20ac","display_short":"672.50\u00a0\u20ac","currency":"EUR"},
//         "last_all":{"value":"907.74051","value_int":"90774051","display":"$907.74","display_short":"$907.74","currency":"USD"},
//         "buy":{"value":"930.43000","value_int":"93043000","display":"$930.43","display_short":"$930.43","currency":"USD"},
//         "sell":{"value":"933.79500","value_int":"93379500","display":"$933.80","display_short":"$933.80","currency":"USD"},
//    "now":"1391514893322685"}}


////////////////////////////////////////////////////////
// https://en.bitcoin.it/wiki/MtGox/API/HTTP/v1#Your_open_orders
//
// BOOK
//1) MtGox Polling API V2. You call the getFullDepth to download the full orderbook. Then you call getPartialDepth for updates, replacing all the orders in your master order book. This method is not very efficient and you can only poll at a throttled pace.
//https://github.com/timmolter/XChange/blob/develop/xchange-examples/src/main/java/com/xeiam/xchange/examples/mtgox/v2/service/marketdata/polling/MtGoxOrderBookChartDemo.java
//Here's a demo from the XChange open source project for simply polling for the partial order book and creating an orderbook chart.
//
//2) MtGox Polling API V2 + streaming. You call the getFullDepth to download the full orderbook. Then you use the streaming API to receive depth events and update your master order book accordingly. Depth updates with an amount of zero mean that the orders were cancelled or filled, i.e. they don't exist anymore and you can remove them.
//https://github.com/timmolter/XChange/blob/develop/xchange-examples/src/main/java/com/xeiam/xchange/examples/mtgox/v2/service/marketdata/streaming/MtGoxWebSocketSyncronizedOrderBookDemo.java
//Here's a demo from the XChange open source project.
