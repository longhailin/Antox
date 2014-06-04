package im.tox.antox.callbacks;

import im.tox.antox.R;
import im.tox.antox.activities.MainActivity;
import im.tox.antox.data.AntoxDB;
import im.tox.antox.tox.ToxSingleton;
import im.tox.antox.utils.Constants;
import im.tox.antox.utils.AntoxFriend;
import im.tox.antox.tox.ToxService;
import im.tox.jtoxcore.callbacks.OnMessageCallback;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class AntoxOnMessageCallback implements OnMessageCallback<AntoxFriend> {
	public static final String TAG = "AntoxOnMessageCallback";
	public static final String MESSAGE = "im.tox.antox.AntoxOnMessageCallback.MESSAGE";
	public static final String KEY = "im.tox.antox.AntoxOnMessageCallback.KEY";
	public static final String FRIEND_NUMBER = "im.tox.antox.AntoxOnMessageCallback.FRIEND_NUMBER";

	private Context ctx;

    ToxSingleton toxSingleton = ToxSingleton.getInstance();

	public AntoxOnMessageCallback(Context ctx) {
		this.ctx = ctx;
	}

	@Override
	public void execute(AntoxFriend friend, String message) {
		Intent intent = new Intent(this.ctx, ToxService.class);
        intent.setAction(Constants.ON_MESSAGE);
		intent.putExtra(MESSAGE, message);
		intent.putExtra(FRIEND_NUMBER, friend.getFriendnumber());
		intent.putExtra(KEY, friend.getId());
		this.ctx.startService(intent);

        /* Add message to database */
        AntoxDB db = new AntoxDB(this.ctx);
        if(!db.isFriendBlocked(friend.getId()))
            db.addMessage(-1, friend.getId(), message, false, true, false, true);
        db.close();

        /* Broadcast to main activity to tell it to refresh */
        Intent notify = new Intent(Constants.BROADCAST_ACTION);
        notify.putExtra("action", Constants.UPDATE_MESSAGES);
        notify.putExtra("key", friend.getId());
        LocalBroadcastManager.getInstance(this.ctx).sendBroadcast(notify);

        /* Notifications for messages */
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this.ctx);

        /* Check user accepts notifications in their settings */
        if(preferences.getBoolean("notifications_enable_notifications", true) != false
                && preferences.getBoolean("notifications_new_message", true) != false) {

            /* Check user isn't actively looking at the friends list or the user's chat */
            if (!(toxSingleton.rightPaneActive && toxSingleton.activeFriendKey.equals(friend.getId()))
                    && !(toxSingleton.leftPaneActive)) {

                String name = toxSingleton.friendsList.getById(friend.getId()).getName();

                NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(this.ctx)
                                .setSmallIcon(R.drawable.ic_actionbar)
                                .setContentTitle(name)
                                .setContentText(message)
                                .setDefaults(Notification.DEFAULT_ALL);
                // Creates an explicit intent for an Activity in your app
                Intent resultIntent = new Intent(this.ctx, MainActivity.class);
                resultIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                resultIntent.setAction(Constants.SWITCH_TO_FRIEND);
                resultIntent.putExtra("key", friend.getId());
                resultIntent.putExtra("name", name);

                // The stack builder object will contain an artificial back stack for the
                // started Activity.
                // This ensures that navigating backward from the Activity leads out of
                // your application to the Home screen.
                TaskStackBuilder stackBuilder = TaskStackBuilder.create(this.ctx);
                // Adds the back stack for the Intent (but not the Intent itself)
                stackBuilder.addParentStack(MainActivity.class);
                // Adds the Intent that starts the Activity to the top of the stack
                stackBuilder.addNextIntent(resultIntent);
                PendingIntent resultPendingIntent =
                        stackBuilder.getPendingIntent(
                                0,
                                PendingIntent.FLAG_UPDATE_CURRENT
                        );
                mBuilder.setContentIntent(resultPendingIntent);
                toxSingleton.mNotificationManager.notify(friend.getFriendnumber(), mBuilder.build());
            }
        }
	}
}
