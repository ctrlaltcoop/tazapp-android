package de.thecode.android.tazreader.worker;

import android.content.Context;
import android.text.TextUtils;

import de.thecode.android.tazreader.BuildConfig;
import de.thecode.android.tazreader.R;
import de.thecode.android.tazreader.data.Paper;
import de.thecode.android.tazreader.data.PaperRepository;
import de.thecode.android.tazreader.data.Resource;
import de.thecode.android.tazreader.data.ResourceRepository;
import de.thecode.android.tazreader.download.OldDownloadManager;
import de.thecode.android.tazreader.download.PaperDownloadFailedEvent;
import de.thecode.android.tazreader.download.ResourceDownloadEvent;
import de.thecode.android.tazreader.notifications.NotificationUtils;
import de.thecode.android.tazreader.secure.HashHelper;
import de.thecode.android.tazreader.utils.ReadableException;
import de.thecode.android.tazreader.utils.StorageManager;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Result;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.WorkerParameters;
import timber.log.Timber;

public class DownloadFinishedWorker extends LoggingWorker {

    private static final String TAG_PREFIX      = BuildConfig.FLAVOR + "_" + "download_finished_job_";
    private static final String ARG_DOWNLOAD_ID = "downloadId";

    private final StorageManager     externalStorage;
    private final OldDownloadManager downloadHelper;
    private final PaperRepository    paperRepository;
    private final ResourceRepository resourceRepository;

    public DownloadFinishedWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        externalStorage = StorageManager.getInstance(context);
        downloadHelper = OldDownloadManager.getInstance(context);
        paperRepository = PaperRepository.getInstance(context);
        resourceRepository = ResourceRepository.getInstance(context);
    }

    @NonNull
    @Override
    public Result doBackgroundWork() {

        long downloadId = getInputData().getLong(ARG_DOWNLOAD_ID, -1);
        Timber.d("starting background work for downloadId: %d", downloadId);
        if (downloadId != -1) {

            OldDownloadManager.DownloadState state = downloadHelper.getDownloadState(downloadId);
            Timber.d("state: %s", state);
            boolean firstOccurrenceOfState = downloadHelper.isFirstOccurrenceOfState(state);
            if (!firstOccurrenceOfState) {
                Timber.e("DownloadState already received");
                return Result.success();
            }

            Paper paper = paperRepository.getPaperWithDownloadId(downloadId);
            if (paper != null) {
                Timber.i("Download complete for paper: %s, %s", paper, state);
                paper.setDownloadId(0);

                try {
                    if (state.getStatus() == OldDownloadManager.DownloadState.STATUS_SUCCESSFUL) {
                        File downloadFile = externalStorage.getDownloadFile(paper);
                        if (!downloadFile.exists())
                            throw new DownloadException("Downloaded paper file missing");
                        Timber.i("... checked file existence");
                        if (paper.getLen() != 0 && downloadFile.length() != paper.getLen())
                            throw new DownloadException(String.format(Locale.GERMANY,
                                                                                          "Wrong size of paper download. expected: %d, file: %d, downloaded: %d",
                                                                                          paper.getLen(),
                                                                                          downloadFile.length(),
                                                                                          state.getBytesDownloaded()));
                        Timber.i("... checked correct size of paper download");
                        try {
                            String fileHash = HashHelper.getHash(downloadFile, HashHelper.SHA_1);
                            if (!TextUtils.isEmpty(paper.getFileHash()) && !paper.getFileHash()
                                                                                 .equals(fileHash)) {
                                throw new DownloadException(String.format(Locale.GERMANY,
                                                                                              "Wrong paper file hash. Expected: %s, calculated: %s",
                                                                                              paper.getFileHash(),
                                                                                              fileHash));
                            }
                            Timber.i("... checked correct hash of paper download");
                        } catch (NoSuchAlgorithmException e) {
                            Timber.w(e);
                        } catch (IOException e) {
                            Timber.e(e);
                            throw new DownloadException(e);
                        }
                        paper.setState(Paper.STATE_DOWNLOADED);
                        paperRepository.savePaper(paper);
                        DownloadFinishedPaperWorker.scheduleNow(paper);
                    } else {
                        throw new DownloadException(state.getStatusText() + ": " + state.getReasonText());
                    }
                } catch (DownloadException e) {
                    Timber.e(e);
                    paper.setState(Paper.STATE_NONE);
                    paperRepository.savePaper(paper);
                    if (state.getReason() == 406) {
                        SyncWorker.scheduleJobImmediately(false);
                        //SyncHelper.requestSync(context);
                    }
                    //AnalyticsWrapper.getInstance().logException(exception);
//                        context.getContentResolver()
//                               .update(TazProvider.getContentUri(Paper.CONTENT_URI, paper.getBookId()),
//                                       paper.getContentValues(),
//                                       null,
//                                       null);
                    if (externalStorage.getDownloadFile(paper)
                                       .exists()) //noinspection ResultOfMethodCallIgnored
                        externalStorage.getDownloadFile(paper)
                                       .delete();
                    if (state.getStatus() != OldDownloadManager.DownloadState.STATUS_NOTFOUND) {
                        NotificationUtils.getInstance(getApplicationContext())
                                         .showDownloadErrorNotification(paper,
                                                                        getApplicationContext().getString(R.string.download_error_hints));
                        //NotificationHelper.showDownloadErrorNotification(context, null, paper.getId());

                        EventBus.getDefault()
                                .post(new PaperDownloadFailedEvent(paper, e));
                    }

                }

            } else {
                Timber.w("No paper found for downloadId %d", downloadId);
            }

            Resource resource = resourceRepository.getWithDownloadId(downloadId);
            if (resource != null) {

                //DownloadHelper.DownloadState downloadDownloadState = downloadHelper.getDownloadState(downloadId);
                Timber.i("Download complete for resource: %s, %s", resource, state);
                try {
                    if (state.getStatus() == OldDownloadManager.DownloadState.STATUS_SUCCESSFUL) {
                        File downloadFile = externalStorage.getDownloadFile(resource);
                        if (!downloadFile.exists())
                            throw new DownloadException("Downloaded resource file missing");
                        Timber.i("... checked file existence");
                        if (resource.getLen() != 0 && downloadFile.length() != resource.getLen())
                            throw new DownloadException(String.format(Locale.GERMANY,
                                                                                          "Wrong size of resource download. expected: %d, file: %d, downloaded: %d",
                                                                                          resource.getLen(),
                                                                                          downloadFile.length(),
                                                                                          state.getBytesDownloaded()));
                        Timber.i("... checked correct size of resource download");
                        try {
                            String fileHash = HashHelper.getHash(downloadFile, HashHelper.SHA_1);
                            if (!TextUtils.isEmpty(resource.getFileHash()) && !resource.getFileHash()
                                                                                       .equals(fileHash))
                                throw new DownloadException(String.format(Locale.GERMANY,
                                                                                              "Wrong resource file hash. Expected: %s, calculated: %s",
                                                                                              resource.getFileHash(),
                                                                                              fileHash));
                            Timber.i("... checked correct hash of resource download");
                        } catch (NoSuchAlgorithmException e) {
                            Timber.w(e);
                        } catch (IOException e) {
                            Timber.e(e);
                            throw new DownloadException(e);
                        }
                        DownloadFinishedResourceWorker.scheduleNow(resource);
                    } else {
                        throw new DownloadException(state.getStatusText() + ": " + state.getReasonText());
                    }
                } catch (DownloadException e) {
                    Timber.e(e);
                    resource.setDownloadId(0);
                    resourceRepository.saveResource(resource);
                    EventBus.getDefault()
                            .post(new ResourceDownloadEvent(resource.getKey(), e));
                }

            } else {
                Timber.w("No resource found for downloadId %d", downloadId);
            }

        }

        return Result.success();
    }

    public static String getTag(long downloadId) {
        return TAG_PREFIX + downloadId;
    }

    public static void scheduleNow(long downloadId) {
        Data data = new Data.Builder().putLong(ARG_DOWNLOAD_ID, downloadId)
                                      .build();
        WorkRequest request = new OneTimeWorkRequest.Builder(DownloadFinishedWorker.class).setInputData(data)
                                                                                      .addTag(getTag(downloadId))
                                                                                      .build();
        WorkManager.getInstance()
                   .enqueue(request);
    }

    public static class DownloadException extends ReadableException {
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
