/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.TUMitfahrer.tools;


import com.TUMitfahrer.drs.Ride;
import com.TUMitfahrer.drs.RideDatabaseManager;
import com.TUMitfahrer.drs.mainForm;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.gui.jmapviewer.JMapViewer;
import org.openstreetmap.gui.jmapviewer.JMapViewerTree;
import org.openstreetmap.gui.jmapviewer.Layer;
import org.openstreetmap.gui.jmapviewer.LayerGroup;
import org.openstreetmap.gui.jmapviewer.MapMarkerCircle;
import org.openstreetmap.gui.jmapviewer.MapMarkerDot;
import org.openstreetmap.gui.jmapviewer.OsmTileLoader;
import org.openstreetmap.gui.jmapviewer.Style;
import org.openstreetmap.gui.jmapviewer.events.JMVCommandEvent;
import org.openstreetmap.gui.jmapviewer.interfaces.JMapViewerEventListener;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.BingAerialTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.MapQuestOpenAerialTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.MapQuestOsmTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.OsmTileSource;

/**
 *
 * @author Hazem
 */
public class CustomJMapViewer extends JFrame implements JMapViewerEventListener {

    private static final long serialVersionUID = 1L;
    private static List<Coordinate> searchRide = null;
    private List<Ride> routes = null;
    private JMapViewerTree treeMap = null;

    private JLabel zoomLabel = null;
    private JLabel zoomValue = null;

    private JLabel mperpLabelName = null;
    private JLabel mperpLabelValue = null;
    private LayerGroup systemRides ;
    private LayerGroup searchGroup;
    private static double radius;
    /**
     * Constructs the {@code }.
     */
    public static void setRideSearchPoints(List<Coordinate> points) {
        searchRide = points;
    }
    public static void setRideRadius(double rad){
        radius=rad;
    }

    public CustomJMapViewer() {
        super("Dynamic Ride Sharing");
        setSize(400, 400);
        if(RideDatabaseManager.connect())
        {
            if(GraphHopperHelper.loadHopper())
            {
                mainForm form = new mainForm();
                form.setVisible(true);
                form.setAlwaysOnTop (true);
            }
            else
            {
                this.dispose();
                return;
            }
        }
        else
        {
            this.dispose();
            return;
        }
        treeMap = new JMapViewerTree("Nodes");
        systemRides = new LayerGroup("Rides");
        searchGroup = new LayerGroup("Search");
        

        // Listen to the map viewer for user operations so components will
        // receive events and update
        map().addJMVListener(this);

        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        JPanel panel = new JPanel();
        JPanel panelTop = new JPanel();
        JPanel panelBottom = new JPanel();
        JPanel helpPanel = new JPanel();

        JButton refreshRides = new JButton("Get Rides");
        refreshRides.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                routes = null;
                try {
                    getNewRides();
                } catch (SQLException ex) {
                    Logger.getLogger(CustomJMapViewer.class.getName()).log(Level.SEVERE, null, ex);
                }

                
            }
        });

        mperpLabelName = new JLabel("Meters/Pixels: ");
        mperpLabelValue = new JLabel(String.format("%s", map().getMeterPerPixel()));

        zoomLabel = new JLabel("Zoom: ");
        zoomValue = new JLabel(String.format("%s", map().getZoom()));

        add(panel, BorderLayout.NORTH);
        add(helpPanel, BorderLayout.SOUTH);
        panel.setLayout(new BorderLayout());
        panel.add(panelTop, BorderLayout.NORTH);
        panel.add(panelBottom, BorderLayout.SOUTH);
        JLabel helpLabel = new JLabel("Use right mouse button to move,\n "
                + "left double click or mouse wheel to zoom.");
        helpPanel.add(helpLabel);
        JButton button = new JButton("setDisplayToFitMapMarkers");
        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                map().setDisplayToFitMapMarkers();
            }
        });
        JComboBox<TileSource> tileSourceSelector = new JComboBox<>(new TileSource[]{
            new OsmTileSource.Mapnik(),
            new OsmTileSource.CycleMap(),
            new BingAerialTileSource(),
            new MapQuestOsmTileSource(),
            new MapQuestOpenAerialTileSource()});
        tileSourceSelector.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                map().setTileSource((TileSource) e.getItem());
            }
        });
        JComboBox<TileLoader> tileLoaderSelector;
        tileLoaderSelector = new JComboBox<>(new TileLoader[]{new OsmTileLoader(map())});
        tileLoaderSelector.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                map().setTileLoader((TileLoader) e.getItem());
            }
        });
        map().setTileLoader((TileLoader) tileLoaderSelector.getSelectedItem());
        panelTop.add(tileSourceSelector);
        panelTop.add(tileLoaderSelector);
        final JCheckBox showMapMarker = new JCheckBox("Map markers visible");
        showMapMarker.setSelected(map().getMapMarkersVisible());
        showMapMarker.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                map().setMapMarkerVisible(showMapMarker.isSelected());
            }
        });
        panelBottom.add(showMapMarker);
        ///
        final JCheckBox showTreeLayers = new JCheckBox("Tree Layers visible");
        showTreeLayers.setSelected(true);
        treeMap.setTreeVisible(true);
        showTreeLayers.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                treeMap.setTreeVisible(showTreeLayers.isSelected());
            }
        });
        panelBottom.add(showTreeLayers);
        ///
        final JCheckBox showToolTip = new JCheckBox("ToolTip visible");
        showToolTip.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                map().setToolTipText(null);
            }
        });
        panelBottom.add(showToolTip);
        ///
        final JCheckBox showTileGrid = new JCheckBox("Tile grid visible");
        showTileGrid.setSelected(map().isTileGridVisible());
        showTileGrid.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                map().setTileGridVisible(showTileGrid.isSelected());
            }
        });
        panelBottom.add(showTileGrid);
        final JCheckBox showZoomControls = new JCheckBox("Show zoom controls");
        showZoomControls.setSelected(map().getZoomControlsVisible());
        showZoomControls.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                map().setZoomContolsVisible(showZoomControls.isSelected());
            }
        });
        panelBottom.add(showZoomControls);
        final JCheckBox scrollWrapEnabled = new JCheckBox("Scrollwrap enabled");
        scrollWrapEnabled.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                map().setScrollWrapEnabled(scrollWrapEnabled.isSelected());
            }
        });
        panelBottom.add(scrollWrapEnabled);
        panelBottom.add(button);

        panelTop.add(zoomLabel);
        panelTop.add(zoomValue);
        panelTop.add(mperpLabelName);
        panelTop.add(mperpLabelValue);
        panelTop.add(refreshRides);
        add(treeMap, BorderLayout.CENTER);
        
        //LayerGroup germanyGroup = new LayerGroup("Germany");
//        Layer germanyWestLayer = germanyGroup.addLayer("Germany West");
//        Layer germanyEastLayer = germanyGroup.addLayer("Germany East");
//        MapMarkerDot eberstadt = new MapMarkerDot(germanyEastLayer, "Eberstadt", 49.814284999, 8.642065999);
//        MapMarkerDot ebersheim = new MapMarkerDot(germanyWestLayer, "Ebersheim", 49.91, 8.24);
//        MapMarkerDot empty = new MapMarkerDot(germanyEastLayer, 49.71, 8.64);
//        MapMarkerDot darmstadt = new MapMarkerDot(germanyEastLayer, "Darmstadt", 49.8588, 8.643);
//        map().addMapMarker(eberstadt);
//        map().addMapMarker(ebersheim);
//        map().addMapMarker(empty);
//        Layer franceLayer = treeMap.addLayer("France");
//        map().addMapMarker(new MapMarkerDot(franceLayer, "La Gallerie", 48.71, -1));
//        map().addMapMarker(new MapMarkerDot(43.604, 1.444));
//        map().addMapMarker(new MapMarkerCircle(53.343, -6.267, 0.666));
//        map().addMapRectangle(new MapRectangleImpl(new Coordinate(53.343, -6.267), new Coordinate(43.604, 1.444)));
        //map().addMapMarker(darmstadt);
//        treeMap.addLayer(germanyWestLayer);
//        treeMap.addLayer(germanyEastLayer);
        //getNewRides();
        
  
        //MapPolygon bermudas = new MapPolygonImpl(c(49,1), c(45,10), c(40,5));
        //map().addMapPolygon( bermudas );
//        map().addMapPolygon( new MapPolygonImpl(germanyEastLayer, "Riedstadt", ebersheim, darmstadt, eberstadt, empty));
//
//        map().addMapMarker(new MapMarkerCircle(germanyWestLayer, "North of Suisse", new Coordinate(48, 7), .5));
//        Layer spain = treeMap.addLayer("Spain");
//        map().addMapMarker(new MapMarkerCircle(spain, "La Garena", new Coordinate(40.4838, -3.39), .002));
//        spain.setVisible(false);
//
//        Layer wales = treeMap.addLayer("UK");
//        map().addMapRectangle(new MapRectangleImpl(wales, "Wales", c(53.35,-4.57), c(51.64,-2.63)));

        // map.setDisplayPosition(new Coordinate(49.807, 8.6), 11);
        // map.setTileGridVisible(true);
        Coordinate c = new Coordinate(48.1333, 11.5667);
        map().setDisplayPosition(c, 12);
        map().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    map().getAttribution().handleAttribution(e.getPoint(), true);
                }
            }
        });

        map().addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point p = e.getPoint();
                boolean cursorHand = map().getAttribution().handleAttributionCursor(p);
                if (cursorHand) {
                    map().setCursor(new Cursor(Cursor.HAND_CURSOR));
                } else {
                    map().setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
                if (showToolTip.isSelected()) {
                    map().setToolTipText(map().getPosition(p).toString());
                }
            }
        });
    }

    private JMapViewer map() {
        return treeMap.getViewer();
    }


    private void getNewRides() throws SQLException {        
        map().removeAllMapPolygons();
        treeMap.removeAll();
        treeMap.validate();
        
        systemRides = new LayerGroup("Rides");
        searchGroup = new LayerGroup("Search");
        
        Style yellowStyle = new Style();
            yellowStyle.setStroke(new BasicStroke(20));
            yellowStyle.setBackColor(Color.yellow);
            Style redStyle = new Style();
            redStyle.setStroke(new BasicStroke(20));
            redStyle.setBackColor(Color.red);
        
            
            Layer searchLayer = searchGroup.addLayer("Path");
            
            if(searchRide!=null)
            {
                
                Style style = new Style();
                style.setColor(Color.red);
                style.setStroke(new BasicStroke(4));
                MapPolyLine poly = new MapPolyLine(searchLayer, searchRide,style);
                //add circle at the start point
                map().addMapMarker(new MapMarkerCircle(searchLayer, "", new Coordinate(poly.getPoints().get(0).getLat(), poly.getPoints().get(0).getLon()),radius/100000));
                //add circle at the end point
                map().addMapMarker(new MapMarkerCircle(searchLayer, "", new Coordinate(poly.getPoints().get(poly.getPoints().size()-1).getLat(), poly.getPoints().get(poly.getPoints().size()-1).getLon()),radius/100000));

                map().addMapPolygon(poly);
                treeMap.addLayer(searchLayer);
            }
            
     
            if (routes == null) {
                //polygons = GraphHopperHelper.getRidesPolygons();
                routes=RideDatabaseManager.getRidesFromDatabase();
            }
            
            for (int i = 0; i < routes.size(); i++) {
                Layer ridesLayer = systemRides.addLayer("Ride: " + String.valueOf(routes.get(i).getRideId()));
                MapPolyLine poly = new MapPolyLine(ridesLayer, routes.get(i).getRidePoints());
                map().addMapPolygon(poly);
                //draw departure point
                

                
                MapMarkerDot pointA = new MapMarkerDot(ridesLayer, "a id:"+routes.get(i).getRideId(), new Coordinate(poly.getPoints().get(0).getLat(),poly.getPoints().get(0).getLon()),yellowStyle);
                map().addMapMarker(pointA);
                //draw distenation point
                
                MapMarkerDot pointB = new MapMarkerDot(ridesLayer, "b", new Coordinate(poly.getPoints().get(poly.getPoints().size()-1).getLat(),poly.getPoints().get(poly.getPoints().size()-1).getLon()),redStyle);
                map().addMapMarker(pointB);
                treeMap.addLayer(ridesLayer);
            }
            

        treeMap.setTreeVisible(true);
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        // java.util.Properties systemProperties = System.getProperties();
        // systemProperties.setProperty("http.proxyHost", "localhost");
        // systemProperties.setProperty("http.proxyPort", "8008");
        new CustomJMapViewer().setVisible(true);
    }

    private void updateZoomParameters() {
        if (mperpLabelValue != null) {
            mperpLabelValue.setText(String.format("%s", map().getMeterPerPixel()));
        }
        if (zoomValue != null) {
            zoomValue.setText(String.format("%s", map().getZoom()));
        }
    }

    @Override
    public void processCommand(JMVCommandEvent command) {
        if (command.getCommand().equals(JMVCommandEvent.COMMAND.ZOOM)
                || command.getCommand().equals(JMVCommandEvent.COMMAND.MOVE)) {
            updateZoomParameters();
        }
    }

}
