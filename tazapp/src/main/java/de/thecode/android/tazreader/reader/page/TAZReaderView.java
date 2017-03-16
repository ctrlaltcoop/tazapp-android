package de.thecode.android.tazreader.reader.page;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.artifex.mupdfdemo.PageView;
import com.artifex.mupdfdemo.ReaderView;

import de.thecode.android.tazreader.data.Paper.Plist.Page;
import de.thecode.android.tazreader.reader.IReaderCallback;

import java.lang.ref.WeakReference;

import timber.log.Timber;


public class TAZReaderView extends ReaderView implements GestureDetector.OnDoubleTapListener {

    private boolean tapDisabled = false;
    private IReaderCallback mReaderCallback;
    private boolean mScrolling;

    public TAZReaderView(Context context) {
        super(context);

        init(context);
    }

    public TAZReaderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    public TAZReaderView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        init(context);
    }

    private void init(Context context) {
        if (!isInEditMode()) mReaderCallback = (IReaderCallback) context;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {


        // Disabled showing the buttons until next touch.
        // Not sure why this is needed, but without it
        // pinch zoom can make the buttons appear
        tapDisabled = true;

        return super.onScaleBegin(detector);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //Log.v(); to much

        if ((event.getAction() & event.getActionMasked()) == MotionEvent.ACTION_DOWN) {
            tapDisabled = false;
        }
        if ((event.getAction() & event.getActionMasked()) == MotionEvent.ACTION_UP) {
            if (mScrolling) mScrolling = false;
        }

        return super.onTouchEvent(event);
    }


    @Override
    protected void onChildSetup(int i, View v) {
        Timber.d("i: %s, v: %s", i, v);

    }

    @Override
    protected void onMoveToChild(int i) {
        Timber.d("i: %s", i);
        Page page = (Page) getAdapter().getItem(i);
        mReaderCallback.updateIndexes(page.getKey(), "0");

    }

    @Override
    protected void onMoveOffChild(int i) {
        Timber.d("i: %s", i);

    }

    @Override
    protected void onSettle(View v) {

        Timber.d("v: %s", v);
        // When the layout has settled ask the page to render
        // in HQ

        if (v instanceof TAZPageView) {
            if (((TAZPageView) v).mCore != null) {
                if (((TAZPageView) v).mCore.isDestroyed) return;
            }
        }
        ((PageView) v).updateHq(false);
    }

    @Override
    protected void onUnsettle(View v) {
        Timber.d("v: %s", v);
        // When something changes making the previous settled view
        // no longer appropriate, tell the page to remove HQ
        ((TAZPageView) v).removeHq();
    }

    @Override
    protected void onNotInUse(View v) {
        Timber.d("v: %s", v);
        ((TAZPageView) v).releaseResources();
    }

    @Override
    protected void onScaleChild(View v, Float scale) {
        Timber.d("v: %s, scale: %s", v, scale);
        ((TAZPageView) v).setScale(scale);
    }


    public void resetScale() {
        mScale = 1.0F;
    }


    public IReaderCallback getReaderCallback() {
        return mReaderCallback;
    }


    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (!tapDisabled) {
            TAZPageView pageView = (TAZPageView) getDisplayedView();
            pageView.passClickEvent(e.getX(), e.getY());
        }
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {

        ValueAnimator animator = ValueAnimator.ofFloat(mScale, mScale > 2F ? 1F : 4F);
        animator.setDuration(500);
        View v = mChildViews.get(mCurrent);
        animator.addUpdateListener(new ZoomAnimatorListener(v, e) {
            @Override
            public void onAnimationUpdate(float newScale, View v, MotionEvent e) {
                float previousScale = mScale;
                mScale = newScale;
                float factor = mScale / previousScale;
                if (v != null) {
                    // Work out the focus point relative to the view top left
                    int viewFocusX = (int) e.getX() - (v.getLeft() + mXScroll);
                    int viewFocusY = (int) e.getY() - (v.getTop() + mYScroll);
                    // Scroll to maintain the focus point
                    mXScroll += viewFocusX - viewFocusX * factor;
                    mYScroll += viewFocusY - viewFocusY * factor;
                    requestLayout();
                }
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                onScaleBegin(null);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                onScaleEnd(null);
                View v = mChildViews.get(mCurrent);
                postSettle(v);
            }
        });
        animator.start();


        return true;
    }



    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    private float minDistance = 10F;

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        Timber.i("ONSCROLL %f %f %b %b %b",distanceX,distanceY,mUserInteracting,mScaling,mScrolling);

        if (distanceX > minDistance || distanceY > minDistance || distanceX < -minDistance || distanceY < -minDistance) {
            mScrolling = true;
        }
        if (mScrolling) {
            return super.onScroll(e1, e2, distanceX, distanceY);
        }
        return false;
    }

    private abstract static class ZoomAnimatorListener implements ValueAnimator.AnimatorUpdateListener {

        final WeakReference<View> viewWeakReference;
        final MotionEvent         event;

        private ZoomAnimatorListener(View view, MotionEvent event) {
            this.viewWeakReference = new WeakReference<>(view);
            this.event = event;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            onAnimationUpdate((float) animation.getAnimatedValue(), viewWeakReference.get(), event);
        }

        public abstract void onAnimationUpdate(float newScale, View view, MotionEvent event);
    }
}
