
import javax.sql.RowSet;
import javax.sql.rowset.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.List;

public class ViewDB {

    public static void main(String[] args) {
        EventQueue.invokeLater(()-> {
            var frame = new ViewDBFrame();
            frame.setTitle("ViewDB");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }
}


class ViewDBFrame extends JFrame {

    private JButton previousButton;
    private JButton nextButton;
    private JButton deleteButton;
    private JButton saveButton;
    private DataPanel dataPanel;
    private Component scrollPane;
    private JComboBox<String> tableNames;
    private Properties props;
    private CachedRowSet crs;
    private Connection conn;

    public ViewDBFrame() {
        tableNames = new JComboBox<>();
        try {
            readDataBaseProperties();
            conn = getConnection();
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet mrs = meta.getTables(null, null, null, new String[]{"TABLE"})) {
                while (mrs.next())
                    tableNames.addItem(mrs.getString(3));
            }
        } catch (SQLException e) {
            for (Throwable t : e) {
                t.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        tableNames.addActionListener(
                event -> showTable((String) tableNames.getSelectedItem(), conn));
        add(tableNames, BorderLayout.NORTH);
        addWindowListener(new WindowAdapter() {

            public void windowClosing(WindowEvent e) {
                try {
                    if (conn != null) conn.close();
                } catch (SQLException ex) {
                    for (Throwable t : ex)
                        t.printStackTrace();
                }
            }
        });

        var buttonPanel = new JPanel();
        add(buttonPanel, BorderLayout.SOUTH);

        previousButton = new JButton("Poprzedni");
        previousButton.addActionListener(event -> showPreviousRow());
        buttonPanel.add(previousButton);

        nextButton = new JButton("Następny");
        nextButton.addActionListener(event -> showNextRow());
        buttonPanel.add(nextButton);

        deleteButton = new JButton("Usuń");
        deleteButton.addActionListener(event -> deleteRow());
        buttonPanel.add(deleteButton);

        saveButton = new JButton("Zapisz");
        saveButton.addActionListener(event -> saveChanges());
        buttonPanel.add(saveButton);

        if (tableNames.getItemCount() > 0) {
            showTable(tableNames.getItemAt(0), conn);
        }
    }

    public void showTable(String tableName, Connection conn) {
        try (Statement stat = conn.createStatement();
             ResultSet result = stat.executeQuery("SELECT * FROM " + tableName)) {

            RowSetFactory factory = RowSetProvider.newFactory();
            crs = factory.createCachedRowSet();
            crs.setTableName(tableName);
            crs.populate(result);

            if (scrollPane != null) remove(scrollPane);
            dataPanel = new DataPanel(crs);
            scrollPane = new JScrollPane(dataPanel);
            add(scrollPane, BorderLayout.CENTER);
            pack();
            showNextRow();
        } catch (SQLException ex) {
            for (Throwable t : ex) {
                t.printStackTrace();
            }
        }
    }

    public void showPreviousRow() {
        try {
            if (crs == null || crs.isFirst()) return;
            crs.previous();
            dataPanel.showRow(crs);
        } catch (SQLException ex) {
            for (Throwable t : ex) {
                t.printStackTrace();
            }
        }
    }

    public void showNextRow() {
        try {
            if (crs == null || crs.isLast()) return;
        } catch (SQLException ex) {
            for (Throwable t : ex) {
                t.printStackTrace();
            }
        }
    }

    public void deleteRow() {
        if (crs == null) return;
        new SwingWorker<Void, Void>() {
            public Void doInBackground() throws SQLException {
                crs.deleteRow();
                crs.acceptChanges(conn);
                if (crs.isAfterLast())
                    if (!crs.last()) crs = null;
                return null;
            }

            public void done() {
                dataPanel.showRow(crs);
            }
        }.execute();
    }

    public void saveChanges(){
        if (crs==null) return;
        new SwingWorker<Void, Void>()
        {
            public Void doInBackground() throws SQLException {
                dataPanel.setRow(crs);
                crs.acceptChanges(conn);
                return null;
            }
        } .execute();
    }

    private void readDataBaseProperties() throws IOException {
        props = new Properties();
        try (InputStream in = Files.newInputStream(Paths.get("database.properties"))) {
            props.load(in);
        }
        String drivers = props.getProperty("jdbc.drivers");
        if (drivers != null) System.setProperty("jdbc.drivers", drivers);
    }

    private Connection getConnection() throws SQLException {
        String url = props.getProperty("jdbc.url");
        String username = props.getProperty("jdbc.username");
        String password = props.getProperty("jdbc.password");

        return DriverManager.getConnection(url, username, password);
    }

}

class DataPanel extends JPanel {

    private List<JTextField> fields;

    public DataPanel(RowSet rs) throws SQLException {
        fields = new ArrayList<>();
        setLayout(new GridBagLayout());
        var gbc = new GridBagConstraints();
        gbc.gridwidth = 1;
        gbc.gridheight = 1;

        ResultSetMetaData rsmd = rs.getMetaData();
        for (int i = 1; i <= rsmd.getColumnCount(); i++){
            gbc.gridy = i -1;

            String columnName = rsmd.getColumnLabel(i);
            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.EAST;
            add(new JLabel(columnName), gbc);

            int columnWidth = rsmd.getColumnDisplaySize(i);
            var tb = new JTextField(columnWidth);
            if (!rsmd.getColumnClassName(i).equals("java.lang.String")){
                tb.setEditable(false);
            }
            fields.add(tb);
            gbc.gridx = 1;
            gbc.anchor = GridBagConstraints.WEST;
            add(tb, gbc);
        }
    }

    public void showRow(ResultSet rs){
        try{
            if (rs==null) return;
            for (int i = 1; i<= fields.size(); i++){
                String field = rs == null ? "" : rs.getString(i);
                JTextField tb = fields.get(i-1);
                tb.setText(field);
            }
        }
        catch (SQLException ex){
            for (Throwable t : ex){
                t.printStackTrace();
            }
        }
    }

    public void setRow(RowSet rs) throws SQLException{
        for (int i = 1; i<=fields.size(); i++)
        {
            String field = rs.getString(i);
            JTextField tb = fields.get(i-1);
            if (!field.equals(tb.getText()))
                rs.updateString(i, tb.getText());
        }
        rs.updateRow();
    }
}


