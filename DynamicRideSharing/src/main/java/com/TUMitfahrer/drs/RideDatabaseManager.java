/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.TUMitfahrer.drs;

import com.TUMitfahrer.tools.GraphHopperHelper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openstreetmap.gui.jmapviewer.Coordinate;

/**
 *
 * @author Hazem
 */
public class RideDatabaseManager {

    static Connection connectionManager;

    public static boolean connect() {
        try {
            String url = "jdbc:mysql://localhost:3306/tumitfahrer_mysql?zeroDateTimeBehavior=convertToNull";
            String username = "root";
            String password = "";
            Logger.getLogger(RideDatabaseManager.class.getName()).log(Level.INFO, "Connecting to tumitfahrer_mysql...");
            connectionManager = DriverManager.getConnection(url, username, password);
            Logger.getLogger(RideDatabaseManager.class.getName()).log(Level.INFO, "Connected successfully!");
        } catch (SQLException ex) {
            Logger.getLogger(RideDatabaseManager.class.getName()).log(Level.SEVERE, "Unable to connect to database, please check port:1527", ex);
        }
        return true;
    }

    /**
     * Gets max rid id from database, this method is used for assigning a new ride id by adding 1 to the returned value of this method
     * @return Maximum Ride ID
     */
    public static int getMaxRideId() {
        ResultSet maxRideId = RideDatabaseManager.selectCommand("SELECT MAX(PATH_ID) AS MAX_ID FROM RIDE");
        int rideId = 0;
        try {
            if (maxRideId.next()) {
                if (maxRideId.getString("MAX_ID") != null) {
                    rideId = Integer.parseInt(maxRideId.getString("MAX_ID"));
                } else {
                    rideId = 0;
                }
            }
        } catch (SQLException ex) {
            Logger.getLogger(RideDatabaseManager.class.getName()).log(Level.SEVERE, "Unable to get MaxRideId", ex);
        }
        return rideId;
    }

    /**
     * This method aims to save the RideOffer in the database
     * @param ride RideOffer to be saved in the database
     * @throws SQLException 
     */
    public static void saveRideOffer(RideOffer ride) throws SQLException {
        // points, distance in meters and time in millis of the full path
        int rideId = getMaxRideId() + 1;
        Statement stm = RideDatabaseManager.createStatement();
        //set the ride information in RIDE table
        stm.addBatch("insert into RIDE (PATH_ID,DEPT_LAT,DEPT_LON,DEST_LAT,DEST_LON,DEPT_TIME,DEPT_TIME_MARGIN,DURATION,DISTANCE,AVAILABLE_SEATS,MAX_DETOUR) "
                + "values (" + rideId + "," + ride.getDepLat() + "," + ride.getDepLon() + "," + ride.getDestLat() + "," + ride.getDestLon() + ",'"
                + ride.getDepartureTime() + "'," + ride.getTimeMargin() + "," + ride.getRideDuration() + "," + ride.getRideDistance() + "," + ride.getAvailableSeats() + "," + 1000 + ")");
        //set ride points int RIDE_PATH table
        for (int i = 0; i < ride.getRidePoints().size(); i++) {
            //get node from path route
            double longitude = ride.getRidePoints().get(i).getLon();
            double latitude = ride.getRidePoints().get(i).getLat();
            //create unique node in NODE table
            double node_id = pairingfunction(longitude, latitude);
            stm.addBatch("INSERT IGNORE INTO NODE(NODE_ID,LONGITUDE,LATITUDE) VALUES("
                    + String.valueOf(node_id) + "," + String.valueOf(longitude) + "," + String.valueOf(latitude) + ")");
            //create the route data in RIDE_PATH table
            stm.addBatch("insert into RIDE_PATH (PATH_ID,LONGITUDE,LATITUDE,NODE_ORDER) "
                    + "values (" + rideId + "," + String.valueOf(longitude) + "," + String.valueOf(latitude) + "," + String.valueOf(i) + ")");
        }
        stm = insertNodePathMapping(ride.getRidePoints(), rideId, stm);
        RideDatabaseManager.executeBatch(stm);
        Logger.getLogger(RideDatabaseManager.class.getName()).log(Level.INFO, "Ride created in database!");
    }
    /**
     * This method aims to build the mapping between list of Nodes and a RideOffer points, it builds the inverted index data structure in the database
     * @param pointList a set of nodes that belongs to a RideOffer
     * @param pathId the owner id (RideOffer id) of the nodes in pointList
     * @param stm
     * @return
     * @throws SQLException 
     */
    private static Statement insertNodePathMapping(List<Coordinate> pointList, int pathId, Statement stm) throws SQLException {

        for (int i = 0; i < pointList.size(); i++) {
            double longitude = pointList.get(i).getLon();
            double latitude = pointList.get(i).getLat();
            double node_id = generateUniqueId(longitude, latitude);
            stm.addBatch("INSERT IGNORE PATH_MAPPING(NODE_ID,PATH_ID) VALUES("
                    + String.valueOf(node_id) + "," + String.valueOf(pathId) + ")");
        }
        return stm;
    }

    /**
     * this function helps to create a unique node out of two inputs (lon,lat)
     * @param lon Longitude
     * @param lat Latitude
     * @return 
     */
    private static double generateUniqueId(double lon, double lat) {
        //pi(k1, k2) = 1/2(k1 + k2)(k1 + k2 + 1) + k2
        //ref: http://www.cs.upc.edu/~alvarez/calculabilitat/enumerabilitat.pdf
        double fn = 0.5 * (lon * lat) * (lon + lat + 1) + lat;
        return fn;

    }

    /**
     * Get all RideOffers from the database
     * @return a List that contains RideOffers
     */
    public static List<Ride> getRidesFromDatabase() {
        try {
            ResultSet rides = RideDatabaseManager.selectCommand("select * from RIDE_PATH order by PATH_ID,NODE_ORDER");
            Ride ride = null;
            List<Coordinate> points = null;
            double prevLat = 0, prevLon = 0;
            List<Ride> systemRides = new ArrayList<>();
            while (rides.next()) {
                if (rides.getInt("NODE_ORDER") == 0) {
                    if (prevLat != 0 && prevLon != 0) {
                        ride.setDestLat(prevLat);
                        ride.setDestLon(prevLon);
                        ride.setRidePoints(points);
                        systemRides.add(ride);
                    }
                    ride = new Ride();
                    ride.setRideId(rides.getInt("PATH_ID"));
                    ride.setDepLat(rides.getDouble("LATITUDE"));
                    ride.setDepLon(rides.getDouble("LONGITUDE"));
                    points = new ArrayList<>();
                    points.add(new Coordinate(rides.getDouble("LATITUDE"), rides.getDouble("LONGITUDE")));
                } else {
                    if (rides.isLast()) {
                        ride.setDestLat(rides.getDouble("LATITUDE"));
                        ride.setDestLon(rides.getDouble("LONGITUDE"));
                        points.add(new Coordinate(rides.getDouble("LATITUDE"), rides.getDouble("LONGITUDE")));
                        ride.setRidePoints(points);
                        systemRides.add(ride);
                    } else {
                        points.add(new Coordinate(rides.getDouble("LATITUDE"), rides.getDouble("LONGITUDE")));
                    }
                }
                prevLat = rides.getDouble("LATITUDE");
                prevLon = rides.getDouble("LONGITUDE");
            }
            return systemRides;
        } catch (SQLException ex) {
            Logger.getLogger(GraphHopperHelper.class.getName()).log(Level.SEVERE, "Unable to get rides from database", ex);
        }
        return null;
    }

    /**
     * Execute a custom SQL query
     * @param query the query that is going to be executed (insert statement)
     */
    public static void executeCommand(String query) {
        try {
            Statement stmt = RideDatabaseManager.connectionManager.createStatement();
            stmt.execute(query);
        } catch (SQLException ex) {
            Logger.getLogger(RideDatabaseManager.class.getName()).log(Level.SEVERE, String.format("Unable to execute command %s", query), ex);
        }
    }

    /**
     * create a new java.sql statement, this helps to add multiple commands inside one java.sql statement then execute them at once (performance related)
     * @return java.sql statement 
     */
    public static Statement createStatement() {
        try {
            Statement stmt = RideDatabaseManager.connectionManager.createStatement();
            return stmt;
        } catch (SQLException ex) {
            Logger.getLogger(RideDatabaseManager.class.getName()).log(Level.SEVERE, String.format("Unable to create statement"), ex);
        }
        return null;
    }

    /**
     * This method execute the all commands that are stored inside java.sql statement
     * @param stmt statement to be executed
     */
    public static void executeBatch(Statement stmt) {
        try {
            stmt.executeBatch();
        } catch (SQLException ex) {
            Logger.getLogger(RideDatabaseManager.class.getName()).log(Level.SEVERE, String.format("Unable to execute batch"), ex);
        }
    }

    /**
     * execute a select command
     * @param query query to be s
     * @return the result of the select command
     */
    public static ResultSet selectCommand(String query) {
        try {
            Statement stmt = RideDatabaseManager.connectionManager.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ResultSet results = stmt.executeQuery(query);
            return results;
        } catch (SQLException ex) {
            Logger.getLogger(RideDatabaseManager.class.getName()).log(Level.SEVERE, String.format("Unable to execute command %s", query), ex);
        }
        return null;
    }

    public static Timestamp getRideDepartureTime(int rideId) throws SQLException {
        ResultSet result = selectCommand("select DEPT_TIME from RIDE where PATH_ID=" + rideId);
        if (!result.next()) {
             Logger.getLogger(mainForm.class.getName()).log(Level.WARNING,"Unable to get RideOffer departureTime");
        }
        return result.getTimestamp("DEPT_TIME");
    }

    public static int getRideDepartureTimeMargin(int rideId) throws SQLException {
        ResultSet result = selectCommand("select DEPT_TIME_MARGIN from RIDE where PATH_ID=" + rideId);
        if (!result.next()) {
            Logger.getLogger(mainForm.class.getName()).log(Level.WARNING,"Unable to get RideOffer departureTimeMargin");
        }
        return result.getInt("DEPT_TIME_MARGIN");
    }

    public static double getRideDepartureLat(int rideId) throws SQLException {
        ResultSet result = selectCommand("select DEPT_LAT from RIDE where PATH_ID=" + rideId);
        if (!result.next()) {
            Logger.getLogger(mainForm.class.getName()).log(Level.WARNING,"Unable to get RideOffer departureLat");
        }
        return result.getDouble("DEPT_LAT");
    }

    public static double getRideDepartureLon(int rideId) throws SQLException {
        ResultSet result = selectCommand("select DEPT_LON from RIDE where PATH_ID=" + rideId);
        if (!result.next()) {
            Logger.getLogger(mainForm.class.getName()).log(Level.WARNING,"Unable to get RideOffer departureLon");
        }
        return result.getDouble("DEPT_LON");
    }

    public static double getRideDestinationLat(int rideId) throws SQLException {
        ResultSet result = selectCommand("select DEST_LAT from RIDE where PATH_ID=" + rideId);
        if (!result.next()) {
            Logger.getLogger(mainForm.class.getName()).log(Level.WARNING,"Unable to get RideOffer DistenationLat");
        }
        return result.getDouble("DEST_LAT");
    }

    public static double getRideDestinationLon(int rideId) throws SQLException {
        ResultSet result = selectCommand("select DEST_LON from RIDE where PATH_ID=" + rideId);
        if (!result.next()) {
            //return error
        }
        return result.getDouble("DEST_LON");
    }

    public static double getRideDistance(int rideId) throws SQLException {
        ResultSet result = selectCommand("select DISTANCE from RIDE where PATH_ID=" + rideId);
        if (!result.next()) {
            Logger.getLogger(mainForm.class.getName()).log(Level.WARNING,"Unable to get RideOffer Distance");
        }
        return result.getDouble("DISTANCE");
    }

    public static double getMaxDetour(int rideId) throws SQLException {
        ResultSet result = selectCommand("select MAX_DETOUR from RIDE where PATH_ID=" + rideId);
        if (!result.next()) {
            Logger.getLogger(mainForm.class.getName()).log(Level.WARNING,"Unable to get RideOffer MaxDetour");
        }
        return result.getDouble("MAX_DETOUR");
    }

    private static double pairingfunction(double lon, double lat) {
        //pi(k1, k2) = 1/2(k1 + k2)(k1 + k2 + 1) + k2
        //ref: http://www.cs.upc.edu/~alvarez/calculabilitat/enumerabilitat.pdf
        double fn = 0.5 * (lon * lat) * (lon + lat + 1) + lat;
        return fn;

    }
}
