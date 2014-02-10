import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Test {
    public static void testDB(Statement statement) throws SQLException {
        ResultSet result;
        statement.executeQuery(
                "DELETE FROM Ticks");

        System.out.println("-- Inserting Data --");

        System.out.println("-- Adding to Ticks Table --");
        statement.executeQuery(
                "INSERT INTO Ticks ( src, stamp, tick ) VALUES " +
                "      ( 0, 12341, 1.346, 1.1 ), " +
                "      ( 0, 12342, 1.344, 2.2 ), " +
                "      ( 0, 12343, 1.343, 3.3 ), " +
                "      ( 0, 12344, 1.344, 4.4 ), " +
                "      ( 0, 12345, 1.345, 5.5 ) ");

        System.out.println("-- query --");
        // How many rows are in the 'Ticks' table?
        result = statement.executeQuery("SELECT COUNT(*) FROM Ticks");
        if (result.next()) {
            System.out.println("Rows in 'Ticks' table: " + result.getInt(1));
        }
        System.out.println();

//                    // List the name and music group of all the people that listen to
//                    // either 'Oasis' or 'Beatles'
//                    result = statement.executeQuery(
//                            "   SELECT Person.name, MusicGroup.name " +
//                                    "     FROM Person, ListensTo, MusicGroup " +
//                                    "    WHERE MusicGroup.name IN ( 'Oasis', 'Beatles' ) " +
//                                    "      AND Person.name = ListensTo.person_name " +
//                                    "      AND ListensTo.music_group_name = MusicGroup.name " +
//                                    " ORDER BY MusicGroup.name, Person.name ");
//                    System.out.println("All people that listen to either Beatles or Oasis:");
//                    while (result.next()) {
//                        System.out.print("  " + result.getString(1));
//                        System.out.print(" listens to ");
//                        System.out.println(result.getString(2));
//                    }
//                    System.out.println();
    }
}
