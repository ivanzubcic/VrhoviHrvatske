package hr.planinarenje.vrhovihrvatske;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.geojson.GeoJsonFeature;
import com.google.maps.android.geojson.GeoJsonGeometry;
import com.google.maps.android.geojson.GeoJsonLayer;
import com.google.maps.android.geojson.GeoJsonPoint;
import com.google.maps.android.geojson.GeoJsonPointStyle;

import org.json.JSONException;

import java.io.IOException;
import java.math.BigDecimal;


public class VrhoviActivity extends Activity implements OnMapReadyCallback,
        LocationListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "LocationActivity";

    private GoogleMap mMap;
    private GeoJsonLayer layer;
    GoogleApiClient mGoogleApiClient;
    Location mCurrentLocation;
    LocationRequest mLocationRequest;

    public static final String VISITED_PEAKS = "VisitedPeaks";
    SharedPreferences sharedPreferencesPeaks;
    String currentPeak = null;

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000 * 10);
        mLocationRequest.setFastestInterval(1000 * 5);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

        sharedPreferencesPeaks = getSharedPreferences(VISITED_PEAKS, Context.MODE_PRIVATE);

        createLocationRequest();
        // ATTENTION: This "addApi(AppIndex.API)"was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(AppIndex.API).build();

        setContentView(R.layout.activity_vrhovi);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
            }
        }

        try {
            layer = new GeoJsonLayer(mMap, R.raw.vrhovi, getApplicationContext());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        setMapMarkerStyle();
        if (layer != null) layer.addLayerToMap();

        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                zoomMapToMapMarkers();
            }
        });

        //add location button click listener
        mMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
            @Override
            public boolean onMyLocationButtonClick() {

                if (mCurrentLocation != null) {
                    GeoJsonFeature featureMinDistance = findNearestGeoJsonLocation();
                    zoomToCurrentLocationAndNearestGeoJsonLocation(featureMinDistance);
                    return true;
                }

                return false;
            }
        });

        mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                createVisitPeakAlertDialog(marker);
            }


        });
    }

    private void createVisitPeakAlertDialog(Marker marker) {
        final CharSequence[] items = { "Osvojen vrh", "Još nisam bil gore" };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Jeste li bili na vrhu \"" + marker.getTitle() + "\"?");
        //TODO zamijeniti s ID-jem kojeg treba dodati u GeoJson
        currentPeak = marker.getTitle();
//        builder.setSingleChoiceItems(items, -1,
//                new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int item) {
//                        Toast.makeText(getApplicationContext(), items[item],
//                                Toast.LENGTH_SHORT).show();
//
//                    }
//                });

        builder.setPositiveButton("Jesam", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                SharedPreferences.Editor editor = sharedPreferencesPeaks.edit();
                editor.putBoolean(currentPeak, true);
                editor.commit();
                setMapMarkerStyle();
                dialog.dismiss();
            }
        });

        builder.setNegativeButton("Nisam", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                SharedPreferences.Editor editor = sharedPreferencesPeaks.edit();
                editor.putBoolean(currentPeak, false);
                editor.commit();
                setMapMarkerStyle();
                dialog.dismiss();
            }
        });

        builder.setNeutralButton("Izlaz", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    private void zoomToCurrentLocationAndNearestGeoJsonLocation(GeoJsonFeature featureMinDistance) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        LatLng mCurrentLoc = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        LatLng featureLatLng = ((GeoJsonPoint) featureMinDistance.getGeometry()).getCoordinates();
        LatLng featureLoc = new LatLng(featureLatLng.latitude, featureLatLng.longitude);
        builder.include(mCurrentLoc);
        builder.include(featureLoc);
        LatLngBounds bounds = builder.build();
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50));
    }

    private GeoJsonFeature findNearestGeoJsonLocation() {
        GeoJsonFeature featureMinDistance = null;

        double minDistance = Integer.MAX_VALUE;
        for (GeoJsonFeature feature : layer.getFeatures()) {

            if (feature.hasGeometry()) {
                GeoJsonGeometry geometry = feature.getGeometry();
                LatLng latLng = ((GeoJsonPoint) geometry).getCoordinates();

                Location featureLocation = new Location("");
                featureLocation.setLatitude(latLng.latitude);
                featureLocation.setLongitude(latLng.longitude);

                if (mCurrentLocation.distanceTo(featureLocation) < minDistance) {
                    minDistance = mCurrentLocation.distanceTo(featureLocation);
                    featureMinDistance = feature;
                }
            }
        }

        BigDecimal niceDistance = BigDecimal.valueOf(minDistance / 1000).setScale(2, BigDecimal.ROUND_HALF_UP);
        Toast.makeText(getApplicationContext(), "Najblizi vrh: " + (featureMinDistance != null ?
                featureMinDistance.getProperty("naziv") + " " + niceDistance + " km" : ""), Toast.LENGTH_LONG).show();

        if(minDistance/1000<0.1){
            Toast.makeText(getApplicationContext(), "Čestitam! Osvojili ste vrh: " + (featureMinDistance != null ?
                    featureMinDistance.getProperty("naziv") : ""), Toast.LENGTH_LONG).show();
            setMapMarkerStyle();
        }

        return featureMinDistance;
    }

    private void setMapMarkerStyle() {
        for (GeoJsonFeature feature : layer.getFeatures()) {
            GeoJsonPointStyle pointStyle = new GeoJsonPointStyle();
            pointStyle.setTitle(feature.getProperty("naziv"));
            pointStyle.setSnippet(feature.getProperty("nv") + " m");
            if (Integer.valueOf(feature.getProperty("nv")) <= 500) {
                pointStyle.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.greenmountain32));
            } else if (Integer.valueOf(feature.getProperty("nv")) > 500
                    && Integer.valueOf(feature.getProperty("nv")) <= 1000) {
                pointStyle.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.yellowmountain32));
            } else if (Integer.valueOf(feature.getProperty("nv")) > 1000
                    && Integer.valueOf(feature.getProperty("nv")) <= 1500) {
                pointStyle.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.bluemountain32));
            } else {
                pointStyle.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.mountain32));
            }

            if(sharedPreferencesPeaks.getBoolean(pointStyle.getTitle(), false))
                pointStyle.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.redmountain32));

            feature.setPointStyle(pointStyle);
        }
    }

    private void zoomMapToMapMarkers() {
        if (layer != null) {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();

            for (GeoJsonFeature feature : layer.getFeatures()) {
                if (feature.hasGeometry()) {
                    GeoJsonGeometry geometry = feature.getGeometry();
                    LatLng latLng = ((GeoJsonPoint) geometry).getCoordinates();
                    builder.include(latLng);
                }
            }

            LatLngBounds bounds = builder.build();
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50));
        }
    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        MenuInflater inflater = getMenuInflater();
//        inflater.inflate(R.menu.menu_items, menu);
//        return true;
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        switch (item.getItemId()) {
//            case R.id.item1:
//                mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
//                return true;
//            default:
//                return super.onOptionsItemSelected(item);
//        }
//    }


    public static final int MY_LOCATION_REQUEST_CODE = 99;

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                //Prompt the user once explanation has been shown
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_LOCATION_REQUEST_CODE);


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_LOCATION_REQUEST_CODE);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == MY_LOCATION_REQUEST_CODE) {
            if (permissions.length == 1 &&
                    permissions[0] == Manifest.permission.ACCESS_FINE_LOCATION &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    mMap.setMyLocationEnabled(true);
                }
            } else {
                // Permission was denied. Display an error message.
                Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart fired ..............");
        mGoogleApiClient.connect();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Vrhovi Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://hr.planinarenje.vrhovihrvatske/http/host/path")
        );
        AppIndex.AppIndexApi.start(mGoogleApiClient, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Vrhovi Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://hr.planinarenje.vrhovihrvatske/http/host/path")
        );
        AppIndex.AppIndexApi.end(mGoogleApiClient, viewAction);
        Log.d(TAG, "onStop fired ..............");
        mGoogleApiClient.disconnect();
        Log.d(TAG, "isConnected ...............: " + mGoogleApiClient.isConnected());
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        startLocationUpdates();
    }

    protected void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            PendingResult<Status> pendingResult = LocationServices.FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient, mLocationRequest, this);
        }
        Log.d(TAG, "Location update started ..............: ");
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
