package bthdg.tres.opt;

public class OptimizeFieldConfig {
    public final OptimizeField m_field;
    public final double m_min;
    public final double m_max;
    public final double m_start;

    public OptimizeFieldConfig(OptimizeField field, double min, double max, double start) {
        m_field = field;
        m_min = min;
        m_max = max;
        m_start = start;
    }
}
