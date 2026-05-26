package owca;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Vector;

/**
 * Director tab — Strategic Operations Dashboard.
 *
 * Top filter strip (status / min clearance / codename search / Refresh) drives
 * a parameterized query over V_DIRECTOR_OVERVIEW. Selecting an operation row
 * loads roster / locations / gadgets for that op into the bottom sub-tabs.
 *
 * Read-only view: directors see strategic data, they do not edit assignments.
 */
public class DirectorPanel extends JPanel {

    private final JComboBox<String> cmbStatus       = new JComboBox<>(new String[]{
            "ALL","PLANNING","ACTIVE","CLOSED","COMPROMISED","ABORTED"});
    private final JSpinner          spnMinClearance = new JSpinner(new SpinnerNumberModel(1, 1, 5, 1));
    private final JTextField        txtSearchCodename = new JTextField(15);
    private final JButton           btnRefresh      = new JButton("Refresh");

    private final JTable tblOperations = makeReadOnlyTable();
    private final JTable tblRoster     = makeReadOnlyTable();
    private final JTable tblLocations  = makeReadOnlyTable();
    private final JTable tblGadgets    = makeReadOnlyTable();

    private final JLabel lblStatus = new JLabel(" ");

    public DirectorPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        add(buildFilterStrip(), BorderLayout.NORTH);
        add(buildSplit(),       BorderLayout.CENTER);
        add(lblStatus,          BorderLayout.SOUTH);

        // Action wiring
        btnRefresh.addActionListener(e -> loadOperations());
        txtSearchCodename.addActionListener(e -> loadOperations());     // Enter in textbox
        cmbStatus.addActionListener(e -> loadOperations());
        spnMinClearance.addChangeListener(e -> loadOperations());

        tblOperations.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = tblOperations.getSelectedRow();
            if (row < 0) return;
            int opId = ((Number) tblOperations.getValueAt(row, 0)).intValue();
            loadRoster(opId);
            loadLocations(opId);
            loadGadgets(opId);
        });

        loadOperations();
    }

    private JPanel buildFilterStrip() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("Strategic filter"));
        p.add(new JLabel("Status:"));        p.add(cmbStatus);
        p.add(new JLabel("Min clearance:")); p.add(spnMinClearance);
        p.add(new JLabel("Codename like:")); p.add(txtSearchCodename);
        p.add(btnRefresh);
        return p;
    }

    private JSplitPane buildSplit() {
        JScrollPane top = new JScrollPane(tblOperations);
        top.setBorder(new TitledBorder("Operations (V_DIRECTOR_OVERVIEW)"));

        JTabbedPane drill = new JTabbedPane();
        drill.addTab("Roster",    new JScrollPane(tblRoster));
        drill.addTab("Locations", new JScrollPane(tblLocations));
        drill.addTab("Gadgets",   new JScrollPane(tblGadgets));

        JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, drill);
        sp.setResizeWeight(0.55);
        return sp;
    }

    /** Build the parameterized query against V_DIRECTOR_OVERVIEW from the filters. */
    private void loadOperations() {
        String status = (String) cmbStatus.getSelectedItem();
        int    minClr = (Integer) spnMinClearance.getValue();
        String search = txtSearchCodename.getText().trim();

        StringBuilder sql = new StringBuilder(
            "SELECT operation_id, op_codename, status, start_date, end_date, " +
            "       required_clearance, lead_handler, primary_nemesis, " +
            "       nemesis_level, roster_size " +
            "FROM V_DIRECTOR_OVERVIEW WHERE required_clearance >= ? "
        );
        if (status != null && !"ALL".equals(status)) sql.append("AND status = ? ");
        if (!search.isEmpty())                       sql.append("AND UPPER(op_codename) LIKE ? ");
        sql.append("ORDER BY start_date DESC");

        try (Connection conn = DBConnection.open();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int idx = 1;
            ps.setInt(idx++, minClr);
            if (status != null && !"ALL".equals(status)) ps.setString(idx++, status);
            if (!search.isEmpty()) ps.setString(idx++, "%" + search.toUpperCase() + "%");

            try (ResultSet rs = ps.executeQuery()) {
                String[] cols = {"OpID","Codename","Status","Start","End",
                                 "Clr","Lead Handler","Primary Nemesis","NLvl","Roster"};
                tblOperations.setModel(buildModel(rs, cols));
                lblStatus.setText(tblOperations.getRowCount() + " operations match");
            }
        } catch (SQLException ex) {
            lblStatus.setText("Error: " + ex.getMessage());
        }
    }

    private void loadRoster(int opId) {
        runIntoTable(tblRoster,
            "SELECT a.agent_id, a.code_designation, a.species, " +
            "       asg.op_codename_in_op, asg.role_in_op, " +
            "       a.is_alive, a.clearance_level " +
            "FROM ASSIGNMENT asg JOIN AGENT a ON asg.agent_id = a.agent_id " +
            "WHERE asg.operation_id = ? ORDER BY asg.role_in_op, a.code_designation",
            new Object[]{opId},
            new String[]{"AgentID","CodeName","Species","CodeInOp","Role","Alive","Clr"});
    }

    private void loadLocations(int opId) {
        runIntoTable(tblLocations,
            "SELECT l.city, l.country, ol.location_role " +
            "FROM OPERATION_LOCATION ol JOIN LOCATION l ON ol.location_id = l.location_id " +
            "WHERE ol.operation_id = ? ORDER BY ol.location_role",
            new Object[]{opId},
            new String[]{"City","Country","Role"});
    }

    private void loadGadgets(int opId) {
        runIntoTable(tblGadgets,
            "SELECT g.gadget_name, g.category, a.code_designation, " +
            "       ga.checked_out_date, ga.returned_date " +
            "FROM GADGET_ASSIGNMENT ga " +
            "  JOIN GADGET g ON ga.gadget_id = g.gadget_id " +
            "  JOIN AGENT  a ON ga.agent_id  = a.agent_id " +
            "WHERE ga.operation_id = ? ORDER BY ga.checked_out_date",
            new Object[]{opId},
            new String[]{"Gadget","Category","Issued To","Checked Out","Returned"});
    }

    /* ---------- helpers shared by all loaders in this panel ---------- */

    private void runIntoTable(JTable target, String sql, Object[] params, String[] cols) {
        try (Connection conn = DBConnection.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                target.setModel(buildModel(rs, cols));
            }
        } catch (SQLException ex) {
            lblStatus.setText("Drill-down error: " + ex.getMessage());
        }
    }

    private static DefaultTableModel buildModel(ResultSet rs, String[] cols) throws SQLException {
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

    private static JTable makeReadOnlyTable() {
        JTable t = new JTable();
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        t.setRowHeight(22);
        return t;
    }
}
