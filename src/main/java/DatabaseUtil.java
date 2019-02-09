import java.awt.geom.Point2D;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class DatabaseUtil {


    /**
     * Get the list of points connecting origin o to destination d (both included).
     * List is pulled from database.
     * <p>
     * Methods getLineString and allPoints are common consumers.
     *
     * @param o Origin id
     * @param d Destination id
     * @return List of points (Point2D)
     */
    public List<Point2D> getPointStringDB(Connection conn, int o, int d) {

        List<Point2D> listPoints = new ArrayList<>();

        // assume that conn is an already created JDBC connection (see previous examples)
        Statement stmt = null;
        ResultSet rs = null;

        String query = "SELECT path_ids," +
                " ST_AsText(path_line) as path_line, " +
                "distance_meters" +
                " FROM from_to" +
                " WHERE id_from = %s" +
                " AND id_to = %s";

        query = String.format(query, o, d);

        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);

            if (rs.next()) {
                String path_line = rs.getString("path_line");
                String path_ids = rs.getString("path_ids");
                List<String> list_ids = Arrays.asList(path_ids.split(";"));
                listPoints = Arrays.asList(path_line.substring("LINESTRING(".length(),
                        path_line.length() - 1).split(",")).stream().map(p -> {
                    String[] a = p.split(" ");
                    return new Point2D.Double(Double.valueOf(a[0]), Double.valueOf(a[1]));
                }).collect(Collectors.toList());

            }
        } catch (SQLException ex) {
            // handle any errors
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());

        } finally {

            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException sqlEx) {
                } // ignore

                rs = null;
            }

            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException sqlEx) {
                } // ignore

                stmt = null;
            }
            return listPoints;
        }
    }

    /**
     * Return the map of node ids and respective coordinates.
     * Pull data from database
     *
     * @return
     */
    private HashMap<Integer, Point2D> getNodesDB(Connection conn) {

        HashMap<Integer, Point2D> nodeCoordinates = new HashMap<>();

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery("SELECT id, st_x(coordinate) as lon, st_y(coordinate) as lat FROM node");

            while (rs.next()) {
                nodeCoordinates.put(rs.getInt("id"),
                        new Point2D.Double(rs.getDouble("lon"),
                                rs.getDouble("lat")));
            }

        } catch (SQLException ex) {
            // handle any errors
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());

        } finally {

            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException sqlEx) {
                } // ignore

                rs = null;
            }

            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException sqlEx) {
                } // ignore

                stmt = null;
            }

            return nodeCoordinates;
        }
    }

    /**
     * Get shortest path (list of network ids) between two nodes from database.
     *
     * @param o
     * @param d
     * @return list of network ids, or empty list if there is no path
     */
    public ArrayList<Short> getShortestPathBetweenODfromDB(Connection conn, int o, int d) {

        // assume that conn is an already created JDBC connection (see previous examples)
        Statement stmt = null;
        ResultSet rs = null;
        ArrayList<Short> list_ids = null;

        String query = "SELECT path_ids," +
                " ST_AsText(path_line) as path_line, " +
                "distance_meters" +
                " FROM from_to" +
                " WHERE id_from = %s" +
                " AND id_to = %s";

        query = String.format(query, o, d);

        System.out.println(query);


        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);

            if (rs.next()) {


                String path_line = rs.getString("path_line");
                Double distance_meters = rs.getDouble("distance_meters");
                String path_ids = rs.getString("path_ids");
                list_ids = (ArrayList) Arrays.asList(path_ids.split(";")).stream().map(n -> Short.valueOf(n)).collect(Collectors.toList());

                return list_ids;

            }
        } catch (SQLException ex) {
            // handle any errors
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());

        } finally {
            // it is a good idea to release
            // resources in a finally{} block
            // in reverse-order of their creation
            // if they are no-longer needed

            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException sqlEx) {
                } // ignore

                rs = null;
            }

            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException sqlEx) {
                } // ignore

                stmt = null;
            }
        }

        //System.out.println(sp);
        return list_ids;
    }
}


