/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.nasa.worldwind.layers.Earth;

import gov.nasa.worldwind.*;
import gov.nasa.worldwind.cache.FileStore;
import gov.nasa.worldwind.formats.geojson.GeoJSONDoc;
import gov.nasa.worldwind.geom.*;
import gov.nasa.worldwind.render.*;
import gov.nasa.worldwind.render.airspaces.SurfaceBox;
import gov.nasa.worldwind.retrieve.*;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Thread to download the gson data
 *
 * @author sbodmer
 */
public class OSMBuildingsTile implements RetrievalPostProcessor, Runnable
{
    /**
     * Use the OSMBuildings key specified for WW
     */
    // static final String OSMBUILDINGS_URL = "http://[abcd].data.osmbuildings.org/0.2/sx3pxpz6/tile"
    //--- OBSOLETE Use https://github.com/sbodmer/JWorldWindEarth instead
    static final String OSMBUILDINGS_URL = "";

    // 15/16942/11632.json"
    /**
     * The counter for the different servers
     */
    static int current = 0;

    int x = 0;
    int y = 0;
    int level = 15;
    double defaultHeight = 10;
    Position center = null;
    FileStore store = null;
    boolean retrieveRemoteData = true;
    long expireDate = 0;
    String cachePath = "";

    /**
     * The loading timestamp
     */
    long ts = 0;

    OSMBuildingsTileListener listener = null;

    /**
     * The loaded buildings
     */
    OSMBuildingsRenderable renderable = null;

    /**
     * The tile bounding box
     */
    Extent bb = null;

    ExtrudedPolygon tile = null;

    public OSMBuildingsTile(int level, int x, int y, OSMBuildingsTileListener listener, Position center,
        FileStore store, boolean retrieveRemoteData, long expireDate, double defaultHeight)
    {
        this.x = x;
        this.y = y;
        this.level = level;
        this.center = center;
        this.listener = listener;
        this.store = store;
        this.retrieveRemoteData = retrieveRemoteData;
        this.defaultHeight = defaultHeight;
        this.expireDate = expireDate;

        cachePath = OSMBuildingsLayer.CACHE_FOLDER + File.separatorChar + level + File.separatorChar + x
            + File.separatorChar + y + ".json";

        //--- Create the surface box for tile information
        tile = new ExtrudedPolygon();
        List<LatLon> list = new ArrayList<LatLon>();
        /*
        double lat = (y*180d/OSMBuildingsLayer.maxY)-90d;
        double dy = 180d/OSMBuildingsLayer.maxX;
        double lon = (x*360d/OSMBuildingsLayer.maxX)-180d;
        double dx = 360d/OSMBuildingsLayer.maxX;
        */
        double lat1 = OSMBuildingsLayer.y2lat(y + 1, OSMBuildingsLayer.ZOOM);
        double lon1 = OSMBuildingsLayer.x2lon(x, OSMBuildingsLayer.ZOOM);
        double lat2 = OSMBuildingsLayer.y2lat(y, OSMBuildingsLayer.ZOOM);
        double lon2 = OSMBuildingsLayer.x2lon(x + 1, OSMBuildingsLayer.ZOOM);
        LatLon p1 = LatLon.fromDegrees(lat1, lon1);       //--- Bottom left
        LatLon p2 = LatLon.fromDegrees(lat2,lon1);     //--- Top left
        LatLon p3 = LatLon.fromDegrees(lat2,lon2);  //--- Top right
        LatLon p4 = LatLon.fromDegrees(lat1,lon2);     //--- Bottom right
        list.add(p1);
        list.add(p2);
        list.add(p3);
        list.add(p4);
        list.add(p1);
        tile.setOuterBoundary(list);
        tile.setVisible(true);
        tile.setHeight(100d);
        tile.setAltitudeMode(WorldWind.CONSTANT);
        tile.setEnableCap(true);
        ShapeAttributes att = new BasicShapeAttributes();
        att.setInteriorOpacity(0.2d);
        att.setEnableLighting(false);
        att.setOutlineMaterial(Material.BLACK);
        att.setOutlineWidth(1d);
        att.setInteriorMaterial(Material.GREEN);
        att.setDrawInterior(true);
        att.setDrawOutline(false);
        tile.setSideAttributes(att);
        tile.setAttributes(att);
        // System.out.println("P1:"+p1+" P2:"+p2+" P3:"+p3+" P4:"+p4);
    }

    @Override
    public String toString()
    {
        return "" + x + "x" + y + "@" + level;
    }

    //**************************************************************************
    //*** API
    //**************************************************************************

    /**
     * Start the data fetch via HTTPRetriever
     */
    public void fetch()
    {
        try
        {
            if (listener != null)
                listener.osmBuildingsLoading(this);

            //--- Check in local file store first
            URL data = store.findFile(cachePath, false);
            if (data != null)
            {
                long now = System.currentTimeMillis();
                File f = new File(data.toURI());
                if (f.lastModified() < now - expireDate)
                {
                    f.delete();
                }
                else
                {
                    // System.out.println("FOUND LOCAL json");
                    WorldWind.getTaskService().addTask(this);
                    return;
                }
            }

            //--- Retreive data from remote server
            String s = OSMBUILDINGS_URL;
            //--- Find the current server to use
            int i1 = s.indexOf('[');
            if (i1 != -1)
            {
                int i2 = s.indexOf(']');
                String sub = s.substring(i1 + 1, i2);
                int l = sub.length();
                current++;
                if (current >= sub.length())
                    current = 0;
                char c = sub.charAt(current);
                s = s.replaceAll("\\[" + sub + "\\]", "" + c);
            }

            s += "/15/" + x + "/" + y + ".json";
            HTTPRetriever r  = new HTTPRetriever(new URL(s), this);
            r.setConnectTimeout(10000);
            r.setReadTimeout(20000);
            WorldWind.getRetrievalService().runRetriever(r);



        }
        catch (MalformedURLException ex)
        {
            //--- Failed
            if (listener != null)
                listener.osmBuildingsLoadingFailed(this, ".json file could not be found : " + ex.getMessage());

        } catch (URISyntaxException ex) {
            //---
            //--- Failed
            if (listener != null)
                listener.osmBuildingsLoadingFailed(this, ".json file could not be found : " + ex.getMessage());

        }
    }

    public Renderable getRenderable()
    {
        /*
        ShapeAttributes a4 = new BasicShapeAttributes();
        a4.setInteriorOpacity(1);
        a4.setEnableLighting(true);
        a4.setOutlineMaterial(Material.RED);
        // a4.setOutlineWidth(2d);
        a4.setDrawInterior(true);
        a4.setDrawOutline(false);
        Cylinder c = new Cylinder(center, 100, 100);
        c.setAltitudeMode(WorldWind.CLAMP_TO_GROUND);
        c.setAttributes(a4);
        c.setVisible(true);
         */
        return renderable;
    }

    /**
     * Return the tile footprint as a surface on the ground
     *
     * @return
     */
    public Renderable getTileSurfaceRenderable() {
        return tile;
    }
    /**
     * Tick tile usage
     */
    public void tick()
    {
        ts = System.currentTimeMillis();
    }

    public long getLastUsed()
    {
        return ts;
    }

    //******************************************************************************************************************
    //*** RetrievalPostProcessor
    //******************************************************************************************************************

    /**
     * Dump the data to local file store, create the renderable and call the layer for rendering
     *
     * @param retriever
     *
     * @return
     */
    @Override
    public ByteBuffer run(Retriever retriever)
    {
        HTTPRetriever hr = (HTTPRetriever) retriever;

        try
        {
            if (hr.getResponseCode() == HttpURLConnection.HTTP_OK)
            {
                byte b[] = hr.getBuffer().array();
                if (b.length == 0) return null;

                //--- Store to cache file
                File f = store.newFile(cachePath);
                FileOutputStream fout = new FileOutputStream(f);
                //--- The buffer contains trailling 0000, so convert to string to remove it
                //--- Why is that, no idea ???
                String tmp = new String(b, "UTF-8").trim();
                fout.write(tmp.getBytes("UTF-8"));
                fout.close();

                //--- Load the data
                GeoJSONDoc doc = new GeoJSONDoc(f.toURI().toURL());
                doc.parse();

                renderable = new OSMBuildingsRenderable(doc, defaultHeight);
                if (listener != null)
                    listener.osmBuildingsLoaded(this);
            } else {
                //--- Wrong http response
                if (listener != null)
                    listener.osmBuildingsLoadingFailed(this, ".json file could not be found, wrong http response : " + hr.getResponseCode()+" "+hr.getResponseMessage());
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            //--- Failed
            if (listener != null)
                listener.osmBuildingsLoadingFailed(this, ".json file could not be found : " + ex.getMessage());
        }
        return null;
    }

    //**************************************************************************
    //*** Runnable
    //**************************************************************************

    /**
     * Load local cached file
     */
    public void run()
    {
        try
        {
            URL data = store.findFile(cachePath, false);

            //--- Load the data
            GeoJSONDoc doc = new GeoJSONDoc(data);
            doc.parse();

            renderable = new OSMBuildingsRenderable(doc, defaultHeight);
            if (listener != null)
                listener.osmBuildingsLoaded(this);
        }
        catch (NullPointerException ex)
        {
            //--- File is no more in local storage ?
            if (listener != null)
                listener.osmBuildingsLoadingFailed(this, ".json file could not be found");
        }
        catch (IOException ex)
        {
            //--- Failed
            if (listener != null)
                listener.osmBuildingsLoadingFailed(this, ".json file could not be found");
        }
    }
}
