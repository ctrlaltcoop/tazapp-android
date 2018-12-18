package de.thecode.android.tazreader.start;

import android.app.Application;
import android.text.TextUtils;

import de.thecode.android.tazreader.data.Paper;
import de.thecode.android.tazreader.data.PaperRepository;
import de.thecode.android.tazreader.data.TazSettings;
import de.thecode.android.tazreader.download.NewDownloadManagerHelper;
import de.thecode.android.tazreader.download.OldDownloadManager;
import de.thecode.android.tazreader.start.library.NewLibraryAdapter;
import de.thecode.android.tazreader.utils.AsyncTaskListener;
import de.thecode.android.tazreader.utils.SingleLiveEvent;
import de.thecode.android.tazreader.utils.StorageManager;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

//import de.thecode.android.tazreader.start.library.LibraryPaperLiveData;

public class StartViewModel extends AndroidViewModel {

//    private final LibraryPaperLiveData libraryPaperLiveData;

    private final MutableLiveData<Boolean>                      demoModeLiveData            = new MutableLiveData<>();
    private final LiveData<List<Paper>>                         livePapers;
    //    private final LiveData<List<Paper>> livePapers;
    private final TazSettings                                   settings;
    private final StorageManager                                storageManager;
    private final PaperRepository                               paperRepository;
    private final OldDownloadManager                            oldDownloadManager;
    private final NewDownloadManagerHelper                      newDownloadManager;
    private final List<String>                                  downloadQueue               = new ArrayList<>();
    private final SingleLiveEvent<DownloadError>                downloadErrorLiveSingleData = new SingleLiveEvent<>();
    private final List<NavigationDrawerFragment.NavigationItem> navBackstack                = new ArrayList<>();
    private       boolean                                       mobileDownloadAllowed       = false;
    private       String                                        openPaperIdAfterDownload;
    private       boolean                                       openReaderAfterDownload     = false;
    private       String                                        paperWaitingForResource;
    private       boolean                                       actionMode                  = false;
    private       NewLibraryAdapter.PaperMetaData               paperMetaDataMap            = new NewLibraryAdapter.PaperMetaData();


    private final TazSettings.OnPreferenceChangeListener<Boolean> demoModeListener = new TazSettings.OnPreferenceChangeListener<Boolean>() {
        @Override
        public void onPreferenceChanged(Boolean changedValue) {
            demoModeLiveData.setValue(changedValue);
        }
    };

    public StartViewModel(@NonNull Application application) {
        super(application);
        oldDownloadManager = OldDownloadManager.getInstance(application);
        newDownloadManager = NewDownloadManagerHelper.getInstance(application);
        paperRepository = PaperRepository.getInstance(application);
        storageManager = StorageManager.getInstance(application);
        settings = TazSettings.getInstance(application);
        settings.addDemoModeListener(demoModeListener);
        demoModeLiveData.setValue(settings.isDemoMode());
        LiveData<List<Paper>> sourceLivePapers = Transformations.switchMap(demoModeLiveData,
                                                                           demoMode -> demoMode ? paperRepository.getLivePapersForDemoLibrary() : paperRepository.getLivePapersForLibrary());
        livePapers = Transformations.map(sourceLivePapers, this::filterLibraryList);
    }

    private List<Paper> filterLibraryList(List<Paper> input) {
        List<Paper> result = new ArrayList<>();
        for (Paper paper : input) {
            if ((paper.getValidUntil() >= System.currentTimeMillis() / 1000) || !paper.hasNoneState()) result.add(paper);
        }
        return result;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        settings.removeOnPreferenceChangeListener(demoModeListener);
    }

    public void addToNavBackstack(NavigationDrawerFragment.NavigationItem item) {
        navBackstack.remove(item);
        navBackstack.add(item);
    }

    public List<NavigationDrawerFragment.NavigationItem> getNavBackstack() {
        return navBackstack;
    }

    public LiveData<List<Paper>> getLivePapers() {
        return livePapers;
    }

    public void setPaperMetaDataMap(NewLibraryAdapter.PaperMetaData paperMetaDataMap) {
        this.paperMetaDataMap = paperMetaDataMap;
    }

    public NewLibraryAdapter.PaperMetaData getPaperMetaDataMap() {
        return paperMetaDataMap;
    }

    public MutableLiveData<Boolean> getDemoModeLiveData() {
        return demoModeLiveData;
    }

//    public LibraryPaperLiveData getLibraryPaperLiveData() {
//        return libraryPaperLiveData;
//    }

    public List<String> getDownloadQueue() {
        return downloadQueue;
    }

    public SingleLiveEvent<DownloadError> getDownloadErrorLiveSingleData() {
        return downloadErrorLiveSingleData;
    }

    public void startDownloadQueue() {
        new AsyncTaskListener<Void, Void>(new AsyncTaskListener.OnExecute<Void, Void>() {
            @Override
            public Void execute(Void... aVoid) {
                while (downloadQueue.size() > 0) {
                    String bookId = downloadQueue.get(0);
                    Paper paper = paperRepository.getPaperWithBookId(bookId);
                    newDownloadManager.startPaperDownload(paper);
//                    OldDownloadManager.DownloadManagerResult result = oldDownloadManager.downloadPaper(bookId, false);
//                    if (result.getState() != OldDownloadManager.DownloadManagerResult.STATE.SUCCESS) {
//                        String title = "";
//                        Paper paper = paperRepository.getPaperWithBookId(bookId);
//                        if (paper != null) title = paper.getTitelWithDate(getApplication().getResources());
//                        downloadErrorLiveSingleData.postValue(new DownloadError(title, result.getDetails()));
//                    }
                    downloadQueue.remove(bookId);
                }
                return null;
            }
        }).execute();
    }

    public PaperRepository getPaperRepository() {
        return paperRepository;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public TazSettings getSettings() {
        return settings;
    }

    public boolean isMobileDownloadAllowed() {
        return mobileDownloadAllowed;
    }

    public void setMobileDownloadAllowed(boolean mobileDownloadAllowed) {
        this.mobileDownloadAllowed = mobileDownloadAllowed;
    }

    public void setOpenPaperIdAfterDownload(String bookId) {
        if (TextUtils.isEmpty(openPaperIdAfterDownload)) {
            openPaperIdAfterDownload = bookId;
            openReaderAfterDownload = true;
        } else {
            openReaderAfterDownload = false;
        }
    }

    public String getOpenPaperIdAfterDownload() {
        return openPaperIdAfterDownload;
    }

    public void removeOpenPaperIdAfterDownload() {
        openPaperIdAfterDownload = null;
        openReaderAfterDownload = true;
    }

    public void setPaperWaitingForResource(String bookId) {
        this.paperWaitingForResource = bookId;
    }

    public String getPaperWaitingForResource() {
        return paperWaitingForResource;
    }

    public boolean isOpenReaderAfterDownload() {
        return openReaderAfterDownload;
    }

    public void setOpenReaderAfterDownload(boolean openReaderAfterDownload) {
        this.openReaderAfterDownload = openReaderAfterDownload;
    }

    public void deletePaper(String... bookIds) {
        new AsyncTaskListener<String, Void>(bookIdsParam -> {
            List<Paper> papersToDelete = paperRepository.getPapersWithBookId(bookIdsParam);
            for (Paper paperToDelete : papersToDelete) {
                if (paperToDelete.hasDownloadingState()) oldDownloadManager.cancelDownload(paperToDelete.getDownloadId());
                paperRepository.deletePaper(paperToDelete);
            }
            return null;
        }).execute(bookIds);
    }

    public boolean isActionMode() {
        return actionMode;
    }

    public void setActionMode(boolean actionMode) {
        this.actionMode = actionMode;
    }

    public static class DownloadError {
        final String    title;
        final String    details;
        final Exception exception;

        public DownloadError(String title, String details, Exception exception) {
            this.title = title;
            this.details = details;
            this.exception = exception;
        }

        public DownloadError(String title, String details) {
            this(title, details, null);
        }


        public Exception getException() {
            return exception;
        }

        public String getTitle() {
            return title;
        }

        public String getDetails() {
            return details;
        }

    }
}
