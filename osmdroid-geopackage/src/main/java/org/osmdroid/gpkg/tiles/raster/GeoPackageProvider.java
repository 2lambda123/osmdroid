package org.osmdroid.gpkg.tiles.raster;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.osmdroid.api.IMapView;
import org.osmdroid.tileprovider.IMapTileProviderCallback;
import org.osmdroid.tileprovider.IRegisterReceiver;
import org.osmdroid.tileprovider.MapTileProviderArray;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.modules.IFilesystemCache;
import org.osmdroid.tileprovider.modules.INetworkAvailablityCheck;
import org.osmdroid.tileprovider.modules.NetworkAvailabliltyCheck;
import org.osmdroid.tileprovider.modules.SqlTileWriter;
import org.osmdroid.tileprovider.modules.TileWriter;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.BoundingBox;

import java.io.File;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.tiles.user.TileDao;
import mil.nga.proj.ProjectionTransform;

/**
 * GeoPackage +
 * created on 1/5/2017.
 *
 * @author Alex O'Ree
 */

public class GeoPackageProvider extends MapTileProviderArray implements IMapTileProviderCallback {

    protected GeoPackageMapTileModuleProvider geopackage;
    protected IFilesystemCache tileWriter;

    public GeoPackageProvider(File[] db, Context context) {
        this(new SimpleRegisterReceiver(context), new NetworkAvailabliltyCheck(context),
                TileSourceFactory.DEFAULT_TILE_SOURCE, context, null, db);
    }


    public GeoPackageProvider(final IRegisterReceiver pRegisterReceiver,
                              final INetworkAvailablityCheck aNetworkAvailablityCheck, final ITileSource pTileSource,
                              final Context pContext, final IFilesystemCache cacheWriter, File[] databases) {


        super(pTileSource, pRegisterReceiver);
        Log.i(IMapView.LOGTAG, "Geopackage support is BETA. Please report any issues");

        if (cacheWriter != null) {
            tileWriter = cacheWriter;
        } else {
            if (Build.VERSION.SDK_INT < 10) {
                tileWriter = new TileWriter();
            } else {
                tileWriter = new SqlTileWriter();
            }
        }

        mTileProviderList.add(MapTileProviderBasic.getMapTileFileStorageProviderBase(pRegisterReceiver, pTileSource, tileWriter));
        geopackage = new GeoPackageMapTileModuleProvider(databases, pContext, tileWriter);
        mTileProviderList.add(geopackage);


    }

    public GeoPackageMapTileModuleProvider geoPackageMapTileModuleProvider() {
        return geopackage;
    }


    @Override
    public IFilesystemCache getTileWriter() {
        return tileWriter;
    }

    @Override
    public void detach() {
        //https://github.com/osmdroid/osmdroid/issues/213
        //close the writer
        if (tileWriter != null)
            tileWriter.onDetach();
        tileWriter = null;
        geopackage.detach();
        super.detach();
    }

    public GeopackageRasterTileSource getTileSource(String database, String table) {
        for (GeoPackage next : geopackage.tileSources) {
            if (next.getName().equalsIgnoreCase(database)) {
                //found the database
                if (next.getTileTables().contains(table)) {
                    //find the tile table
                    TileDao tileDao = next.getTileDao(table);
                    mil.nga.geopackage.BoundingBox boundingBox = tileDao.getBoundingBox();
                    ProjectionTransform transformation = tileDao.getProjection().getTransformation(tileDao.getProjection());
                    double[] transformed = transformation.transform(boundingBox.getMinLongitude(), boundingBox.getMinLatitude(), boundingBox.getMaxLongitude(), boundingBox.getMaxLatitude());
                    boundingBox = new mil.nga.geopackage.BoundingBox(transformed[0], transformed[1], transformed[2], transformed[3]);
                    BoundingBox bounds = new BoundingBox(boundingBox.getMaxLatitude(), boundingBox.getMaxLongitude(), boundingBox.getMinLatitude(), boundingBox.getMinLongitude());
                    return new GeopackageRasterTileSource(database, table, (int) tileDao.getMinZoom(), (int) tileDao.getMaxZoom(), bounds);
                }
            }
        }

        return null;
    }


    @Override
    public void setTileSource(final ITileSource aTileSource) {
        super.setTileSource(aTileSource);
        geopackage.setTileSource(aTileSource);
    }


}
