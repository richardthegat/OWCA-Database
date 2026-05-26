package owca;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public final class DBConnection {

    private static final String URL  = "jdbc:oracle:thin:@oracle2.wiu.edu:1521/orclpdb1";
    private static final String USER = "S26_TEAM_2";
    private static final String PASS = "bl68aQJc";



    
    public static Connection open() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
