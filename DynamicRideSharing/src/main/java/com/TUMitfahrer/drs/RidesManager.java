/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.TUMitfahrer.drs;

import com.TUMitfahrer.tools.GraphHopperHelper;
import com.graphhopper.GHResponse;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import org.openstreetmap.gui.jmapviewer.Coordinate;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Calendar;
import java.util.logging.Logger;

/**
 *
 * @author Hazem
 */
public class RidesManager {

    /**
     * Chech if there is at least one RideOffer on date
     * @param date the date to be checked
     * @return true if there are RideOffers on the same date
     * @throws SQLException 
     */
    public static boolean checkIfExistRideOffersOnDate(String date) throws SQLException {
        ResultSet result = RideDatabaseManager.selectCommand("SELECT * FROM ride where cast(DEPT_TIME as DATE) ='" + date.substring(0, 10) + "'");
        if (!result.next()) {
            Logger.getLogger(RidesManager.class.getName()).log(Level.INFO, "There are nor ride offers on the system in the same date of the passenger departure date");
            return false;
        }
        return true;

    }

    /**
     * Check the max detour distance in case a passenger joined a RideOffer
     * @param passengerRide the passenger route (RideRequest)
     * @param rideOffers a set of ride offers 
     * @return a list of RideOffers which obeys the max detour condition
     * @throws SQLException 
     */
    public static List<IntersectionResult> chechIfNewRideWithingMaxDetour(Ride passengerRide, List<IntersectionResult> rideOffers) throws SQLException {
        for (Iterator<IntersectionResult> iterator = rideOffers.iterator(); iterator.hasNext();) {
            IntersectionResult row = iterator.next();
            double distanceBeforeJoin = RideDatabaseManager.getRideDistance(row.pathId);
            double maxDetour = RideDatabaseManager.getMaxDetour(row.pathId);
            List<GHPoint> list = new ArrayList<GHPoint>();
            list.add(new GHPoint(RideDatabaseManager.getRideDepartureLat(row.pathId), RideDatabaseManager.getRideDepartureLon(row.pathId)));
            list.add(new GHPoint(passengerRide.getDepLat(), passengerRide.getDepLon()));
            list.add(new GHPoint(passengerRide.getDestLat(), passengerRide.getDestLon()));
            list.add(new GHPoint(RideDatabaseManager.getRideDestinationLat(row.pathId), RideDatabaseManager.getRideDestinationLon(row.pathId)));
            GHResponse rsp = GraphHopperHelper.createRequest(list);
            if (rsp.getDistance() - distanceBeforeJoin < maxDetour) {
                //passenger can join ride
                Logger.getLogger(RidesManager.class.getName()).log(Level.INFO, String.format("Passenger can joind ride: %d, detour distance is: %f and maxDetour is:%f",
                        row.pathId, rsp.getDistance() - distanceBeforeJoin, maxDetour));
            } else {
                //can't join ride==>remove ride offer from the result list
                iterator.remove();
            }
        }
        return rideOffers;
    }

    /**
     * Search for a ride within a circular area
     * @param passengerRoute Passenger's path
     * @param radius circle radius
     * @param passengerTimestamp the date of the passenger's departure
     * @throws SQLException 
     */
    public static void searchForRide(Ride passengerRoute, double radius, String passengerTimestamp) throws SQLException {
        //check if the ride request has ride offers on the same day, if not return out of the method
        if (!checkIfExistRideOffersOnDate(passengerTimestamp)) {
            return;
        }
        //get riderOffers withing the circle arount departure and destenation points of the RideRequest
        List<PathMapingResultRow> departurePointAvaliablePathIds = getRidesWithinCircularArea(passengerRoute.getDepLat(), passengerRoute.getDepLon(), radius);
        List<PathMapingResultRow> destinationPointAvaliablePathIds = getRidesWithinCircularArea(passengerRoute.getDestLat(), passengerRoute.getDestLon(), radius);
        List<IntersectionResult> intersectionResult = intersectTwoLists(departurePointAvaliablePathIds, destinationPointAvaliablePathIds);
        //remove the paths which don't statisfy the departure time criteria
        intersectionResult = checkDriverDepartureTime(intersectionResult, Timestamp.valueOf(passengerTimestamp), passengerRoute.getDepLat(), passengerRoute.getDepLon());
        //remove paths which have don't statisfy the max detour criteria
        intersectionResult = chechIfNewRideWithingMaxDetour(passengerRoute, intersectionResult);
        //check if the departure time of the passenger suits the driver departure time 
        //check if the driver path has the same direction of the passenger path
        //this can be done by checking thr node order of the driver
        //i.e: passeger departure point must intersect with the the driver node which have the lowest order ex: 5
        //then passeger destenation point must intersect with the the driver node which have the highest order ex: 124
        //in this case we know that driver path has the same direction of the passenger path
        long startTime6 = System.nanoTime();
        for (IntersectionResult row : intersectionResult) {
            //get the node order of the node which has been intersected in the circle around the departure point
            //1st get lat,lon of the node1
            ResultSet nodeSet = RideDatabaseManager.selectCommand("select * from Node where NODE_ID=" + row.sourceNodeId);
            if (!nodeSet.next()) {
                Logger.getLogger(RidesManager.class.getName()).log(Level.SEVERE, "Intersected driver node id at the passenger departure circle not found in table NODE");
                return;
            }
            double node1Lat = nodeSet.getDouble("LATITUDE");
            double node1Lon = nodeSet.getDouble("LONGITUDE");
            ResultSet orderSet = RideDatabaseManager.selectCommand("select NODE_ORDER from RIDE_PATH where LONGITUDE=" + node1Lon + " AND LATITUDE=" + node1Lat);
            if (!orderSet.next()) {
                Logger.getLogger(RidesManager.class.getName()).log(Level.SEVERE, "Intersected driver node at the passenger departure circle not found in table RIDE_PATH");
                return;
            }
            int intersectedNodeOrderAtSource = orderSet.getInt("NODE_ORDER");
            //2nd get lat,lon of the node2
            nodeSet = RideDatabaseManager.selectCommand("select * from Node where NODE_ID=" + row.destenationNodeId);
            if (!nodeSet.next()) {
                Logger.getLogger(RidesManager.class.getName()).log(Level.SEVERE, "Intersected driver node id at the passenger destenation circle not found in table NODE");
                return;
            }
            double node2Lat = nodeSet.getDouble("LATITUDE");
            double node2Lon = nodeSet.getDouble("LONGITUDE");
            orderSet = RideDatabaseManager.selectCommand("select NODE_ORDER from RIDE_PATH where LONGITUDE=" + node2Lon + " AND LATITUDE=" + node2Lat);
            if (!orderSet.next()) {
                Logger.getLogger(RidesManager.class.getName()).log(Level.SEVERE, "Intersected driver node id at the passenger destenation circle not found in table RIDE_PATH");
                return;
            }
            int intersectedNodeOrderAtDistenation = orderSet.getInt("NODE_ORDER");
            row.canJoinRide = intersectedNodeOrderAtSource < intersectedNodeOrderAtDistenation; //pasenger can join
            if (row.canJoinRide) {
                Logger.getLogger(RidesManager.class.getName()).log(Level.INFO, String.format("Passenger can join rideId: %d ", row.pathId));
            }
        }
        long endTime6 = System.nanoTime();
        System.out.format("\n6- CheckRideDirection %f", (endTime6 - startTime6) / 1000000000.0);
    }

    /**
     * This Method aims to check drivers departure time along with the passenger departure time, taking into consideration the travel time from driver's departure location
     * to passenger's departure location
     * @param paths
     * @param passengerTimestamp
     * @return
     * @throws SQLException
     */
    private static List<IntersectionResult> checkDriverDepartureTime(List<IntersectionResult> paths, Timestamp passengerTimestamp, double passengerDeptLat, double passengerDeptLon) throws SQLException {
        Calendar cal = Calendar.getInstance();
        for (Iterator<IntersectionResult> iterator = paths.iterator(); iterator.hasNext();) {
            IntersectionResult row = iterator.next();
            String details = "Passenger can join rideId:" + row.pathId + "\n";
            //calculate the approximate time needed for the driver to go from the driver departure location to passenger departure location
            ResultSet result = RideDatabaseManager.selectCommand("select DEPT_LON,DEPT_LAT from RIDE where PATH_ID=" + row.pathId);
            if (!result.next()) {
                //return error
            }
            double rideofferDepLat = result.getDouble("DEPT_LAT");
            double rideofferDepLon = result.getDouble("DEPT_LON");
            long travelTime = GraphHopperHelper.getTravelTimeBetweenTwoPoints(rideofferDepLat, rideofferDepLon, passengerDeptLat, passengerDeptLon);
            //get driver actual departre date and time
            Timestamp driverDepartureTime = RideDatabaseManager.getRideDepartureTime(row.pathId);
            details += "DriverDeparture Time:" + driverDepartureTime + "\n";
            //get the time marging in order to calculate the min and max time window to look for rides
            int driverDeparturMargin = RideDatabaseManager.getRideDepartureTimeMargin(row.pathId);
            //calculate min
            cal.setTimeInMillis(driverDepartureTime.getTime());
            cal.add(Calendar.MINUTE, -driverDeparturMargin);
            cal.add(Calendar.SECOND, (int) travelTime / 1000);
            Timestamp minDriverTimestamp = new Timestamp(cal.getTime().getTime());
            details += "TimeRange to join Ride: [" + minDriverTimestamp + ", ";
            //calculate max
            cal = Calendar.getInstance();
            cal.setTimeInMillis(driverDepartureTime.getTime());
            cal.add(Calendar.MINUTE, driverDeparturMargin);
            cal.add(Calendar.MILLISECOND, (int) travelTime);
            Timestamp maxDriverTimestamp = new Timestamp(cal.getTime().getTime());
            details += maxDriverTimestamp + "\n";
            details += "Travel Time from driverDep to pasengerDest:" + travelTime / 1000 + "\n";
            cal = Calendar.getInstance();
            cal.setTimeInMillis(passengerTimestamp.getTime());
            details += "PassengerDept: " + passengerTimestamp;
            if (passengerTimestamp.before(maxDriverTimestamp) && passengerTimestamp.after(minDriverTimestamp)) {
                //passenger can join this ride
                Logger.getLogger(RidesManager.class.getName()).log(Level.INFO, details);
            } else {
                iterator.remove();
            }
        }
        return paths;
    }

    /**
     * Intersect two PathMapingResultRow lists in terms of same pathId, if the
     * path id is the same in the two list then take the average distance of
     * nodeDistanceToCircleCenter. Average distance will help to order the rides
     * for the passenger
     *
     * @param list1
     * @param list2
     * @return
     */
    private static List<IntersectionResult> intersectTwoLists(List<PathMapingResultRow> list1, List<PathMapingResultRow> list2) {
        List<IntersectionResult> result = new ArrayList<>();
        for (PathMapingResultRow result1 : list1) {
            for (PathMapingResultRow result2 : list2) {
                if (result1.pathId == result2.pathId) {
                    IntersectionResult row = new IntersectionResult();
                    row.averageDistance = (result1.nodeDistanceToCircleCednter + result2.nodeDistanceToCircleCednter) / 2;
                    row.pathId = result1.pathId;
                    row.sourceNodeId = result1.nodeId;
                    row.destenationNodeId = result2.nodeId;
                    result.add(row);
                }
            }
        }
        return result;
    }

    /**
     * Get all routes that pass throw a defined a circle
     *
     * @param circle the area to search for routes inside
     *
     */
    private static List<PathMapingResultRow> getRidesWithinCircularArea(double lat, double lon, double radius) throws SQLException {
        //circle solution
         ResultSet availableNodes = RideDatabaseManager.selectCommand("select a.NODE_ID,b.PATH_ID,a.DISTANCE "
         + " from ("
         + "select NODE_ID,get_distance_in_meter_between_geo_locations(LATITUDE,LONGITUDE," + lat + "," + lon + ") as DISTANCE "
         + "FROM NODE "
         + "HAVING DISTANCE < " + radius + ") as a, PATH_MAPPING as b "
         + "WHERE a.NODE_ID=b.NODE_ID");
        List<PathMapingResultRow> pathsSet = new ArrayList<>();
        //get the rides which pas through available points 
        while (availableNodes.next()) {
            PathMapingResultRow result = new PathMapingResultRow();
            result.nodeId = availableNodes.getDouble("NODE_ID");
            result.pathId = availableNodes.getInt("PATH_ID");
            //result.nodeDistanceToCircleCednter = distance(lat, lon, availableNodes.getDouble("LATITUDE"), availableNodes.getDouble("LONGITUDE"));
            result.nodeDistanceToCircleCednter = availableNodes.getInt("DISTANCE");
            if (result.nodeDistanceToCircleCednter < radius) {
                if (replaceIfLowerDistanceFound(pathsSet, result) == null) {
                    pathsSet.add(result);
                }
            }
        }
        return pathsSet;
    }
    /**
     * same method above but it discusses the square solution prsented in the thesis in Evaluation section
     * @param lat
     * @param lon
     * @param radius
     * @return
     * @throws SQLException 
     */
    private static List<PathMapingResultRow> getRidesWithinSquareArea(double lat, double lon, double radius) throws SQLException {
         //get all system nodes which the distance between each node and the center of the circle is less than the radius of the circle
        double maxLat = getLatitudeInMeters(lat, radius);
        double minLat = getLatitudeInMeters(lat, -radius);
        double maxLon = getLongitudeInMeters(lon, lat, radius);
        double minLon = getLongitudeInMeters(lon, lat, -radius);
        ResultSet availableNodes = RideDatabaseManager.selectCommand("select a.NODE_ID,b.PATH_ID,a.LATITUDE,a.LONGITUDE "
                + " from ("
                + "select NODE_ID,LATITUDE,LONGITUDE "
                + "FROM NODE "
                + "WHERE LATITUDE BETWEEN " + minLat + " AND " + maxLat + " AND "
                + "LONGITUDE BETWEEN " + minLon + " AND " + maxLon + ") as a,PATH_MAPPING as b "
                + "WHERE a.NODE_ID=b.NODE_ID");
        
        List<PathMapingResultRow> pathsSet = new ArrayList<>();
        //get the rides which pas through available points 
        while (availableNodes.next()) {
            PathMapingResultRow result = new PathMapingResultRow();
            result.nodeId = availableNodes.getDouble("NODE_ID");
            result.pathId = availableNodes.getInt("PATH_ID");
            result.nodeDistanceToCircleCednter = distance(lat, lon, availableNodes.getDouble("LATITUDE"), availableNodes.getDouble("LONGITUDE"));
            if (result.nodeDistanceToCircleCednter < radius) {
                if (replaceIfLowerDistanceFound(pathsSet, result) == null) {
                    pathsSet.add(result);
                }
            }
        }
        return pathsSet;
    }

    /*
     * Calculate distance between two points in latitude and longitude taking
     * into account height difference. If you are not interested in height
     * difference pass 0.0. Uses Haversine method as its base.
     * 
     * lat1, lon1 Start point lat2, lon2 End point el1 Start altitude in meters
     * el2 End altitude in meters
     * @returns Distance in Meters
     */
    public static double distance(double lat1, double lon1, double lat2,
            double lon2) {
        double el1 = 500;
        double el2 = 500;
        final int R = 6371; // Radius of the earth

        Double latDistance = Math.toRadians(lat2 - lat1);
        Double lonDistance = Math.toRadians(lon2 - lon1);
        Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        double height = el1 - el2;

        distance = Math.pow(distance, 2) + Math.pow(height, 2);

        return Math.sqrt(distance);
    }

    private static double getLatitudeInMeters(double latitude, double distance) {
        long earthRadius = 6378000;//meters
        double newLatitude = latitude + (distance / earthRadius) * (180 / Math.PI);
        return newLatitude;
    }

    private static double getLongitudeInMeters(double longitude, double latitude, double distance) {
        long earthRadius = 6378000;//meters
        double newLongitude = longitude + (distance / earthRadius) * (180 / Math.PI) / Math.cos(latitude * Math.PI / 180);
        return newLongitude;
    }

    /**
     * Choose the shortest distance between the intersected point and the circle
     * center
     *
     * @param set the list of paths which intersected with the circle around
     * departure/destination points
     * @param var compare this value to the values in the list, if this value
     * has shortest distance=>replace it with the existing value in set
     * @return
     */
    private static List<PathMapingResultRow> replaceIfLowerDistanceFound(List<PathMapingResultRow> set, PathMapingResultRow var) {
        for (PathMapingResultRow row : set) {
            if (row.pathId == var.pathId) {
                if (var.nodeDistanceToCircleCednter < row.nodeDistanceToCircleCednter) {
                    //repalce to the new node
                    row.nodeDistanceToCircleCednter = var.nodeDistanceToCircleCednter;
                    row.nodeId = var.nodeId;
                }
                return set;
            }
        }
        return null;
    }

    /**
     * get Ride offer by id from the database
     * @param id
     * @return
     * @throws SQLException 
     */
    public static RideOffer getRideOfferById(int id) throws SQLException {
        RideOffer rideOffer = new RideOffer();
        rideOffer.setRideId(id);
        ResultSet ride = RidesManager.getRideByPathId(id);
        if (!ride.next()) {
            //ride not found
            Logger.getLogger(RidesManager.class.getName()).log(Level.SEVERE, String.format("Ride not found! ride id: %d", id));
            return null;
        }
        rideOffer.setDepLat(ride.getDouble("DEPT_LAT"));
        rideOffer.setDepLon(ride.getDouble("DEPT_LON"));
        rideOffer.setDestLat(ride.getDouble("DEST_LAT"));
        rideOffer.setDestLon(ride.getDouble("DEST_LON"));
        rideOffer.setRideDistance(ride.getDouble("DISTANCE"));
        rideOffer.setRideDuration(ride.getLong("DURATION"));
        rideOffer.setAvailableSeats(ride.getInt("AVAILABLE_SEATS"));
        rideOffer.setMaxDetour(ride.getDouble("MAX_DETOUR"));
        rideOffer.setDepartureTime(ride.getTimestamp("DEPT_TIME"));
        rideOffer.setTimeMargin(ride.getInt("DEPT_TIME_MARGIN"));
        return rideOffer;
    }

    /**
     * This method aims to combine two routes, the passenger's route and the driver's route
     * @param passengerRoute
     * @param driverRideId
     * @throws SQLException
     * @throws ParseException 
     */
    public static void joinRide(RideRequest passengerRoute, int driverRideId) throws SQLException, ParseException {
        //get ride info from database
        ResultSet rideinfo = RideDatabaseManager.selectCommand("select * from Ride where PATH_ID=" + driverRideId);
        if (rideinfo.next()) {
            double driverDeptLat = rideinfo.getDouble("DEPT_LAT");
            double driverDeptLon = rideinfo.getDouble("DEPT_LON");
            double driverDestLat = rideinfo.getDouble("DEST_LAT");
            double driverDestLon = rideinfo.getDouble("DEST_LON");
            //ride X--->Z
            //passenger a--b
            //new ride X-->a-->b-->Z
            //get the ride between X-->a
            List<Coordinate> points = new ArrayList<>();
            points.add(new Coordinate(driverDeptLat, driverDeptLon));
            points.add(new Coordinate(passengerRoute.getDepLat(), passengerRoute.getDepLon()));
            points.add(new Coordinate(passengerRoute.getDestLat(), passengerRoute.getDestLon()));
            points.add(new Coordinate(driverDestLat, driverDestLon));
            RideOffer newRoute = getRideOfferById(driverRideId);
            createRouteWithIntermediatePoints(newRoute, points);
            //delete old driver route form database (path from X-->Z)
            //passengerRoute.deleteRide(driverRideId);
            deleteRide(driverRideId);
            //calculate the new distance and route time
            //create the new route in the database
            RideDatabaseManager.saveRideOffer(newRoute);
        }
    }

    /**
     * Create a RideOffer that passed through intermediate different points 
     * @param rideOffer Old RideOffer
     * @param intermediatePoints points that RideOffer will pass through
     */
    public static void createRouteWithIntermediatePoints(RideOffer rideOffer, List<Coordinate> intermediatePoints) {
        List<GHPoint> newList = new ArrayList<>();
        List<Coordinate> ridePoints = new ArrayList<>();
        for (Coordinate point : intermediatePoints) {
            newList.add(new GHPoint(point.getLat(), point.getLon()));
        }
        GHResponse rsp = GraphHopperHelper.createRequest(newList);
        PointList points = rsp.getPoints();
        for (int i = 0; i < points.size(); i++) {
            ridePoints.add(new Coordinate(points.getLatitude(i), points.getLongitude(i)));
        }
        if (ridePoints.size() > 2) {
            rideOffer.setDepLat(ridePoints.get(0).getLat());
            rideOffer.setDepLon(ridePoints.get(0).getLon());
            rideOffer.setDestLat(ridePoints.get(ridePoints.size() - 1).getLat());
            rideOffer.setDestLon(ridePoints.get(ridePoints.size() - 1).getLon());
            rideOffer.setRideDistance(rsp.getDistance());
            rideOffer.setRideDuration(rsp.getMillis() / 1000);//millis to seconds
            //join ride => decrease available seats by 1
            rideOffer.setAvailableSeats(rideOffer.getAvailableSeats() - 1);
            rideOffer.setMaxDetour(rideOffer.getMaxDetour());
            rideOffer.setDepartureTime(rideOffer.getDepartureTime());
            rideOffer.setTimeMargin(rideOffer.getTimeMargin());
            rideOffer.setRidePoints(ridePoints);
        }
    }
    /**
     * Generate a new RideOffer id
     * @return
     * @throws SQLException 
     */
    public static int getNewRideId() throws SQLException {
        return RideDatabaseManager.getMaxRideId() + 1;
    }

    /**
     * Delete a RideOffer from the database, this function is used after a joining a RideOffer, a new RideOffer is created and the old RideOffer will be deleted
     * @param rideId ride id to be deleted
     */
    public static void deleteRide(int rideId) {
        //1. delete route in PATH table
        RideDatabaseManager.executeCommand("delete from RIDE where PATH_ID=" + rideId);
        //2. delete route points from RIDE_PATH table
        RideDatabaseManager.executeCommand("delete from RIDE_PATH where PATH_ID=" + rideId);
        //3. delete all nodes with points to this route in PATH_MAPPING table
        RideDatabaseManager.executeCommand("delete from PATH_MAPPING where PATH_ID=" + rideId);
    }

    public void getRideFromDB(int rideId) throws SQLException {
        //TODO: select from RIDE to get ride bearing and dept time
        ResultSet result = RideDatabaseManager.selectCommand("select * from RIDE_PATH where PATH_ID=" + String.valueOf(rideId));
        while (result.next()) {
            double nodeOrder = result.getDouble("NODE_ORDER");
            double lat = result.getDouble("LATITUDE");
            double lon = result.getDouble("LONGITUDE");
        }
    }

    public static ResultSet getRideByPathId(int pathId) {
        return RideDatabaseManager.selectCommand("select * from RIDE where PATH_ID=" + pathId);
    }

}
