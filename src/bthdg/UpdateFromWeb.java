package bthdg;

import bthdg.exch.Exchange;
import bthdg.util.Utils;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

// http://api.bitcoincharts.com/v1/csv/
public class UpdateFromWeb extends DbReady {
    private static final String DELETE_TICKS_SQL = "DELETE FROM Ticks WHERE src = ? AND stamp = ?";

    public static void main(String[] args) {
        System.out.println("Started");
        long millis = Utils.logStartTimeMemory();

        updateFromWeb();

        System.out.println("done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));
    }

    private static void updateFromWeb() {
        goWithDb(new IDbRunnable() {
            public void run(Connection connection) throws SQLException {
                updateFromWeb(connection);
            }
        });
    }

    private static void updateFromWeb(Connection connection) throws SQLException {
        connection.setAutoCommit(false); // for fast inserts/updates
        PreparedStatement pStatement = connection.prepareStatement(DELETE_TICKS_SQL);
        try {
            for(Exchange exchange: Exchange.values()) {
                if (exchange.m_doWebUpdate) {
                    System.out.println("====== update for exchange " + exchange.m_name + " ======");
                    int iter = 1;
                    while (true) {
                        System.out.println("iteration " + iter);
                        long one = System.currentTimeMillis();
                        long timestamp = getMaxTimestamp(connection, exchange);
                        if (timestamp == 0) {
                            break;
                        }
                        long two = System.currentTimeMillis();
                        System.out.println("MaxTimestamp found in " + Utils.millisToDHMSStr(two - one));

                        System.out.println("deleting last ticks to avoid duplication");
                        pStatement.setInt(1, exchange.m_databaseId); // src
                        pStatement.setLong(2, timestamp); // stamp
                        int deleted = pStatement.executeUpdate();
                        System.out.println("deleted " + deleted);

                        int ticksInserted = updateFromWeb(connection, exchange, timestamp);
                        System.out.println("ticksInserted " + ticksInserted);

                        connection.commit();
                        if (ticksInserted < 500) {
                            break;
                        }
                        iter++;
                    }
                }
            }
        } finally {
            pStatement.close();
        }
    }

    private static int updateFromWeb(Connection connection, Exchange exchange, long timestamp) {
        int ticksInserted = 0;
        // from http://bitcoincharts.com/about/markets-api/
        try {
            // http://api.bitcoincharts.com/v1/trades.csv?symbol=SYMBOL[&start=UNIXTIME]
            long star = timestamp / 1000;
            URL url = new URL("http://api.bitcoincharts.com/v1/trades.csv?symbol=" + exchange.m_bitcoinchartsSymbol + "&start=" + star);
            System.out.println("loading from: " + url);
            URLConnection conn = url.openConnection();
            InputStream is = conn.getInputStream();
            // returns CSV: unixtime,price,amount
            BufferedInputStream bis = new BufferedInputStream(is);
            try {
                //int len = conn.getContentLength();
                int len = is.available();
                ticksInserted = readData(connection, exchange, len, bis, timestamp);
            } finally {
                bis.close();
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }
        return ticksInserted;
    }
}
