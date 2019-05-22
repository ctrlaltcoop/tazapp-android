package de.thecode.android.tazreader.utils;


import android.content.Intent;
import android.os.Bundle;

import de.mateware.dialog.DialogAdapterList;
import de.mateware.dialog.listener.DialogAdapterListListener;
import de.mateware.dialog.listener.DialogButtonListener;
import de.mateware.dialog.listener.DialogCancelListener;
import de.mateware.dialog.listener.DialogDismissListener;
import de.mateware.dialog.listener.DialogListListener;
import de.thecode.android.tazreader.BuildConfig;
import de.thecode.android.tazreader.data.TazSettings;
import de.thecode.android.tazreader.dialog.PushNotificationDialog;
import de.thecode.android.tazreader.dialognew.HelpPage;
import de.thecode.android.tazreader.push.PushHelper;
import de.thecode.android.tazreader.push.PushNotification;
import de.thecode.android.tazreader.worker.PushRestApiWorker;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import timber.log.Timber;

/**
 * Created by mate on 12.05.2015.
 */
public class BaseActivity extends AppCompatActivity
        implements DialogButtonListener, DialogDismissListener, DialogCancelListener, DialogListListener,
        DialogAdapterListListener {

    public static final  String DIALOG_HELP = "hilfeDialog";
    private static final String DIALOG_PUSH = "DialogPush";

    private TazSettings.OnPreferenceChangeListener<String> orientationPreferenceListener = new TazSettings.OnPreferenceChangeListener<String>() {
        @Override
        public void onPreferenceChanged(String changedValue) {
            setOrientation(changedValue);
        }
    };
    private TazSettings.OnPreferenceChangeListener<Object> pushPreferenceListener        = new TazSettings.OnPreferenceChangeListener<Object>() {
        @Override
        public void onPreferenceChanged(Object changedValue) {
            PushRestApiWorker.scheduleNow();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FragmentManager.enableDebugLogging(BuildConfig.DEBUG);
        setOrientation(TazSettings.getInstance(this)
                                  .getPrefString(TazSettings.PREFKEY.ORIENTATION, "auto"));
    }

    private void setOrientation(String orientationKey) {
        Orientation.setActivityOrientation(this, orientationKey);
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault()
                .register(this);
        TazSettings.getInstance(this)
                   .addOnPreferenceChangeListener(TazSettings.PREFKEY.ORIENTATION, orientationPreferenceListener);
        TazSettings.getInstance(this)
                   .addOnPreferenceChangeListener(TazSettings.PREFKEY.NOTIFICATION_PUSH, pushPreferenceListener);
        TazSettings.getInstance(this)
                   .addOnPreferenceChangeListener(TazSettings.PREFKEY.NOTIFICATION_SOUND_PUSH, pushPreferenceListener);
        TazSettings.getInstance(this)
                   .addOnPreferenceChangeListener(TazSettings.PREFKEY.FIREBASETOKEN, pushPreferenceListener);
    }

    @Override
    protected void onStop() {
        TazSettings.getInstance(this)
                   .removeOnPreferenceChangeListener(orientationPreferenceListener);
        TazSettings.getInstance(this)
                   .removeOnPreferenceChangeListener(pushPreferenceListener);
        EventBus.getDefault()
                .unregister(this);
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PushHelper.checkIntentForFCMPushNotificationExtras(this, getIntent());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPushNotification(PushNotification event) {
        Timber.d("received PushNotification Event");
        new PushNotificationDialog.Builder().setPositiveButton()
                                            .setPushNotification(event)
                                            .buildSupport()
                                            .show(getSupportFragmentManager(), DIALOG_PUSH);
    }

//    public void showHelpDialog(@HelpDialog.HelpPage String helpPage) {
//        if (getSupportFragmentManager().findFragmentByTag(DIALOG_HELP) == null) {
//            HelpDialog.Builder builder = new HelpDialog.Builder().setPositiveButton()
//                                                                 .setHelpPage(helpPage);
//            if (HelpDialog.HELP_INTRO.equals(helpPage)){
//                builder.setNeutralButton(R.string.drawer_account);
//            }
//            builder.buildSupport()
//                   .show(getSupportFragmentManager(), DIALOG_HELP);
//        }
//    }


    public void showNewHelpDialog(HelpPage helpPage) {
        if (getSupportFragmentManager().findFragmentByTag(de.thecode.android.tazreader.dialognew.HelpDialog.TAG) == null) {
            de.thecode.android.tazreader.dialognew.HelpDialog.Companion.newInstance(helpPage).show(getSupportFragmentManager(),de.thecode.android.tazreader.dialognew.HelpDialog.TAG);
        }
    }

    @Override
    public void onDialogClick(String tag, Bundle dialogArguments, int which) {

    }

    @Override
    public void onDialogCancel(String tag, Bundle dialogArguments) {

    }

    @Override
    public void onDialogDismiss(String tag, Bundle dialogArguments) {
    Timber.i("");
    }

    @Override
    public void onDialogAdapterListClick(String tag, DialogAdapterList.DialogAdapterListEntry entry, Bundle arguments) {

    }

    @Override
    public void onDialogListClick(String tag, Bundle arguments, int which, String value, String[] items) {

    }
}
