package owca;

import javax.swing.*;
import java.awt.*;

/**
 * Top-level frame for the OWCA application.
 *
 * Holds a JTabbedPane with three role tabs:
 *   - Director  (strategic overview)
 *   - Handler   (mission roster builder)
 *   - Analyst   (intelligence lookup)
 *
 * Each tab is a self-contained JPanel subclass that builds its own UI.
 * Switching tabs simulates switching roles — there is no login, per the
 * project spec.
 */
public class MainFrame extends JFrame {

    public MainFrame() {
        super("OWCA — Organization Without a Cool Attitude");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 750);
        setLocationRelativeTo(null);   // centre on screen

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Director",  new DirectorPanel());
        tabs.addTab("Handler",   new HandlerPanel());
        tabs.addTab("Analyst",   new AnalystPanel());

        // Slightly larger tab font for visual hierarchy
        tabs.setFont(tabs.getFont().deriveFont(Font.PLAIN, 14f));

        setContentPane(tabs);
    }
}
