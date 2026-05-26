package owca;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Vector;

/**
 * Analyst tab — Intelligence Lookup.
 *
 * Four sub-tabs, each posing a different analytical question:
 *   1. Identity Trace      — "Who knows the true identity behind code name X?"
 *   2. Co-occurrence       — "Which ops did agents X and Y both work on?"
 *   3. Damage Assessment   — "All assets connected to a compromised op."
 *   4. Nemesis Profile     — "Inators built and ops run against a given nemesis."
 *
 * Reads from V_ANALYST_PROFILE plus other tables. Read-only.
 */
public class AnalystPanel extends JPanel {

    private final JLabel lblStatus = new JLabel(" ");

    public AnalystPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JTabbedPane sub = new JTabbedPane();
        sub.addTab("Identity Trace",    new IdentityTracePanel());
        sub.addTab("Co-occurrence",     new CoOccurrencePanel());
        sub.addTab("Damage Assessment", new DamageAssessmentPanel());
        sub.addTab("Nemesis Profile",   new NemesisProfilePanel());

        add(sub,        BorderLayout.CENTER);
        add(lblStatus,  BorderLayout.SOUTH);
    }

    /* =========================================================================
     * Sub-panel 1 — Identity Trace
     * Pick an agent code-designation; show every personnel who knows their
     * true identity, along with when they were read in.
     * ========================================================================= */
    private class IdentityTracePanel extends JPanel {
        private final JComboBox<IdLabel> cmbAgent = new JComboBox<>();
        private final JTable tbl = readOnlyTable();
        private final JTextArea summary = new JTextArea(3, 60);

        IdentityTracePanel() {
            setLayout(new BorderLayout(6, 6));
            setBorder(new EmptyBorder(6, 6, 6, 6));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            top.setBorder(new TitledBorder("Choose agent"));
            top.add(new JLabel("Code name:")); top.add(cmbAgent);

            summary.setEditable(false);
            summary.setLineWrap(true);
            summary.setWrapStyleWord(true);
            summary.setBackground(getBackground());

            JPanel body = new JPanel(new BorderLayout(6, 6));
            JScrollPane sp = new JScrollPane(tbl);
            sp.setBorder(new TitledBorder("Personnel who know the true identity"));
            body.add(sp, BorderLayout.CENTER);

            JScrollPane sumSp = new JScrollPane(summary);
            sumSp.setBorder(new TitledBorder("Profile summary (V_ANALYST_PROFILE)"));
            body.add(sumSp, BorderLayout.SOUTH);

            add(top,  BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);

            cmbAgent.addActionListener(e -> runTrace());
            loadAgentList();
        }

        private void loadAgentList() {
            try (Connection c = DBConnection.open();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT agent_id, code_designation FROM AGENT ORDER BY code_designation")) {
                cmbAgent.removeAllItems();
                while (rs.next())
                    cmbAgent.addItem(new IdLabel(rs.getString(1), rs.getString(2)));
                if (cmbAgent.getItemCount() > 0) cmbAgent.setSelectedIndex(0);
            } catch (SQLException ex) {
                lblStatus.setText("Init error: " + ex.getMessage());
            }
        }

        private void runTrace() {
            IdLabel sel = (IdLabel) cmbAgent.getSelectedItem();
            if (sel == null) return;
            try (Connection c = DBConnection.open()) {
                // Personnel who know
                String q1 = "SELECT p.personnel_id, p.first_name || ' ' || p.last_name AS full_name, " +
                            "       p.role_type, ki.date_disclosed " +
                            "FROM KNOWS_IDENTITY ki JOIN PERSONNEL p ON ki.personnel_id = p.personnel_id " +
                            "WHERE ki.agent_id = ? ORDER BY ki.date_disclosed";
                try (PreparedStatement ps = c.prepareStatement(q1)) {
                    ps.setString(1, (String) sel.id);
                    try (ResultSet rs = ps.executeQuery()) {
                        tbl.setModel(modelFrom(rs, new String[]{"PersID","Name","Role","Disclosed"}));
                    }
                }
                // Profile summary
                String q2 = "SELECT species, is_alive, cover_burned, identity_known_by_count, " +
                            "       most_recent_op_date, exposed_to_compromised, paired_nemesis " +
                            "FROM V_ANALYST_PROFILE WHERE agent_id = ?";
                try (PreparedStatement ps = c.prepareStatement(q2)) {
                    ps.setString(1, (String) sel.id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            summary.setText(String.format(
                                "Species: %s   |   Alive: %s   |   Cover burned: %s%n" +
                                "%d people know true identity   |   most recent op start: %s%n" +
                                "Has been on a compromised op: %s   |   Paired nemesis: %s",
                                rs.getString(1), rs.getString(2), rs.getString(3),
                                rs.getInt(4), rs.getDate(5),
                                rs.getString(6),
                                rs.getString(7) == null ? "(none on file)" : rs.getString(7)));
                        } else summary.setText("(no profile data)");
                    }
                }
                lblStatus.setText("Identity trace: " + sel.label);
            } catch (SQLException ex) {
                lblStatus.setText("Trace error: " + ex.getMessage());
            }
        }
    }

    /* =========================================================================
     * Sub-panel 2 — Co-occurrence
     * Pick two agents → show every operation they were both on.
     * ========================================================================= */
    private class CoOccurrencePanel extends JPanel {
        private final JComboBox<IdLabel> cmbA = new JComboBox<>();
        private final JComboBox<IdLabel> cmbB = new JComboBox<>();
        private final JButton btnRun = new JButton("Find shared operations");
        private final JTable tbl = readOnlyTable();

        CoOccurrencePanel() {
            setLayout(new BorderLayout(6, 6));
            setBorder(new EmptyBorder(6, 6, 6, 6));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            top.setBorder(new TitledBorder("Pick two agents"));
            top.add(new JLabel("Agent 1:")); top.add(cmbA);
            top.add(new JLabel("Agent 2:")); top.add(cmbB);
            top.add(btnRun);

            JScrollPane sp = new JScrollPane(tbl);
            sp.setBorder(new TitledBorder("Operations both agents are assigned to"));

            add(top, BorderLayout.NORTH);
            add(sp,  BorderLayout.CENTER);

            btnRun.addActionListener(e -> run());
            loadAgents();
        }

        private void loadAgents() {
            try (Connection c = DBConnection.open();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT agent_id, code_designation FROM AGENT ORDER BY code_designation")) {
                cmbA.removeAllItems(); cmbB.removeAllItems();
                while (rs.next()) {
                    IdLabel item = new IdLabel(rs.getString(1), rs.getString(2));
                    cmbA.addItem(item); cmbB.addItem(item);
                }
                if (cmbB.getItemCount() > 1) cmbB.setSelectedIndex(1);
            } catch (SQLException ex) {
                lblStatus.setText("Init error: " + ex.getMessage());
            }
        }

        private void run() {
            IdLabel a = (IdLabel) cmbA.getSelectedItem();
            IdLabel b = (IdLabel) cmbB.getSelectedItem();
            if (a == null || b == null) return;
            if (a.id.equals(b.id)) {
                lblStatus.setText("Pick two different agents");
                return;
            }
            String sql =
                "SELECT o.operation_id, o.op_codename, o.status, o.start_date, " +
                "       (SELECT role_in_op FROM ASSIGNMENT WHERE operation_id=o.operation_id AND agent_id=?) AS role_a, " +
                "       (SELECT role_in_op FROM ASSIGNMENT WHERE operation_id=o.operation_id AND agent_id=?) AS role_b " +
                "FROM OPERATION o " +
                "WHERE EXISTS (SELECT 1 FROM ASSIGNMENT WHERE operation_id=o.operation_id AND agent_id=?) " +
                "  AND EXISTS (SELECT 1 FROM ASSIGNMENT WHERE operation_id=o.operation_id AND agent_id=?) " +
                "ORDER BY o.start_date";
            try (Connection c = DBConnection.open();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, (String) a.id);
                ps.setString(2, (String) b.id);
                ps.setString(3, (String) a.id);
                ps.setString(4, (String) b.id);
                try (ResultSet rs = ps.executeQuery()) {
                    tbl.setModel(modelFrom(rs, new String[]{
                        "OpID","Codename","Status","Start", a.label+" role", b.label+" role"}));
                    lblStatus.setText(tbl.getRowCount() + " shared operation(s)");
                }
            } catch (SQLException ex) {
                lblStatus.setText("Co-occurrence error: " + ex.getMessage());
            }
        }
    }

    /* =========================================================================
     * Sub-panel 3 — Damage Assessment
     * Pick a compromised operation → show every agent and gadget touched.
     * ========================================================================= */
    private class DamageAssessmentPanel extends JPanel {
        private final JComboBox<IdLabel> cmbOp = new JComboBox<>();
        private final JTable tbl = readOnlyTable();

        DamageAssessmentPanel() {
            setLayout(new BorderLayout(6, 6));
            setBorder(new EmptyBorder(6, 6, 6, 6));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            top.setBorder(new TitledBorder("Compromised operation"));
            top.add(cmbOp);

            JScrollPane sp = new JScrollPane(tbl);
            sp.setBorder(new TitledBorder("Assets connected to this op"));

            add(top, BorderLayout.NORTH);
            add(sp,  BorderLayout.CENTER);

            cmbOp.addActionListener(e -> runAssessment());
            loadCompromisedOps();
        }

        private void loadCompromisedOps() {
            try (Connection c = DBConnection.open();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT operation_id, op_codename FROM OPERATION " +
                     " WHERE status = 'COMPROMISED' ORDER BY start_date DESC")) {
                cmbOp.removeAllItems();
                while (rs.next())
                    cmbOp.addItem(new IdLabel(rs.getInt(1), rs.getString(2)));
                if (cmbOp.getItemCount() > 0) runAssessment();
                else lblStatus.setText("No compromised operations on record (good news!)");
            } catch (SQLException ex) {
                lblStatus.setText("Init error: " + ex.getMessage());
            }
        }

        private void runAssessment() {
            IdLabel sel = (IdLabel) cmbOp.getSelectedItem();
            if (sel == null) return;
            String sql =
                "SELECT 'AGENT'  AS asset_type, a.code_designation, " +
                "       asg.role_in_op AS detail, NULL AS extra " +
                "FROM ASSIGNMENT asg JOIN AGENT a ON asg.agent_id = a.agent_id " +
                "WHERE asg.operation_id = ? " +
                "UNION ALL " +
                "SELECT 'GADGET', g.gadget_name, g.category, " +
                "       TO_CHAR(ga.checked_out_date,'YYYY-MM-DD') " +
                "FROM GADGET_ASSIGNMENT ga JOIN GADGET g ON ga.gadget_id = g.gadget_id " +
                "WHERE ga.operation_id = ? " +
                "UNION ALL " +
                "SELECT 'LOCATION', l.city || ', ' || l.country, ol.location_role, NULL " +
                "FROM OPERATION_LOCATION ol JOIN LOCATION l ON ol.location_id = l.location_id " +
                "WHERE ol.operation_id = ? " +
                "ORDER BY 1, 2";
            try (Connection c = DBConnection.open();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, (Integer) sel.id);
                ps.setInt(2, (Integer) sel.id);
                ps.setInt(3, (Integer) sel.id);
                try (ResultSet rs = ps.executeQuery()) {
                    tbl.setModel(modelFrom(rs, new String[]{"Type","Name","Detail","Extra"}));
                    lblStatus.setText(tbl.getRowCount() + " assets touched");
                }
            } catch (SQLException ex) {
                lblStatus.setText("Assessment error: " + ex.getMessage());
            }
        }
    }

    /* =========================================================================
     * Sub-panel 4 — Nemesis Profile
     * Pick a nemesis → show their inators + ops run against them + paired agent.
     * ========================================================================= */
    private class NemesisProfilePanel extends JPanel {
        private final JComboBox<IdLabel> cmbNemesis = new JComboBox<>();
        private final JTable tblInators = readOnlyTable();
        private final JTable tblOps     = readOnlyTable();

        NemesisProfilePanel() {
            setLayout(new BorderLayout(6, 6));
            setBorder(new EmptyBorder(6, 6, 6, 6));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            top.setBorder(new TitledBorder("Nemesis"));
            top.add(cmbNemesis);

            JScrollPane spI = new JScrollPane(tblInators);
            spI.setBorder(new TitledBorder("Inators invented by this nemesis"));
            JScrollPane spO = new JScrollPane(tblOps);
            spO.setBorder(new TitledBorder("Operations run against this nemesis"));

            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spI, spO);
            split.setResizeWeight(0.45);

            add(top,   BorderLayout.NORTH);
            add(split, BorderLayout.CENTER);

            cmbNemesis.addActionListener(e -> runProfile());
            loadNemeses();
        }

        private void loadNemeses() {
            try (Connection c = DBConnection.open();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT nemesis_id, villain_name FROM NEMESIS ORDER BY villain_name")) {
                cmbNemesis.removeAllItems();
                while (rs.next())
                    cmbNemesis.addItem(new IdLabel(rs.getInt(1), rs.getString(2)));
                if (cmbNemesis.getItemCount() > 0) runProfile();
            } catch (SQLException ex) {
                lblStatus.setText("Init error: " + ex.getMessage());
            }
        }

        private void runProfile() {
            IdLabel sel = (IdLabel) cmbNemesis.getSelectedItem();
            if (sel == null) return;
            try (Connection c = DBConnection.open()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT inator_name, function_desc, weakness, current_status " +
                        "FROM INATOR WHERE nemesis_id = ? ORDER BY inator_name")) {
                    ps.setInt(1, (Integer) sel.id);
                    try (ResultSet rs = ps.executeQuery()) {
                        tblInators.setModel(modelFrom(rs,
                            new String[]{"Inator","Function","Weakness","Status"}));
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT op_codename, status, start_date, end_date, required_clearance " +
                        "FROM OPERATION WHERE primary_nemesis_id = ? ORDER BY start_date DESC")) {
                    ps.setInt(1, (Integer) sel.id);
                    try (ResultSet rs = ps.executeQuery()) {
                        tblOps.setModel(modelFrom(rs,
                            new String[]{"Codename","Status","Start","End","Clr"}));
                    }
                }
                lblStatus.setText("Profile: " + sel.label);
            } catch (SQLException ex) {
                lblStatus.setText("Profile error: " + ex.getMessage());
            }
        }
    }

    /* =========================================================================
     * Shared helpers
     * ========================================================================= */

    /** Generic id+label holder used by all combo boxes in this panel. */
    private static class IdLabel {
        final Object id; final String label;
        IdLabel(Object id, String label) { this.id = id; this.label = label; }
        @Override public String toString() { return label; }
    }

    private static DefaultTableModel modelFrom(ResultSet rs, String[] cols) throws SQLException {
        DefaultTableModel m = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        while (rs.next()) {
            Vector<Object> row = new Vector<>();
            for (int i = 1; i <= n; i++) row.add(rs.getObject(i));
            m.addRow(row);
        }
        return m;
    }

    private static JTable readOnlyTable() {
        JTable t = new JTable();
        t.setRowHeight(22);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        return t;
    }
}
