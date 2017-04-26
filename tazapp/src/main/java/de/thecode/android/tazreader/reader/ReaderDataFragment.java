package de.thecode.android.tazreader.reader;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;

import de.thecode.android.tazreader.data.Paper;

import org.greenrobot.eventbus.EventBus;

import java.lang.ref.WeakReference;

import timber.log.Timber;

/**
 * Created by mate on 13.11.2015.
 */
public class ReaderDataFragment extends Fragment /*implements TextToSpeech.OnInitListener*/ {

    private static final String TAG = "RetainDataFragment";

    private Paper   _paper;
    private String  mCurrentKey;
    private String  mPosition;
    private boolean filterBookmarks;

    private PaperLoadingTask paperLoadingTask;

    private WeakReference<ReaderDataFramentCallback> callback;

    public ReaderDataFragment() {
    }

    public static ReaderDataFragment findRetainFragment(FragmentManager fm) {
        return (ReaderDataFragment) fm.findFragmentByTag(TAG);
    }

    public static ReaderDataFragment createRetainFragment(FragmentManager fm) {
        ReaderDataFragment fragment = new ReaderDataFragment();
        fm.beginTransaction()
          .add(fragment, TAG)
          .commit();
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (hasCallback()) getCallback().onDataFragmentAttached(this);
    }

    public void setCallback(ReaderDataFramentCallback callback) {
        this.callback = new WeakReference<>(callback);
    }

    private boolean hasCallback() {
        return callback != null && callback.get() != null;
    }

    private ReaderDataFramentCallback getCallback() {
        return callback.get();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    public Paper getPaper() {
        return _paper;
    }

    public void setPaper(Paper paper) {
        this._paper = paper;
    }

    public boolean isPaperLoaded() {
        return _paper != null;
    }

    public void loadPaper(long paperId) {
        if (paperLoadingTask == null) {
            paperLoadingTask = new PaperLoadingTask(getContext(), paperId) {
                @Override
                protected void onPostError(Exception exception) {
                    EventBus.getDefault().post(new PaperLoadedEvent(exception));
                }
                @Override
                protected void onPostSuccess(Paper paper) {
                    _paper = paper;
                    String currentKey = paper.getStoreValue(getContext(), ReaderActivity.STORE_KEY_CURRENTPOSITION);
                    String position = paper.getStoreValue(getContext(), ReaderActivity.STORE_KEY_POSITION_IN_ARTICLE);
                    if (TextUtils.isEmpty(currentKey)) {
                        currentKey = paper.getPlist()
                                          .getSources()
                                          .get(0)
                                          .getBooks()
                                          .get(0)
                                          .getCategories()
                                          .get(0)
                                          .getPages()
                                          .get(0)
                                          .getKey();
                    }
                    if (TextUtils.isEmpty(position)) position = "0";
                    setCurrentKey(currentKey, position);
                    EventBus.getDefault().post(new PaperLoadedEvent());
                }
            };
            paperLoadingTask.execute();
        }
    }

    public void setCurrentKey(String currentKey, String position) {
        Timber.d("%s %s", currentKey, position);

        mCurrentKey = currentKey;
        mPosition = position;
        try {
            _paper.saveStoreValue(getContext(), ReaderActivity.STORE_KEY_CURRENTPOSITION, mCurrentKey);
            _paper.saveStoreValue(getContext(), ReaderActivity.STORE_KEY_POSITION_IN_ARTICLE, position);
        } catch (Exception e) {
            Timber.w(e);
        }
    }

    public String getCurrentKey() {
        return mCurrentKey;
    }

    public String getPostion() {
        return mPosition;
    }

    public boolean isFilterBookmarks() {
        return filterBookmarks;
    }

    public void setFilterBookmarks(boolean filterBookmarks) {
        this.filterBookmarks = filterBookmarks;
    }

    public interface ReaderDataFramentCallback {
        void onDataFragmentAttached(Fragment fragment);
    }
}
