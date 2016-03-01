package bthdg.tres.opt;

public class OptimizeFieldConfig {
    public final OptimizeField m_field;
    public final double m_min;
    public final double m_max;
    public final double m_start;
    public final double m_multiplier;

    public OptimizeFieldConfig(OptimizeField field, double min, double max, double start) {
        m_field = field;
        double minimum = Math.min(min, Math.min(max, start));
        double log10 = Math.log10(minimum);
        int pow = (int) log10;
        m_multiplier = Math.pow(10, pow);
        m_min = min;
        m_max = max;
        m_start = start;
    }
}
