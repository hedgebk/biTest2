package bthdg.util;

import java.io.*;
import java.util.ArrayList;

public class LineReader {
    private final InputStream m_is;
    private final BufferedReader m_bis;
    private final ArrayList<String> m_buffer = new ArrayList<String>();
    public int m_linesReaded = 0;

    public LineReader(String logFile) throws FileNotFoundException {
        this(new File(logFile));
    }

    public LineReader(File file) throws FileNotFoundException {
        this(new FileInputStream(file));
    }

    public LineReader(InputStream is) {
        m_is = is;
        m_bis = new BufferedReader(new InputStreamReader(is));
    }

    public void close() throws IOException {
        m_is.close();
        m_bis.close();
    }

    public synchronized String getLine(int index) throws IOException {
        if (index < m_buffer.size()) {
            return m_buffer.get(index);
        }
        return readLine();
    }

    private String readLine() throws IOException {
        String line = m_bis.readLine();
        if (line != null) {
            m_buffer.add(line);
            m_linesReaded++;
        }
        return line;
    }
}
