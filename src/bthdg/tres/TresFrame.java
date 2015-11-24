package bthdg.tres;

import bthdg.Log;
import bthdg.util.Snoozer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class TresFrame extends JFrame implements Runnable {
    private final Tres m_tres;
    private final Snoozer m_snoozer;
    private final JLabel m_label;
    private final TresCanvas m_canvas;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    public TresFrame(Tres tres) throws java.awt.HeadlessException {
        m_tres = tres;
        m_snoozer = new Snoozer(500) {
            @Override protected void wakeUp() {
                updateUI();
            }
        };
        m_canvas = new TresCanvas(m_tres);
        setTitle("Tres");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                m_snoozer.stop();
            }
        });
        JPanel panel1 = new JPanel(new BorderLayout());
        {
            panel1.setBackground(Color.BLACK);
            JPanel topPanel = new JPanel(new FlowLayout());
            {
                topPanel.setBackground(Color.DARK_GRAY);
                m_label = new JLabel("...");
                topPanel.add(m_label);
                topPanel.add(new JButton("B") {
                    @Override protected void fireActionPerformed(ActionEvent event) {
                        super.fireActionPerformed(event);
                        log("BTN.fireActionPerformed: " + event);
                    }
                });
//                topPanel.add(new JCheckBox("top", true));
                topPanel.add(new JCheckBox("ord", TresCanvas.m_paintOrders) {
                    @Override protected void fireItemStateChanged(ItemEvent event) {
                        super.fireItemStateChanged(event);
                        TresCanvas.m_paintOrders = (event.getStateChange() == ItemEvent.SELECTED);
                        m_canvas.repaint();
                    }
                });
                topPanel.add(new JCheckBox("od", TresCanvas.m_paintOrderData) {
                    @Override protected void fireItemStateChanged(ItemEvent event) {
                        super.fireItemStateChanged(event);
                        TresCanvas.m_paintOrderData = (event.getStateChange() == ItemEvent.SELECTED);
                        m_canvas.repaint();
                    }
                });
                topPanel.add(new JCheckBox("id", TresCanvas.m_paintOrderIds) {
                    @Override protected void fireItemStateChanged(ItemEvent event) {
                        super.fireItemStateChanged(event);
                        TresCanvas.m_paintOrderIds = (event.getStateChange() == ItemEvent.SELECTED);
                        m_canvas.repaint();
                    }
                });
//                topPanel.add(new JCheckBox("sym", TresCanvas.m_paintSym) {
//                    @Override protected void fireItemStateChanged(ItemEvent event) {
//                        super.fireItemStateChanged(event);
//                        TresCanvas.m_paintSym = (event.getStateChange() == ItemEvent.SELECTED);
//                        m_canvas.repaint();
//                    }
//                });
                {
                    JRadioButton auto = new JRadioButton("A") {
                        @Override protected void fireStateChanged() {
                            super.fireStateChanged();
                            TresExecutor.s_auto = isSelected();
                        }
                    };
                    JRadioButton manual = new JRadioButton("M");

                    ButtonGroup grp = new ButtonGroup();
                    grp.add(auto);
                    grp.add(manual);
                    auto.setSelected(true);

                    topPanel.add(auto);
                    topPanel.add(manual);
                }
                {
                    final JLabel level = new JLabel();
                    topPanel.add(new JSlider(-100, 100, 0) {
                        @Override protected void fireStateChanged() {
                            super.fireStateChanged();
                            int valueInt = getValue();
                            double value = valueInt / 100.0;
                            TresExecutor.s_manualDirection = value;
                            level.setText(Double.toString(value));
                        }
                    });
                    topPanel.add(level);
                }
            }
            JPanel controllersPanel = new JPanel(new FlowLayout());
            {
                controllersPanel.setBackground(Color.DARK_GRAY);
                tres.addControllers(controllersPanel, m_canvas);
            }
            JPanel panel2 = new JPanel(new BorderLayout());
            {
                panel2.setBackground(Color.BLACK);
                panel2.add(topPanel, BorderLayout.NORTH);
                panel2.add(controllersPanel, BorderLayout.CENTER);
            }
            panel1.add(panel2, BorderLayout.NORTH);
            panel1.add(m_canvas, BorderLayout.CENTER);
        }
        add(panel1);
        pack();
        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                removeComponentListener(this);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override public void run() {
                        toFront();
                    }
                });
            }
        });
    }

     public void fireUpdated() { m_snoozer.update(); }

    public void stop() {
        m_snoozer.stop();
        dispose();
    }

    private void updateUI() {
        SwingUtilities.invokeLater(this);
    }

    @Override public void run() {
        m_label.setText(m_tres.getState());
        m_canvas.repaint();
    }
}
