package com.yedidyabc1000.nagan44;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/** Routes Android headset/lock-screen media buttons to the active player activity. */
public class MediaButtonReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) return;
        KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return;

        Intent command = new Intent(MainActivity.ACTION_APP_MEDIA_BUTTON);
        command.setPackage(context.getPackageName());
        command.putExtra(MainActivity.EXTRA_MEDIA_KEY_CODE, event.getKeyCode());
        context.sendBroadcast(command);
    }
}
