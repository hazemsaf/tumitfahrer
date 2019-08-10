/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.TUMitfahrer.drs;

import java.text.ParseException;


/**
 *
 * @author Hazem
 */
public class RideOffer extends Ride {

    private int availableSeats;
    private int timeMargin;
    private double maxDetour;
    
    public int getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(int value) {
        this.availableSeats = value;
    }

    public int getTimeMargin() {
        return timeMargin;
    }

    public void setTimeMargin(int value) {
        this.timeMargin = value;
    }

        public void setMaxDetour(double value) {
        this.maxDetour=value;
    }
        
    public double getMaxDetour() {
        return maxDetour;
    }
    
    public RideOffer() {
    }

    public RideOffer(double departureLatitude, double departureLongitude, double destinationLatitude, double destinationLongitude, String departureTime, int timeMargin, int availableSeats,double maxDetour) throws ParseException {
        super(departureLatitude, departureLongitude, destinationLatitude, destinationLongitude, departureTime);
        this.availableSeats = availableSeats;
        this.timeMargin = timeMargin;
        this.maxDetour=maxDetour;
    }
    
    public void deleteRide()
    {
        RidesManager.deleteRide(this.getRideId());
    }
}
