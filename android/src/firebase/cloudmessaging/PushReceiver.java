package firebase.cloudmessaging;

import android.Manifest;
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
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiProperties;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class PushReceiver extends BroadcastReceiver {
    private final String LCAT = "PushReceiver";
    private final String URI = "https://basciano-dev.everyup.it/api";

    private String authorizationToken;
    private RequestQueue requestQueue;
    private FusedLocationProviderClient fusedLocationProvider;

    public PushReceiver() {
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
        final String action = intent.getAction();
        final String id = intent.getStringExtra("inquiry_id");
        final Map<String, Object> body = new HashMap<String, Object>();

        body.put("date", Utils.getCurrentTimeAsISOString());

        if (this.requestQueue == null) {
            this.requestQueue = Volley.newRequestQueue(context);
        }

        if (this.fusedLocationProvider == null) {
            this.fusedLocationProvider = LocationServices.getFusedLocationProviderClient(context);
        }

        if (id == null) {
            Log.w(LCAT, "Unable to find any inquiry_id.. aborting.");
        } else {
            if (action.equals("AREYOUFINE")) {
                body.put("status", "delivered");

                this.forwardReceiveFeedback(id, body);
            } else if (action.equals("GEOCLAIM")) {
                body.put("status", "with_response");

                if (this.hasLocationPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) && this.isLocationEnabled(context)) {
                    this.fusedLocationProvider.getLastLocation().addOnCompleteListener(
                            new OnCompleteListener<Location>() {
                                @Override
                                public void onComplete(@NonNull Task<Location> task) {
                                    Location location = task.getResult();
                                    if (location == null) {
                                        Log.d(LCAT, "Unable to get a valid location, aborting.");
                                    } else {
                                        Map<String, Double> payload = new HashMap<String, Double>();

                                        payload.put("latitude", location.getLatitude());
                                        payload.put("longitude", location.getLongitude());

                                        body.put("payload", payload);

                                        forwardGeoClaimFeedback(id, body);
                                    }
                                }
                            }
                    );
                } else {
                    Log.d(LCAT, "No ACCESS_FINE_LOCATION permissions were granted. Aborting GEOCLAIM feedback.");
                }
            }
        }
    }

    private void forwardReceiveFeedback(String id, Map<String, Object> body) {
        String endpoint = this.URI + "/inquiry/" + id + "/feedback";

        this.postRequest(endpoint, body);
    }

    private void forwardGeoClaimFeedback(String id, Map<String, Object> body) {
        String endpoint = this.URI + "/inquiry/" + id + "/feedback";

        this.postRequest(endpoint, body);
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