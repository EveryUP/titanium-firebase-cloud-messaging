package firebase.cloudmessaging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PushActionReceiver extends BroadcastReceiver {
    private final String LCAT = "PushActionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

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
    }
}