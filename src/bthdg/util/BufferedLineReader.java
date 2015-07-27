package bthdg.util;

import java.io.IOException;

public class BufferedLineReader {
    private final LineReader m_reader;
    private int m_start = 0;
    private int m_index = 0;

    public BufferedLineReader(LineReader reader) {
        m_reader = reader;
    }

    public void close() throws IOException {
        m_reader.close();
    }

    public String getLine() throws IOException {
        String line = m_reader.getLine(m_index);
        if (line != null) {
            m_index++;
        }
        return line;
    }

    public void removeLine() {
        m_start++;
        m_index = m_start;
    }
}
