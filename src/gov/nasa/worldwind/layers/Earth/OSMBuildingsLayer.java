/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.nasa.worldwind.layers.Earth;

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.*;
import gov.nasa.worldwind.cache.BasicDataFileStore;
import gov.nasa.worldwind.cache.FileStore;
import gov.nasa.worldwind.cache.FileStoreFilter;
import gov.nasa.worldwind.event.Message;
import gov.nasa.worldwind.formats.geojson.GeoJSONDoc;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.*;
import gov.nasa.worldwind.retrieve.*;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwindx.examples.util.FileStoreDataSet;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

/**
 * Always assume a zoom level of 15
 *
 * @author sbodmer
 */
public class OSMBuildingsLayer extends RenderableLayer implements OSMBuildingsTileListener, ActionListener
{

    public static final String CACHE_FOLDER = "Earth" + File.separatorChar + "OSMBuildings";

    static final int ZOOM = 15;

    static final double maxX = Math.pow(2, ZOOM);
    static final double maxY = Math.pow(2, ZOOM);

    // LatLon center = null;
    // SurfacePolygon carpet = null;
    ExtrudedPolygon box = null;

    SurfaceCircle cursor = null;

    /**
     * The key is "{level};{x};{y}"
     */
    HashMap<String, OSMBuildingsTile> buildings = new HashMap<String, OSMBuildingsTile>();


    File cacheFolder = null;

    int maxTiles = 10;
    double defaultHeight = 10;
    
    // Cylinder c = null;
    public OSMBuildingsLayer() {
        super();

        //--- Prepare the screen credits
        ScreenCredit sc = new ScreenCreditImage("OSM Buildings", getClass().getResource("/images/OSMBuildings_32x32.png"));
        sc.setLink("http://www.osmbuildings.org");
        sc.setOpacity(1);
        setScreenCredit(sc);


        setExpiryTime(7 * 24 * 60 * 1000);

        // System.out.println("ROOT:"+cacheRoot);
        /*
        List<FileStoreDataSet> dataSets = FileStoreDataSet.getDataSets(cacheRoot);
        for (FileStoreDataSet fileStoreDataSet : dataSets) {
            String cacheName = fileStoreDataSet.getName();
            if (cacheName.contains(sourceName)) {
                fileStoreDataSet.delete(false);
                break;
            }
        }
         */
 /*
        ShapeAttributes a4 = new BasicShapeAttributes();
        a4.setInteriorOpacity(1);
        a4.setEnableLighting(true);
        a4.setOutlineMaterial(Material.RED);
        // a4.setOutlineWidth(2d);
        a4.setDrawInterior(true);
        a4.setDrawOutline(false);
         */
 /*
        c = new Cylinder(new Position(LatLon.fromDegrees(0,0),0), 100, 10);
        c.setAltitudeMode(WorldWind.CLAMP_TO_GROUND);
        c.setAttributes(a4);
        c.setVisible(true);
        addRenderable(c);
         */
 /*
        ArrayList<Position> poss = new ArrayList<>();
        poss.add(Position.fromDegrees(0, 0));
        poss.add(Position.fromDegrees(oneTileX, 0));
        poss.add(Position.fromDegrees(oneTileX, oneTileY));
        poss.add(Position.fromDegrees(0, oneTileY));
        
        carpet = new SurfacePolygon(a4, poss);
        addRenderable(carpet);
         */
 /*
        ShapeAttributes at = new BasicShapeAttributes();
        at.setInteriorMaterial(Material.WHITE);
        // at.setOutlineOpacity(0.5);
        at.setInteriorOpacity(1);
        // at.setOutlineMaterial(Material.GREEN);
        // at.setOutlineWidth(2);
        // at.setDrawOutline(true);
        at.setDrawInterior(true);
        at.setEnableLighting(true);

        ShapeAttributes cap = new BasicShapeAttributes();
        cap.setInteriorMaterial(Material.GRAY);
        cap.setInteriorOpacity(1);
        cap.setDrawInterior(true);
        cap.setEnableLighting(true);

        ArrayList<Position> poss = new ArrayList<>();
        poss.add(Position.fromDegrees(0, 0));
        poss.add(Position.fromDegrees(0, oneTileY));
        poss.add(Position.fromDegrees(oneTileX, oneTileY));
        poss.add(Position.fromDegrees(oneTileX, 0));

        box = new ExtrudedPolygon(10d);
        box.setAltitudeMode(WorldWind.CONSTANT);
        box.setAttributes(at);
        box.setSideAttributes(at);
        box.setCapAttributes(cap);
        box.setVisible(true);
        box.setOuterBoundary(poss);

        addRenderable(box);
         */


        //--- Prepare the cursor
        ShapeAttributes a1 = new BasicShapeAttributes();
        a1.setInteriorMaterial(Material.GREEN);
        a1.setInteriorOpacity(0.5);
        a1.setEnableLighting(false);
        a1.setOutlineMaterial(Material.BLACK);
        a1.setOutlineWidth(2d);
        a1.setDrawInterior(true);
        a1.setDrawOutline(true);
        cursor = new SurfaceCircle(a1, LatLon.ZERO, 20d);
        cursor.setVisible(true);
        addRenderable(cursor);
    }

    //**************************************************************************
    //*** API
    //*************************************************************************
    public void setDefaultBuildingHeight(double defaultHeight) {
        this.defaultHeight = defaultHeight;
        
    }
    
    public void setMaxTiles(int maxTiles) {
        this.maxTiles = maxTiles;
        
    }
    
    //**************************************************************************
    //*** AbstractLayer
    //**************************************************************************
    @Override
    public void dispose() {

        super.dispose();

    }

    @Override
    public boolean isLayerInView(DrawContext dc) {
        dc.addScreenCredit(getScreenCredit());

        return true;
    }

    /**
     * Fetch the buldings data for the center of the viewport
     * 
     * @param dc 
     */
    @Override
    public void doRender(DrawContext dc) {
        
        Position center = dc.getViewportCenterPosition();
        if (center != null) {
            //--- Move cursor to center of viewport
            cursor.moveTo(new Position(center, 0));

            double rx = center.getLongitude().radians;
            double dx = 128 / Math.PI * maxX * (rx + Math.PI);
            int x = (int) dx / 256;

            //--- Rows
            double ry = center.getLatitude().radians;
            double tl = Math.tan(Math.PI / 4d + ry / 2d);
            double dy = 128 / Math.PI * maxY * (Math.PI - Math.log(tl));
            int y = (int) dy / 256;
            // System.out.println("X=" + x + ", Y=" + y);

            //--- Check if max tiles are reached, if so, remove the oldest one
            if (buildings.size() > maxTiles) {
                Iterator<OSMBuildingsTile> it = buildings.values().iterator();
                OSMBuildingsTile oldest = null;
                while (it.hasNext()) {
                    OSMBuildingsTile t = it.next();
                    if (oldest == null) oldest = t;
                    if (t.getLastUsed() < oldest.getLastUsed()) oldest = t;
                }
                Renderable rend = oldest.getRenderable();
                if (rend != null) removeRenderable(rend);
                buildings.remove(oldest.toString());

            }

            String key = x + "x" + y + "@" + ZOOM;
            OSMBuildingsTile t = buildings.get(key);
            if (t == null) {
                t = new OSMBuildingsTile(ZOOM, x, y, this, center, getDataFileStore(), isNetworkRetrievalEnabled(), getExpiryTime(), defaultHeight);
                buildings.put(key, t);
                t.fetch();

            }
            t.tick();
        }

        super.doRender(dc);

    }

    @Override
    public void doPreRender(DrawContext dc) {
        // System.out.println("doPreRender:" + dc);
        // SectorGeometryList gl = dc.getSurfaceGeometry();
        // Sector s = dc.getVisibleSector();
        // System.out.println("Sector:" + s);
        // LatLon poss[] = s.getCorners();
        // Position center = dc.getViewportCenterPosition();
        // fr.getRight().

        // ArrayList<Position> poss = new ArrayList<>();
        /*
        poss.add(Position.fromDegrees(, 0));
        poss.add(Position.fromDegrees(0, oneTileY));
        poss.add(Position.fromDegrees(oneTileX, oneTileY));
        poss.add(Position.fromDegrees(oneTileX, 0));
         */
        // box.setOuterBoundary(Arrays.asList(s.getCorners()));
        // box.moveTo(new Position(center, 0));
        // Vec4 c = dc.getView().getCenterPoint();
        // System.out.println("CENTER:" + center.getLatitude().degrees + "," + center.getLongitude().degrees);
        // System.out.println("Lat:"+c.getY()+" Lon:"+c.getX()+" Alt:"+c.z);
        /*
        Cylinder c = new Cylinder(Position.fromRadians(center.getLatitude(), oneTileX, oneTileX), maxX, maxX)
        BasicShapeAttributes sa = new BasicShapeAttributes();
        SurfacePolyline sp = new SurfacePolyline(sa, s.asList());
        addRenderable(sp);
         */
        super.doPreRender(dc);
    }

    //**************************************************************************
    //*** MessageListener
    //**************************************************************************
    @Override
    public void onMessage(Message msg) {
        // System.out.println("onMessage:" + msg);
    }

    //**************************************************************************
    //*** OSMBuildingsTileListener
    //**************************************************************************
    @Override
    public void osmBuildingsLoaded(OSMBuildingsTile btile) {

        addRenderable(btile.getRenderable());
    }

    @Override
    public void osmBuildingsLoadingFailed(OSMBuildingsTile btile, String reason) {
        // System.out.println("LOADING FAILED:" + btile+" reason:"+reason);
        Logging.logger().log(Level.WARNING, "OSMBuildingsLayer.osmBuildingsLoadingFailed for tile "+btile.toString(), new Object[] {reason});
        buildings.remove(btile);
    }

    //**************************************************************************
    //*** ActionListener
    //**************************************************************************
    @Override
    public void actionPerformed(ActionEvent e) {
        //--- Nothing at the moment
    }

    //**************************************************************************
    //*** Private
    //**************************************************************************
    public static void main(String args[]) {
        // double lat = 52.20276987984823d;
        double lat = 46.1935;
        double lon = 6.129;
        // double lat = 0;

        int level = 15;
        int maxY = 1 << level;
        int maxX = 1 << level;
        double oneY = 180d / maxY;

        //--- https://en.wikipedia.org/wiki/Web_Mercator
        /*
        System.out.println("MAX:" + maxY);
        System.out.println("ONE:" + oneY);
        int y = (int) ((maxY * (lat + 90d)) / 180d);

        System.out.println("Y  :" + y);
        System.out.println("D  :" + (maxY - y));

        double plat = Math.log(Math.tan((Math.PI/4)+(lat/2)));
        System.out.println("PLAT:"+plat);
         */
        //--- Cols (x)
        double rx = Math.toRadians(lon);
        double x = 128 / Math.PI * maxX * (rx + Math.PI);
        System.out.println("COLS:" + (x / 256));

        //--- Rows
        double ry = Math.toRadians(lat);
        double tl = Math.tan(Math.PI / 4d + ry / 2d);
        double y = 128 / Math.PI * maxY * (Math.PI - Math.log(tl));
        System.out.println("ROWS:" + (y / 256));

    }


}
