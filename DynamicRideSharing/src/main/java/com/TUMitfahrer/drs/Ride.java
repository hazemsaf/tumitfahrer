/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.TUMitfahrer.drs;

import com.TUMitfahrer.tools.GraphHopperHelper;
import com.graphhopper.*;
import com.graphhopper.util.PointList;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.openstreetmap.gui.jmapviewer.Coordinate;
import java.sql.Timestamp;
import java.util.logging.Logger;

/**
 *
 * @author Hazem
 */
public class Ride {

    //user defined
    private double departure_latitude;
    private double departure_longitude;
    private double destination_latitude;
    private double destination_longitude;
    private Timestamp departure_time;
    //system calculated variables
    private List<Coordinate> ridePoints;
    private int rideId;
    private double rideDistance;//meter
    private long rideDuration;//seconds

    public int getRideId() {
        return this.rideId;
    }
    
    public void setRideId(int value) {
        this.rideId=value;
    }
    
    public double getDepLat() {
        return departure_latitude;
    }

    public void setDepLat(double value) {
        this.departure_latitude = value;
    }

    public double getDepLon() {
        return departure_longitude;
    }

    public void setDepLon(double value) {
        this.departure_longitude = value;
    }

    public double getDestLat() {
        return destination_latitude;
    }

    public void setDestLat(double value) {
        this.destination_latitude = value;
    }

    public double getDestLon() {
        return destination_longitude;
    }

    public void setDestLon(double value) {
        this.destination_longitude = value;
    }

    public Timestamp getDepartureTime() {
        return departure_time;
    }

    public void setDepartureTime(Timestamp value) {
        this.departure_time = value;
    }
   
    public List<Coordinate> getRidePoints() {
        return ridePoints;
    }

    public void setRidePoints(List<Coordinate> points) {
        this.ridePoints = points;
    }

    public double getRideDistance() {
        return this.rideDistance;
    }

    public void setRideDistance(double value) {
        this.rideDistance = value;
    }
    
    public long getRideDuration() {
        return this.rideDuration;
    }

    public void setRideDuration(long value) {
        this.rideDuration = value;
    }
    
    public Ride(){}
    
    public Ride(double departureLatitude, double departureLongitude, double destinationLatitude, double destinationLongitude, String departureTime) throws ParseException {
        this.departure_latitude = departureLatitude;
        this.departure_longitude = departureLongitude;
        this.destination_latitude = destinationLatitude;
        this.destination_longitude = destinationLongitude;
        if (departureTime != null) {
            this.departure_time = Timestamp.valueOf(departureTime);
            System.out.println(this.departure_time.toString());
        }
        this.ridePoints = new ArrayList<>();
        this.createRide();
    }

    /**
     * This method aims to create a GraghHopper request in order to get shortest path points between departure and destination points
     * returned response to this request contains path info (i.e, ride points, ride distance, ride duration)
     * within this method, a new ride id is assigned
     */
    private void createRide() {
        if (this != null) {
            GHResponse rsp =GraphHopperHelper.createRequest(this.departure_latitude, this.departure_longitude, this.destination_latitude, this.destination_longitude);
            if (rsp.hasErrors()) {
                throw new Error(rsp.getErrors().toString());
            }
            PointList points = rsp.getPoints();
            for (int i = 0; i < points.size(); i++) {
                this.ridePoints.add(new Coordinate(points.getLatitude(i), points.getLongitude(i)));
            }
            this.rideDistance = rsp.getDistance();
            this.rideDuration = rsp.getMillis() / 1000;//millis to seconds
            try {
                this.rideId=RidesManager.getNewRideId();
            } catch (SQLException ex) {
                Logger.getLogger(Ride.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
