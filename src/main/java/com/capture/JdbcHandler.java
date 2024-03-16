package com.capture;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JdbcHandler {

    public Connection connection;
    public String[] resultList;
    public int colCount;

    static public Pattern pattern = Pattern.compile("[a-z][a-z0-9_]*");

    public void connect(String jdbcClassName, String dbUri, String username, String password)
            throws ClassNotFoundException, SQLException {
        Class.forName(jdbcClassName);
        connection = DriverManager.getConnection(dbUri, username, password);
    }

    public void executeSQL(String sql) throws SQLException {
        Statement statement = connection.createStatement();
        boolean result = statement.execute(sql);
        SQLWarning warning = statement.getWarnings();

        if (warning != null) {
            // System.out.println("There are some warnings:");
            // System.out.println(warning.getMessage());
        }

        if (result) {
            // System.out.println("There are some results:");

            ArrayList<String> resultArrayList = new ArrayList<String>();
            ResultSet rs = statement.getResultSet();

            colCount = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                for (int i = 1; i <= colCount; ++i) {
                    resultArrayList.add(rs.getString(i));
                    // System.out.println(resultArrayList.get(resultArrayList.size() - 1));
                }
            }

            resultList = resultArrayList.toArray(new String[0]);
        } else {
            colCount = 0;
            resultList = new String[0];
        }
    }

    /* Execute the SQL statement, and return the sub-schema info of this sql statement. 
    * If the sub-schema is empty, return null. 
    */
    public String captureSQL(String sql) throws SQLException {
        executeSQL(sql);

        Matcher matcher = pattern.matcher(sql);
        StringBuilder description = null;

        while (matcher.find()) {
            String matched = matcher.group();
            String objectType = getObjectTypeOfIdentifier(matched);

            if (objectType == null) {
                System.err.println("No object type found for " + matched);
                continue;
            }

            String cur_description = "\"" + matched + "\" is a " + objectType;

            if (objectType != null) {
                if (description == null) {
                    description = new StringBuilder();
                    description.append("Note that ");
                } else {
                    description.append(", ");
                }

                description.append(cur_description);
            }
        }

        if (description == null) {
            return null;
        } else {
            return description.toString();
        }
    }

    public String getObjectTypeOfIdentifier(String identifier) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        ResultSet tables = metadata.getTables(null, null, identifier, new String[] { "TABLE" });
        if (tables.next()) {
            return "TABLE";
        }

        ResultSet indexes = metadata.getIndexInfo(null, null, identifier, false, true);
        if (indexes.next()) {
            return "INDEX";
        }

        ResultSet columns = metadata.getColumns(null, null, null, identifier);
        if (columns.next()) {
            return "column with " + columns.getString("TYPE_NAME") + " type";
        }

        return null;
    }

    void setCatalog(String catalog) throws SQLException {
        System.out.println("Try to setCatalog: " + catalog);
        connection.setCatalog(catalog);
        System.out.println("Current catalog: " + connection.getCatalog());
    }

    String[] getResultList() {
        return resultList;
    }

    int getColCount() {
        return colCount;
    }

    boolean clearDatabase() throws SQLException {
        try {
            executeSQL("commit");
        } catch (Exception e) {
        }

        try {
            executeSQL("end");
        } catch (Exception e) {
        }

        ResultSet tables = connection.getMetaData().getTables(null, null, null, new String[] { "TABLE" });

        List<String> tablenames = new ArrayList<String>();
        while (tables.next()) {
            tablenames.add(tables.getString("TABLE_NAME"));
        }

        for (String tablename : tablenames) {
            executeSQL("DROP TABLE " + tablename);
        }

        return true;
    }

    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    public static void main(String[] args) throws SQLException, ClassNotFoundException, InterruptedException {
        if (args.length != 5) {
            System.out.println("argument: <jdbcClassName> <dbUri> <username> <password> <sql>");
            return;
        } else {

            long startTime = System.currentTimeMillis();

            JdbcHandler jdbcForCreateDatabase = new JdbcHandler();
            jdbcForCreateDatabase.connect(args[0], args[1], args[2], args[3]);
            System.err.println("Databases Connected.");

            String description = jdbcForCreateDatabase.captureSQL(args[4]);

            if (description == null) {
                System.err.println("No sub-schema info found.");
            } else {
                System.out.println(args[4]);
                System.out.println("-- " + description);
            }
       }
    }
}