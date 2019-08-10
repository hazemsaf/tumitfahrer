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
public class RideRequest extends Ride{

    public RideRequest(double departureLatitude, double departureLongitude, double destinationLatitude, double destinationLongitude, String departureTime) throws ParseException {
        super(departureLatitude, departureLongitude, destinationLatitude, destinationLongitude, departureTime);
    }
    
}
