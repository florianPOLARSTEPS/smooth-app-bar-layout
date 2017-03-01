/*
 * Copyright 2016 "Henry Tao <hi@henrytao.me>"
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.henrytao.smoothappbarlayout;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import me.henrytao.smoothappbarlayout.base.ObservableFragment;
import me.henrytao.smoothappbarlayout.base.ObservablePagerAdapter;
import me.henrytao.smoothappbarlayout.base.ScrollFlag;
import me.henrytao.smoothappbarlayout.base.ScrollTargetCallback;
import me.henrytao.smoothappbarlayout.base.Utils;

/**
 * Created by henrytao on 2/1/16.
 */
@CoordinatorLayout.DefaultBehavior(SmoothAppBarLayout.Behavior.class)
public class SmoothAppBarLayout extends AppBarLayout {

  private static final String ARG_CURRENT_OFFSET = "ARG_CURRENT_OFFSET";

  private static final String ARG_SUPER = "ARG_SUPER";

  private static final int CUSTOM_EDGE_FLAG = 2023477;

  public static boolean DEBUG = false;

  protected final List<WeakReference<OnOffsetChangedListener>> mOffsetChangedListeners = new ArrayList<>();

  protected boolean mHaveChildWithInterpolator;

  private float mDownY;

  private int mRestoreCurrentOffset;

  private ScrollTargetCallback mScrollTargetCallback;

  private me.henrytao.smoothappbarlayout.base.OnOffsetChangedListener mSyncOffsetListener;

  private int mTargetId;

  private int mViewPagerId;

  private ViewPager vViewPager;

  public SmoothAppBarLayout(Context context) {
    super(context);
    init(context, null);
  }

  public SmoothAppBarLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context, attrs);

  }

  @Override
  public void addOnOffsetChangedListener(OnOffsetChangedListener listener) {
    super.addOnOffsetChangedListener(listener);
    int i = 0;
    for (int z = this.mOffsetChangedListeners.size(); i < z; ++i) {
      WeakReference ref = (WeakReference) this.mOffsetChangedListeners.get(i);
      if (ref != null && ref.get() == listener) {
        return;
      }
    }
    this.mOffsetChangedListeners.add(new WeakReference(listener));
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {

    // We need to check wether we have arrived from our own triggered motion dispatch,
    // There are no really appropriate fields on MotionEvents to store custom data, so we abuse edgeflags
    boolean dispatched = super.dispatchTouchEvent(ev);
    if (ev.getEdgeFlags() == CUSTOM_EDGE_FLAG) {
      return false;
    }

    switch (ev.getAction()) {
      case MotionEvent.ACTION_DOWN:
        mDownY = ev.getY();
        break;

      case MotionEvent.ACTION_MOVE:
        if (dispatched && Math.abs(ev.getY() - mDownY) > 20) {
          // After we know some view in our hierarchy would want to receive the move touch event, we don't want it to have though,
          // we create a new motion event which will cancel our current motion event stream and will be disregarded by appbarlayout,
          // so CoordinatorLayout.Behaviour can receive the new motion event stream
          MotionEvent motionEvent = MotionEvent.obtain(ev);
          motionEvent.offsetLocation(getLeft(), getTop());
          motionEvent.setAction(MotionEvent.ACTION_DOWN);
          motionEvent.setEdgeFlags(CUSTOM_EDGE_FLAG);

          // getParent() cannot return null, since well - who would have called this method
          ((ViewGroup) getParent()).dispatchTouchEvent(motionEvent);
          return false;
        }
        break;

      default:
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        mDownY = 0;
        break;
    }

    return dispatched;
  }

  @Override
  public void removeOnOffsetChangedListener(OnOffsetChangedListener listener) {
    super.removeOnOffsetChangedListener(listener);
    Iterator i = mOffsetChangedListeners.iterator();
    while (true) {
      OnOffsetChangedListener item;
      do {
        if (!i.hasNext()) {
          return;
        }
        WeakReference ref = (WeakReference) i.next();
        item = (OnOffsetChangedListener) ref.get();
      } while (item != listener && item != null);
      i.remove();
    }
  }

  @Override
  public void setExpanded(boolean expanded) {
    setExpanded(expanded, false);
  }

  @Override
  public void setExpanded(boolean expanded, boolean animate) {
    CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) getLayoutParams();
    Behavior behavior = (Behavior) params.getBehavior();
    if (behavior == null) {
      super.setExpanded(expanded, animate);
      return;
    }
    View view = behavior.getScrollTarget();
    if (!expanded && !behavior.isCollapsed()) {
      int minOffset = Math.abs(behavior.getMinOffset(this));
      if (view instanceof RecyclerView) {
        RecyclerView recyclerView = (RecyclerView) view;
        int offset = minOffset - recyclerView.computeVerticalScrollOffset();
        if (animate) {
          recyclerView.smoothScrollBy(0, offset);
        } else {
          recyclerView.scrollBy(0, offset);
        }
      } else if (view instanceof NestedScrollView) {
        NestedScrollView nestedScrollView = (NestedScrollView) view;
        if (animate) {
          nestedScrollView.smoothScrollTo(0, minOffset);
        } else {
          nestedScrollView.scrollTo(0, minOffset);
        }
      }
    } else if (expanded) {
      if (view instanceof RecyclerView) {
        RecyclerView recyclerView = (RecyclerView) view;
        if (animate) {
          if (behavior.isCollapsed()) {
            recyclerView.smoothScrollToPosition(0);
          } else {
            recyclerView.smoothScrollBy(0, -recyclerView.computeVerticalScrollOffset());
          }
        } else {
          if (behavior.isCollapsed()) {
            recyclerView.scrollToPosition(0);
          } else {
            recyclerView.scrollBy(0, -recyclerView.computeVerticalScrollOffset());
          }
        }
      } else if (view instanceof NestedScrollView) {
        NestedScrollView nestedScrollView = (NestedScrollView) view;
        if (animate) {
          nestedScrollView.smoothScrollTo(0, 0);
        } else {
          nestedScrollView.scrollTo(0, 0);
        }
      }
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    initViews();
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    int i = 0;
    for (int z = this.getChildCount(); i < z; ++i) {
      View child = this.getChildAt(i);
      LayoutParams childLp = (LayoutParams) child.getLayoutParams();
      Interpolator interpolator = childLp.getScrollInterpolator();
      if (interpolator != null) {
        mHaveChildWithInterpolator = true;
        break;
      }
    }
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    Bundle bundle = (Bundle) state;
    mRestoreCurrentOffset = bundle.getInt(ARG_CURRENT_OFFSET);
    Parcelable superState = bundle.getParcelable(ARG_SUPER);
    super.onRestoreInstanceState(superState);
  }

  @Override
  protected Parcelable onSaveInstanceState() {
    Bundle bundle = new Bundle();
    bundle.putInt(ARG_CURRENT_OFFSET, getCurrentOffset());
    bundle.putParcelable(ARG_SUPER, super.onSaveInstanceState());
    return bundle;
  }

  public int getCurrentOffset() {
    return -Utils.parseInt(getTag(R.id.tag_current_offset));
  }

  public void setScrollTargetCallback(ScrollTargetCallback scrollTargetCallback) {
    mScrollTargetCallback = scrollTargetCallback;
  }

  public void syncOffset(int newOffset) {
    syncOffset(newOffset, false);
  }

  private void init(Context context, AttributeSet attrs) {
    TypedArray a = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.SmoothAppBarLayout, 0, 0);
    try {
      mViewPagerId = a.getResourceId(R.styleable.SmoothAppBarLayout_sabl_view_pager_id, 0);
      mTargetId = a.getResourceId(R.styleable.SmoothAppBarLayout_sabl_target_id, 0);
    } finally {
      a.recycle();
    }
  }

  private void initViews() {
    if (mViewPagerId > 0) {
      vViewPager = (ViewPager) getRootView().findViewById(mViewPagerId);
    } else {
      int i = 0;
      ViewGroup parent = (ViewGroup) getParent();
      View child;
      for (int z = parent.getChildCount(); i < z; i++) {
        child = parent.getChildAt(i);
        if (child instanceof ViewPager) {
          vViewPager = (ViewPager) child;
          break;
        }
      }
    }
  }

  private void setSyncOffsetListener(me.henrytao.smoothappbarlayout.base.OnOffsetChangedListener syncOffsetListener) {
    mSyncOffsetListener = syncOffsetListener;
    syncOffset(mRestoreCurrentOffset, true);
  }

  private void syncOffset(int newOffset, boolean force) {
    mRestoreCurrentOffset = newOffset;
    if (mSyncOffsetListener != null) {
      mSyncOffsetListener.onOffsetChanged(this, newOffset, force);
    }
  }

  public static class Behavior extends BaseBehavior {

    protected ScrollFlag mScrollFlag;

    private int mLastY;

    private int mStatusBarSize;

    private ViewPager vViewPager;

    @Override
    protected void onInit(CoordinatorLayout coordinatorLayout, final AppBarLayout child) {
      Utils.log("widget | onInit");
      if (mScrollFlag == null) {
        mScrollFlag = new ScrollFlag(child);
      }
      if (child instanceof SmoothAppBarLayout) {
        final SmoothAppBarLayout layout = (SmoothAppBarLayout) child;
        setScrollTargetCallback(layout.mScrollTargetCallback);
        vViewPager = layout.vViewPager;
        if (vViewPager != null) {
          vViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrollStateChanged(int state) {
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
              propagateViewPagerOffset(layout, true);
            }
          });
        }
        layout.setSyncOffsetListener(new me.henrytao.smoothappbarlayout.base.OnOffsetChangedListener() {
          @Override
          public void onOffsetChanged(SmoothAppBarLayout smoothAppBarLayout, int verticalOffset, boolean isOrientationChanged) {
            syncOffset(smoothAppBarLayout, -verticalOffset);
            if (!isOrientationChanged) {
              propagateViewPagerOffset(smoothAppBarLayout, false);
            }
          }
        });
        temporaryInit(coordinatorLayout, child);
      }
    }

    @Override
    protected void onScrollChanged(CoordinatorLayout coordinatorLayout, AppBarLayout child, View target, int y, int dy, boolean accuracy) {
      if (!mScrollFlag.isFlagScrollEnabled() || !(child instanceof SmoothAppBarLayout)) {
        return;
      }

      int targetId = ((SmoothAppBarLayout) child).mTargetId;
      if (targetId > 0 && targetId != target.getId()) {
        return;
      }

      if (vViewPager != null && vViewPager.getAdapter() instanceof ObservablePagerAdapter) {
        ObservablePagerAdapter pagerAdapter = (ObservablePagerAdapter) vViewPager.getAdapter();
        if (pagerAdapter.getObservableFragment(vViewPager.getCurrentItem()).getScrollTarget() != target) {
          return;
        }
      }

      // TODO: temporary fix for issues:
      // https://github.com/henrytao-me/smooth-app-bar-layout/issues/114
      // https://github.com/henrytao-me/smooth-app-bar-layout/issues/139
      if (y == mLastY) {
        return;
      }
      mLastY = y;

      int oDy = dy;
      int minOffset = getMinOffset(child);
      int maxOffset = getMaxOffset(child);
      int translationOffset = accuracy ? Math.min(Math.max(minOffset, -y), maxOffset) : minOffset;

      dy = dy != 0 ? dy : y + getCurrentOffset();

      if (mScrollFlag.isQuickReturnEnabled()) {
        translationOffset = getCurrentOffset() - dy;
        translationOffset = Math.min(Math.max(minOffset, translationOffset), maxOffset);
        int breakPoint = minOffset + getMinHeight(child, true);
        if (dy <= 0 && !(accuracy && y <= Math.abs(breakPoint))) {
          translationOffset = Math.min(translationOffset, breakPoint);
        }
        // TODO: temporary fix for issue https://github.com/henrytao-me/smooth-app-bar-layout/issues/108
        translationOffset = !accuracy && oDy == 0 && getCurrentOffset() == minOffset ? minOffset : translationOffset;
      } else if (mScrollFlag.isFlagEnterAlwaysEnabled()) {
        translationOffset = getCurrentOffset() - dy;
        translationOffset = Math.min(Math.max(minOffset, translationOffset), maxOffset);
      } else if (mScrollFlag.isFlagEnterAlwaysCollapsedEnabled()) {
        // do nothing
      } else if (mScrollFlag.isFlagExitUntilCollapsedEnabled()) {
        // do nothing
      }

      Utils.log("widget | onScrollChanged | %d | %d | %d | %d | %d | %b | %d", minOffset, maxOffset, getCurrentOffset(), y, dy, accuracy,
          translationOffset);
      syncOffset(child, translationOffset);

      propagateViewPagerOffset((SmoothAppBarLayout) child, false);
    }

    protected int getMaxOffset(AppBarLayout layout) {
      return 0;
    }

    protected int getMinOffset(AppBarLayout layout) {
      int minOffset = layout.getMeasuredHeight();
      if (mScrollFlag != null) {
        if (mScrollFlag.isFlagScrollEnabled()) {
          minOffset = layout.getMeasuredHeight() - getMinHeight(layout, false);
        }
      }
      if (ViewCompat.getFitsSystemWindows(layout)) {
        if (mStatusBarSize == 0) {
          mStatusBarSize = Utils.getStatusBarSize(layout.getContext());
        }
        minOffset -= mStatusBarSize;
      }
      return -Math.max(minOffset, 0);
    }

    private int getMinHeight(AppBarLayout layout, boolean forceQuickReturn) {
      int minHeight = ViewCompat.getMinimumHeight(layout);
      if (mScrollFlag.isFlagExitUntilCollapsedEnabled() || (minHeight > 0 && !mScrollFlag.isQuickReturnEnabled()) || forceQuickReturn) {
        return minHeight > 0 ? minHeight : ViewCompat.getMinimumHeight(mScrollFlag.getView());
      }
      return 0;
    }

    private boolean propagateViewPagerOffset(SmoothAppBarLayout smoothAppBarLayout, int position) {
      if (vViewPager != null && vViewPager.getAdapter() instanceof ObservablePagerAdapter) {
        int n = vViewPager.getAdapter().getCount();
        if (position >= 0 && position < n) {
          int currentItem = vViewPager.getCurrentItem();
          int currentOffset = Math.max(0, -getCurrentOffset());
          Utils.log("widget | propagateViewPagerOffset | %d | %d | %d", currentItem, position, currentOffset);

          try {
            ObservablePagerAdapter pagerAdapter = (ObservablePagerAdapter) vViewPager.getAdapter();
            ObservableFragment fragment = pagerAdapter.getObservableFragment(position);
            View target = pagerAdapter.getObservableFragment(currentItem).getScrollTarget();

            return fragment.onOffsetChanged(smoothAppBarLayout, target, currentOffset);
          } catch (Exception ex) {
            Log.e("SmoothAppBarLayout", String.format(Locale.US,
                "ViewPager at position %d and %d need to implement %s", currentItem, position, ObservableFragment.class.getName()));
          }
        }
      }
      return true;
    }

    private void propagateViewPagerOffset(SmoothAppBarLayout smoothAppBarLayout, boolean isOnPageSelected) {
      if (vViewPager != null) {
        Utils.log("widget | propagateViewPagerOffset | isPageSelected | %b", isOnPageSelected);

        int currentItem = vViewPager.getCurrentItem();
        boolean shouldPropagate = true;
        if (isOnPageSelected) {
          shouldPropagate = propagateViewPagerOffset(smoothAppBarLayout, currentItem);
        }

        if (shouldPropagate) {
          int n = vViewPager.getAdapter().getCount();
          for (int i = 0; i < n; i++) {
            if (i != currentItem) {
              propagateViewPagerOffset(smoothAppBarLayout, i);
            }
          }
        }
      }
    }
  }
}