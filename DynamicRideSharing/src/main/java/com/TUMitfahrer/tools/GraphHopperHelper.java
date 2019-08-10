/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.TUMitfahrer.tools;


import com.google.code.geocoder.Geocoder;
import com.google.code.geocoder.GeocoderRequestBuilder;
import com.google.code.geocoder.model.GeocodeResponse;
import com.google.code.geocoder.model.GeocoderRequest;
import com.google.code.geocoder.model.GeocoderResult;
import com.google.code.geocoder.model.LatLng;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.shapes.GHPoint;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.List;
import java.util.logging.Level;

/**
 *
 * @author Hazem
 */
public class GraphHopperHelper {

    private static GraphHopper hopper;
    //public static Logger LOGGER = Logger.getLogger("InfoLogging");
    
    public static boolean loadHopper() {
        hopper = new GraphHopper().forServer();
        hopper.setInMemory();
        Logger.getLogger(GraphHopperHelper.class.getName()).log(Level.INFO,"Setting map file location...");
        hopper.setOSMFile("../Map/bayern-latest.osm.pbf");
        // where to store graphhopper files?
        hopper.setGraphHopperLocation("../Map/bayern-latest.osm-gh2");
        hopper.setEncodingManager(new EncodingManager("car"));
        // now this can take minutes if it imports or a few seconds for loading
        Logger.getLogger(GraphHopperHelper.class.getName()).log(Level.INFO,"Importing map file...");
        hopper.importOrLoad();
        Logger.getLogger(GraphHopperHelper.class.getName()).log(Level.INFO,"Map imported successfully!");
        return true;
        // simple configuration of the request object, see the GraphHopperServlet classs for more possibilities.  
    }

    public static GraphHopper getHopper() {
        return hopper;
    }

    public static GHResponse createRequest(double departure_latitude, double departure_longitude, double destination_latitude, double destination_longitude)
    {
            GHRequest req = new GHRequest(departure_latitude, departure_longitude, destination_latitude, destination_longitude).
                    setWeighting("fastest").
                    setVehicle("car");
            GHResponse rsp = hopper.route(req);
            if (rsp.hasErrors()) {
                throw new Error(rsp.getErrors().toString());
            }
            return rsp;
    }
    public static GHResponse createRequest(List<GHPoint> list)
    {
        GHRequest req = new GHRequest(list).
                    setWeighting("fastest").
                    setVehicle("car");
            GraphHopper hopper = GraphHopperHelper.getHopper();
            GHResponse rsp = hopper.route(req);
            if (rsp.hasErrors()) {
                throw new Error(rsp.getErrors().toString());
            }
            return rsp;
    }
    /**
     * This method returns the travel time between two points A, B
     * @param pointALat point A latitude
     * @param pointALon point A longitude
     * @param pointBLat point B latitude
     * @param pointBLon point B longitude
     * @return distance in Milliseconds
     */
    public static long getTravelTimeBetweenTwoPoints(double pointALat, double pointALon, double pointBLat, double pointBLon)
    {
        GHResponse rsp=createRequest(pointALat, pointALon, pointBLat, pointBLon);
        return rsp.getMillis();    
    }
    
    public static LatLng gecodeAddress(String address){
        Logger.getLogger(GraphHopperHelper.class.getName()).log(Level.INFO,String.format("getting geocode of address: %s", address));
        final Geocoder geocoder = new Geocoder();
        GeocoderRequest geocoderRequest = new GeocoderRequestBuilder().setAddress(address).setLanguage("de").getGeocoderRequest();
        LatLng geocode=null;
        try
        {
            GeocodeResponse geocoderResponse = geocoder.geocode(geocoderRequest);
            List<GeocoderResult> results = geocoderResponse.getResults();
            geocode= results.get(0).getGeometry().getLocation();
        }
        catch(IOException ex)
        {
            Logger.getLogger(GraphHopperHelper.class.getName()).log(Level.SEVERE,String.format("Can not locate address: %s",address),ex);
        }
        return geocode;
    }

}
