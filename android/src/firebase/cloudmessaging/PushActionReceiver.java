package firebase.cloudmessaging;

import android.Manifest;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiProperties;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class PushActionReceiver extends BroadcastReceiver {
    private final String LCAT = "PushActionReceiver";
    private final String URI = "https://basciano-dev.everyup.it/api";

    private String authorizationToken;
    private RequestQueue requestQueue;
    private FusedLocationProviderClient fusedLocationProvider;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    public PushActionReceiver() {
        super();

        TiProperties applicationProperties = TiApplication.getInstance().getAppProperties();

        try {
            JSONObject authorizationValues = new JSONObject(applicationProperties.getString("authorization_values", "{}"));

            this.authorizationToken = authorizationValues.getString("token");
        } catch (JSONException exception) {
            Log.d(LCAT, "Unable to get authorization values:");
            Log.d(LCAT, exception.getMessage());
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String id = intent.getStringExtra("inquiry_id");
        int notification = intent.getIntExtra("notification_id", -1);

        if (this.requestQueue == null) {
            this.requestQueue = Volley.newRequestQueue(context);
        }

        if (id == null) {
            Log.w(LCAT, "Unable to find any inquiry_id.. aborting.");
        } else {
            if (action.equals("IM_FINE")) {
                this.replyToAreYouFine(context, id, "im_fine");
            } else if (action.equals("NEED_HELP")) {
                this.replyToAreYouFine(context, id, "need_help");
            } else if (action.equals("GEO_CLAIM")) {
                Log.d(LCAT, "Handling geo claim");
            } else {
                Log.d(LCAT, "Unknown action to do.");
            }

            Intent closeNotificationIntent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            context.sendBroadcast(closeNotificationIntent);

            if (notification > -1) {
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancel(notification);
            }
        }
    }

    private void replyToAreYouFine(Context context, String id, String response) {
        final Map<String, Object> body = new HashMap<String, Object>();
        final Map<String, Object> payload = new HashMap<String, Object>();

        final String endpoint = this.URI + "/inquiry/" + id + "/feedback";

        payload.put("status", response);

        body.put("status", "with_response");
        body.put("payload", payload);
        body.put("date", Utils.getCurrentTimeAsISOString());

        if (this.hasLocationPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) && this.isLocationEnabled(context)) {
            if (this.fusedLocationProvider == null) {
                this.fusedLocationProvider = LocationServices.getFusedLocationProviderClient(context);
                this.locationRequest = LocationRequest.create()
                        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                        .setInterval(10 * 1000)
                        .setFastestInterval(1 * 1000);

                this.locationCallback = new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        if (locationResult == null) {
                            Log.d(LCAT, "Unable to get a valid location. Sending AREYOUFINE feedback without location data.");

                            postRequest(endpoint, body);

                            return;
                        }

                        Log.d(LCAT, "Got fresh new location data..");

                        for (Location location : locationResult.getLocations()) {
                            if (location != null) {
                                payload.put("latitude", location.getLatitude());
                                payload.put("longitude", location.getLongitude());

                                Log.d(LCAT, "Latitude and longitude were correctly added to the request payload.");
                            }
                        }

                        postRequest(endpoint, body);

                        fusedLocationProvider.removeLocationUpdates(locationCallback);
                    }
                };
            }

            this.fusedLocationProvider.getLastLocation().addOnCompleteListener(
                new OnCompleteListener<Location>() {
                    @Override
                public void onComplete(@NonNull Task<Location> task) {
                    Location location = task.getResult();
                    if (location == null) {
                        Log.d(LCAT, "Unable to get a valid location, requesting new one.");
                        fusedLocationProvider.requestLocationUpdates(locationRequest, locationCallback, null);
                    } else {
                        payload.put("latitude", location.getLatitude());
                        payload.put("longitude", location.getLongitude());

                        postRequest(endpoint, body);
                    }
                    }
                }
            );
        } else {
            Log.d(LCAT, "No ACCESS_FINE_LOCATION permissions were granted. Sending AREYOUFINE feedback without location data.");

            this.postRequest(endpoint, body);
        }
    }

    private void postRequest(String url, Map<String, Object> body) {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, new JSONObject(body), new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.d(LCAT, "Feedback sent");
                Log.d(LCAT, response.toString());
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                VolleyLog.d(LCAT, "Error: " + error.getMessage());
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();

                params.put("Authorization", "JWT " + authorizationToken);

                return params;
            }
        };

        Log.d(LCAT, "Sending request..");

        this.requestQueue.add(request);
    }

    private boolean hasLocationPermission(Context context, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    private boolean isLocationEnabled(Context context){
        LocationManager locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
}