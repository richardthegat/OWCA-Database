package owca;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Handler tab — Mission Roster Builder.
 *
 * The handler picks filter criteria skills (multi-select), language +
 * fluency, alive flag, and current city to find candidate agents. They can
 * then multi-select agents from the result and assign them to an operation
 * in one click.
 *
 * Reads from V_HANDLER_ROSTER for the candidate list, writes to ASSIGNMENT
 * for the bulk add.
 */
public class HandlerPanel extends JPanel {

    /* ---------- combo-item holders so we can keep both label + ID ---------- */
    private static class IdLabel<T> {
        final T id; final String label;
        IdLabel(T id, String label) { this.id = id; this.label = label; }
        @Override public String toString() { return label; }
    }

    /* ---------- filter components ---------- */
    private final JList<IdLabel<Integer>>     lstSkills           = new JList<>();
    private final JComboBox<IdLabel<Integer>> cmbLanguage         = new JComboBox<>();
    private final JComboBox<String>           cmbFluency          = new JComboBox<>(
            new String[]{"(any)","BASIC","CONVERSATIONAL","FLUENT","NATIVE"});
    private final JCheckBox                   chkAliveOnly        = new JCheckBox("Alive only", true);
    private final JComboBox<IdLabel<Integer>> cmbCurrentLocation  = new JComboBox<>();
    private final JComboBox<String>           cmbSkillMatchMode   = new JComboBox<>(
            new String[]{"ANY of selected","ALL of selected"});
    private final JButton                     btnFindAgents       = new JButton("Find candidates");

    /* ---------- candidates table + assign controls ---------- */
    private final JTable                      tblCandidates       = makeMultiSelectTable();
    private final JComboBox<IdLabel<Integer>> cmbOperation        = new JComboBox<>();
    private final JComboBox<String>           cmbRole             = new JComboBox<>(
            new String[]{"LEAD","INFILTRATOR","SUPPORT","OVERWATCH","ANALYST","EXTRACTION"});
    private final JButton                     btnAssign           = new JButton("Assign to operation");

    private final JLabel                      lblStatus           = new JLabel(" ");

    public HandlerPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        add(buildFilterPanel(), BorderLayout.WEST);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(lblStatus,          BorderLayout.SOUTH);

        btnFindAgents.addActionListener(e -> findCandidates());
        btnAssign.addActionListener(e -> assignSelected());

        loadFilterChoices();
    }

    private JPanel buildFilterPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new TitledBorder("Filter agents"));
        p.setPreferredSize(new Dimension(300, 0));

        // Skills (multi-select with scroll pane)
        lstSkills.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        lstSkills.setVisibleRowCount(7);
        JScrollPane skillScroll = new JScrollPane(lstSkills);
        skillScroll.setBorder(new TitledBorder("Skills (Ctrl/Cmd-click)"));
        p.add(skillScroll);

        p.add(rowOf("Match: ", cmbSkillMatchMode));
        p.add(Box.createVerticalStrut(8));

        p.add(rowOf("Language: ", cmbLanguage));
        p.add(rowOf("Fluency: ",  cmbFluency));
        p.add(Box.createVerticalStrut(8));

        p.add(rowOf("Current city: ", cmbCurrentLocation));
        p.add(rowOf("",               chkAliveOnly));
        p.add(Box.createVerticalStrut(12));

        btnFindAgents.setAlignmentX(LEFT_ALIGNMENT);
        p.add(btnFindAgents);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel buildCenterPanel() {
        JPanel p = new JPanel(new BorderLayout(6, 6));

        JScrollPane sp = new JScrollPane(tblCandidates);
        sp.setBorder(new TitledBorder("Candidate agents (V_HANDLER_ROSTER) — multi-select to assign"));
        p.add(sp, BorderLayout.CENTER);

        // Assignment strip at the bottom
        JPanel assignStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        assignStrip.setBorder(new TitledBorder("Assignment"));
        assignStrip.add(new JLabel("Operation:")); assignStrip.add(cmbOperation);
        assignStrip.add(new JLabel("Role:"));      assignStrip.add(cmbRole);
        assignStrip.add(btnAssign);
        p.add(assignStrip, BorderLayout.SOUTH);
        return p;
    }

    /** Helper: a horizontal row with label + component, left-aligned. */
    private static JPanel rowOf(String label, JComponent c) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setAlignmentX(LEFT_ALIGNMENT);
        if (!label.isEmpty()) row.add(new JLabel(label));
        row.add(c);
        return row;
    }

    /** Pull skills, languages, locations, ops from the DB to populate filter widgets. */
    private void loadFilterChoices() {
        try (Connection conn = DBConnection.open();
             Statement st = conn.createStatement()) {

            DefaultListModel<IdLabel<Integer>> sm = new DefaultListModel<>();
            try (ResultSet rs = st.executeQuery(
                    "SELECT skill_id, skill_name FROM SKILL ORDER BY skill_name")) {
                while (rs.next()) sm.addElement(new IdLabel<>(rs.getInt(1), rs.getString(2)));
            }
            lstSkills.setModel(sm);

            cmbLanguage.removeAllItems();
            cmbLanguage.addItem(new IdLabel<>(null, "(any language)"));
            try (ResultSet rs = st.executeQuery(
                    "SELECT language_id, language_name FROM LANGUAGE ORDER BY language_name")) {
                while (rs.next())
                    cmbLanguage.addItem(new IdLabel<>(rs.getInt(1), rs.getString(2)));
            }

            cmbCurrentLocation.removeAllItems();
            cmbCurrentLocation.addItem(new IdLabel<>(null, "(anywhere)"));
            try (ResultSet rs = st.executeQuery(
                    "SELECT location_id, city || ', ' || country FROM LOCATION ORDER BY city")) {
                while (rs.next())
                    cmbCurrentLocation.addItem(new IdLabel<>(rs.getInt(1), rs.getString(2)));
            }

            cmbOperation.removeAllItems();
            try (ResultSet rs = st.executeQuery(
                    "SELECT operation_id, op_codename FROM OPERATION " +
                    " WHERE status IN ('PLANNING','ACTIVE') ORDER BY start_date DESC")) {
                while (rs.next())
                    cmbOperation.addItem(new IdLabel<>(rs.getInt(1), rs.getString(2)));
            }

            cmbFluency.setSelectedItem("FLUENT");

        } catch (SQLException ex) {
            lblStatus.setText("Init error: " + ex.getMessage());
        }
    }

    /* ----------------- Find-candidates query ----------------- */
    private void findCandidates() {
        List<IdLabel<Integer>> selSkills = lstSkills.getSelectedValuesList();
        IdLabel<Integer> selLang = (IdLabel<Integer>) cmbLanguage.getSelectedItem();
        String selFlu = (String) cmbFluency.getSelectedItem();
        boolean alive = chkAliveOnly.isSelected();
        IdLabel<Integer> selLoc = (IdLabel<Integer>) cmbCurrentLocation.getSelectedItem();
        String matchMode = (String) cmbSkillMatchMode.getSelectedItem();

        StringBuilder sql = new StringBuilder(
            "SELECT agent_id, code_designation, species, is_alive, clearance_level, " +
            "       handler_name, current_city, current_country, skills, languages, total_ops " +
            "FROM V_HANDLER_ROSTER WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();

        if (alive) sql.append("AND is_alive = 'Y' ");

        if (selLoc != null && selLoc.id != null) {
            sql.append("AND agent_id IN (SELECT agent_id FROM AGENT WHERE current_location_id = ?) ");
            params.add(selLoc.id);
        }

        if (selLang != null && selLang.id != null) {
            sql.append("AND agent_id IN (SELECT agent_id FROM AGENT_LANGUAGE WHERE language_id = ? ");
            params.add(selLang.id);
            if (selFlu != null && !"(any)".equals(selFlu)) {
                sql.append("AND fluency_level = ? ");
                params.add(selFlu);
            }
            sql.append(") ");
        }

        if (!selSkills.isEmpty()) {
            if ("ALL of selected".equals(matchMode)) {
                for (IdLabel<Integer> s : selSkills) {
                    sql.append("AND EXISTS (SELECT 1 FROM AGENT_SKILL WHERE agent_id = V_HANDLER_ROSTER.agent_id AND skill_id = ?) ");
                    params.add(s.id);
                }
            } else {
                sql.append("AND agent_id IN (SELECT agent_id FROM AGENT_SKILL WHERE skill_id IN (");
                for (int i = 0; i < selSkills.size(); i++) {
                    sql.append(i == 0 ? "?" : ",?");
                    params.add(selSkills.get(i).id);
                }
                sql.append(")) ");
            }
        }

        sql.append("ORDER BY clearance_level DESC, code_designation");

        try (Connection conn = DBConnection.open();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                String[] cols = {"AgentID","CodeName","Species","Alive","Clr",
                                 "Handler","City","Country","Skills","Languages","Ops"};
                tblCandidates.setModel(modelFrom(rs, cols));
                lblStatus.setText(tblCandidates.getRowCount() + " agents match");
            }
        } catch (SQLException ex) {
            lblStatus.setText("Search error: " + ex.getMessage());
        }
    }

    /* ----------------- Assign-selected (write op) ----------------- */
    private void assignSelected() {
        int[] rows = tblCandidates.getSelectedRows();
        if (rows.length == 0) {
            lblStatus.setText("Select one or more agents first");
            return;
        }
        IdLabel<Integer> op = (IdLabel<Integer>) cmbOperation.getSelectedItem();
        String role = (String) cmbRole.getSelectedItem();
        if (op == null || role == null) {
            lblStatus.setText("Pick an operation and a role");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Assign " + rows.length + " agent(s) to " + op.label + " as " + role + "?",
                "Confirm assignment", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) return;

        String sql = "INSERT INTO ASSIGNMENT (operation_id, agent_id, op_codename_in_op, role_in_op, assigned_date) " +
                     "VALUES (?, ?, ?, ?, SYSDATE)";

        Connection conn = null;
        try {
            conn = DBConnection.open();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int success = 0, dupes = 0;
                for (int viewRow : rows) {
                    String agentId  = (String) tblCandidates.getValueAt(viewRow, 0);
                    String codeName = (String) tblCandidates.getValueAt(viewRow, 1);
                    String opCode   = op.label.replaceAll("\\s+","") + "-" +
                                      codeName.replace("Agent ", "");
                    ps.setInt(1, op.id);
                    ps.setString(2, agentId);
                    ps.setString(3, opCode);
                    ps.setString(4, role);
                    try {
                        ps.executeUpdate();
                        success++;
                    } catch (SQLException dup) {
                        // Likely PK violation — agent already on this op
                        dupes++;
                    }
                }
                conn.commit();
                lblStatus.setText("Assigned " + success + " agent(s); skipped " + dupes + " duplicate(s)");
            }
        } catch (SQLException ex) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ignored) {}
            lblStatus.setText("Assignment failed: " + ex.getMessage());
        } finally {
            try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
        }
    }

    /* ----------------- shared helpers ----------------- */

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

    private static JTable makeMultiSelectTable() {
        JTable t = new JTable();
        t.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        t.setRowHeight(22);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        return t;
    }
}
