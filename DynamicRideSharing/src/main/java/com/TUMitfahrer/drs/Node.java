/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.TUMitfahrer.drs;
import java.sql.SQLException;

/**
 *
 * @author Hazem
 */
public class Node {

    public double longitude;
    public double latitude;
    
    public Node(double longitude,double latitude)
    {
        this.longitude=longitude;
        this.latitude=latitude;
    }
    
    public void createNodeInDatabase() throws SQLException
    {
        
          
    }


    
}
