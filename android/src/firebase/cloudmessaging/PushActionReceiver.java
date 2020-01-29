package firebase.cloudmessaging;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiProperties;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class PushActionReceiver extends BroadcastReceiver {
    private final String LCAT = "PushActionReceiver";
    private final String URI = "https://basciano-dev.everyup.it/api";

    private String authorizationToken;
    private RequestQueue requestQueue;

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
                this.replyToAreYouFine(id, "im_fine");
            } else if (action.equals("NEED_HELP")) {
                this.replyToAreYouFine(id, "need_help");
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

    private void replyToAreYouFine(String id, String response) {
        String endpoint = this.URI + "/inquiry/" + id + "/feedback";
        Map<String, String> body = new HashMap<String, String>();

        body.put("status", "with_response");
        body.put("payload", response);
        body.put("date", Utils.getCurrentTimeAsISOString());

        this.postRequest(endpoint, body);
    }

    private void postRequest(String url, Map<String, String> body) {
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
}