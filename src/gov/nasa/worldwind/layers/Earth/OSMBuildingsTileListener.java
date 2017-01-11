/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.nasa.worldwind.layers.Earth;

/**
 *
 * @author sbodmer
 */
public interface OSMBuildingsTileListener {
    public void osmBuildingsLoaded(OSMBuildingsTile btile);
    public void osmBuildingsLoadingFailed(OSMBuildingsTile btile, String reason);
}
