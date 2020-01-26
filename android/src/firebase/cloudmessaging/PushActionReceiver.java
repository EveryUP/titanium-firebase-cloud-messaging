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
    private String authorizationToken;
    private RequestQueue requestQueue;

    public PushActionReceiver() {
        super();

        TiProperties applicationProperties = TiApplication.getInstance().getAppProperties();

        try {
            JSONObject authorizationValues = new JSONObject(applicationProperties.getString("authorization_values", "{}"));

            this.authorizationToken = authorizationValues.getString("token");

            Log.d(LCAT, "Authorization token set to: " + this.authorizationToken);
        } catch (JSONException exception) {
            Log.d(LCAT, "Unable to get authorization values:");
            Log.d(LCAT, exception.getMessage());
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        int id = intent.getIntExtra("notification_id", -1);

        JSONObject body = new JSONObject();

        if (this.requestQueue == null) {
            this.requestQueue = Volley.newRequestQueue(context);
        }

        if (action.equals("IM_FINE")) {
            Log.d(LCAT, "Handling im fine");
        } else if (action.equals("NEED_HELP")) {
            Log.d(LCAT, "Handling need help");
        } else if (action.equals("GEO_CLAIM")) {
            Log.d(LCAT, "Handling geo claim");
        } else {
            Log.d(LCAT, "Unknown action to do.");
        }

        Intent closeNotificationIntent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(closeNotificationIntent);
        
        if (id > -1) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(id);
        }
    }

    private void postRequest(String url, final JSONObject body) {
        StringRequest request = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String rawResponse) {
                Log.d(LCAT, rawResponse);
                try {
                    JSONObject response = new JSONObject(rawResponse);

                    // TODO
                } catch (JSONException exception) {
                    Log.e(LCAT, exception.getMessage());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                Log.e(LCAT, volleyError.getMessage());
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();

                params.put("Authorization", "JWT " + authorizationToken);

                return params;
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                try {
                    return body == null ? null : body.toString().getBytes("utf-8");
                } catch (UnsupportedEncodingException exception) {
                    VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", body.toString(), "utf-8");

                    return null;
                }
            }
        };

        this.requestQueue.add(request);
    }
}