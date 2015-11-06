package de.thecode.android.tazreader.download;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import de.greenrobot.event.EventBus;
import de.thecode.android.tazreader.data.Paper;
import de.thecode.android.tazreader.data.Resource;
import de.thecode.android.tazreader.secure.HashHelper;
import de.thecode.android.tazreader.start.StartActivity;
import de.thecode.android.tazreader.utils.StorageManager;
import de.thecode.android.tazreader.utils.Log;

public class DownloadReceiver extends BroadcastReceiver {


//    Context mContext;
//    ExternalStorage mStorage;

    @Override
    public void onReceive(Context context, Intent intent) {

        StorageManager externalStorage = StorageManager.getInstance(context);
        DownloadHelper downloadHelper = new DownloadHelper(context);

        String action = intent.getAction();

        Log.t("DownloadReceiver received intent:",intent);

        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);

            DownloadHelper.DownloadState state = downloadHelper.getDownloadState(downloadId);
            boolean firstOccurrenceOfState = downloadHelper.isFirstOccurrenceOfState(state);
            if (!firstOccurrenceOfState) {
                Log.e("DownloadState already received:",state);
                return;
            }

            Cursor cursor = context.getContentResolver()
                                   .query(Paper.CONTENT_URI, null, Paper.Columns.DOWNLOADID + " = " + downloadId, null, null);
            try {
                while (cursor.moveToNext()) {
                    Paper paper = new Paper(cursor);
                    //DownloadHelper.DownloadState downloadDownloadState = downloadHelper.getDownloadState(downloadId);
                    Log.t("Download complete for paper:", paper);
                    Log.t(state);
                    boolean failed = false;
                    if (state.getStatus() == DownloadHelper.DownloadState.STATUS_SUCCESSFUL) {
                        File downloadFile = externalStorage.getDownloadFile(paper);
                        if (!downloadFile.exists()) {
                            failed = true;
                        } else {
                            if (paper.getLen() != 0 && downloadFile.length() != paper.getLen()) {
                                Log.e("Wrong size of paper download");
                                failed = true;
                            } else Log.t("... checked correct size of paper download");
                            try {
                                String fileHash = HashHelper.getHash(downloadFile, HashHelper.SHA_1);
                                if (paper.getFileHash() != null && !paper.getFileHash()
                                                                         .equals(fileHash)) {
                                    Log.e("Wrong paper filehash");
                                    failed = true;
                                } else Log.t("... checked correct hash of paper download");
                            } catch (NoSuchAlgorithmException e) {
                                Log.w(e);
                                Log.sendExceptionWithCrashlytics(e);
                            } catch (IOException e) {
                                Log.e(e);
                                failed = true;
                            }
                            if (!failed) {
                                Intent unzipIntent = new Intent(context, FinishPaperDownloadService.class);
                                unzipIntent.putExtra(FinishPaperDownloadService.PARAM_PAPER_ID, paper.getId());
                                context.startService(unzipIntent);
                            }
                        }
                    } else if (state.getStatus() == DownloadHelper.DownloadState.STATUS_FAILED) {
                        failed = true;
                    }
                    if (failed) {
                        Log.e("Download failed");
                        DownloadException exception = new DownloadException(state.getStatusText() + ": " + state.getReasonText());
                        Log.sendExceptionWithCrashlytics(exception);
                        paper.setDownloadId(0);
                        context.getContentResolver()
                               .update(ContentUris.withAppendedId(Paper.CONTENT_URI, paper.getId()), paper.getContentValues(), null, null);
                        if (externalStorage.getDownloadFile(paper)
                                    .exists()) //noinspection ResultOfMethodCallIgnored
                            externalStorage.getDownloadFile(paper)
                                    .delete();
                        NotificationHelper.showDownloadErrorNotification(context, paper.getId());
                        EventBus.getDefault()
                                .post(new PaperDownloadFailedEvent(paper.getId(), exception));
                    }
                }
            } finally {
                cursor.close();
            }

            cursor = context.getContentResolver()
                            .query(Resource.CONTENT_URI, null, Resource.Columns.DOWNLOADID + " = " + downloadId, null, null);
            try {
                while (cursor.moveToNext()) {

                    Resource resource = new Resource(cursor);
                    //DownloadHelper.DownloadState downloadDownloadState = downloadHelper.getDownloadState(downloadId);
                    Log.t("Download complete for resource:", resource);
                    Log.t(state);

                    boolean failed = false;
                    if (state.getStatus() == DownloadHelper.DownloadState.STATUS_SUCCESSFUL) {


                        File downloadFile = externalStorage.getDownloadFile(resource);
                        if (!downloadFile.exists()) {
                            failed = true;
                        } else {
                            if (resource.getLen() != 0 && downloadFile.length() != resource.getLen()) {
                                Log.e("Wrong size of resource download");
                                failed = true;
                            } else Log.t("... checked correct size of resource download");
                            try {
                                String fileHash = HashHelper.getHash(downloadFile, HashHelper.SHA_1);
                                if (resource.getFileHash() != null && !resource.getFileHash()
                                                                               .equals(fileHash)) {
                                    Log.e("Wrong resource filehash");
                                    failed = true;
                                } else Log.t("... checked correct hash of resource download");
                            } catch (NoSuchAlgorithmException e) {
                                Log.w(e);
                                Log.sendExceptionWithCrashlytics(e);
                            } catch (IOException e) {
                                Log.e(e);
                                failed = true;
                            }
                            if (!failed) {
                                Intent unzipIntent = new Intent(context, FinishResourceDownloadService.class);
                                unzipIntent.putExtra(FinishResourceDownloadService.PARAM_RESOURCE_KEY, resource.getKey());
                                context.startService(unzipIntent);
                            }
                        }
                    } else if (state.getStatus() == DownloadHelper.DownloadState.STATUS_FAILED) {
                        failed = true;
                    }
                    if (failed) {
                        Log.e("Download failed");
                        DownloadException exception = new DownloadException(state.getStatusText() + ": " + state.getReasonText());
                        Log.sendExceptionWithCrashlytics(exception);
                        resource.setDownloadId(0);
                        context.getContentResolver()
                               .update(Uri.withAppendedPath(Resource.CONTENT_URI, resource.getKey()), resource.getContentValues(), null, null);
                        EventBus.getDefault()
                                .post(new ResourceDownloadEvent(resource.getKey(), exception));
                    }

                }
            } finally {
                cursor.close();
            }

        } else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(action)) {
            Intent libIntent = new Intent(context, StartActivity.class);
            libIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(libIntent);
        }
    }

    public class DownloadException extends Exception {
        public DownloadException() {
        }

        public DownloadException(String detailMessage) {
            super(detailMessage);
        }

        public DownloadException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        public DownloadException(Throwable throwable) {
            super(throwable);
        }
    }

}