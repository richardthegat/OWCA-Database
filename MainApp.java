package owca;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Application entry point. Launches the main frame on the Swing event-dispatch
 * thread (the only thread Swing components should be touched from).
 */
public class MainApp {

    public static void main(String[] args) {
        // Try to use the system look-and-feel so the app feels native on
        // macOS / Windows. Fall back silently if anything goes wrong.
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }

        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
