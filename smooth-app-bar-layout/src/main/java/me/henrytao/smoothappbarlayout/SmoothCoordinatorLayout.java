/*
 * Copyright 2017 "Henry Tao <hi@henrytao.me>"
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
import android.support.design.widget.CoordinatorLayout;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;


/**
 * Created by florian on 27/02/2017.
 * (c) Polarsteps
 */

public class SmoothCoordinatorLayout extends CoordinatorLayout {

  private SmoothAppBarLayout mAppBarLayout;

  public SmoothCoordinatorLayout(Context context) {
    super(context);
  }

  public SmoothCoordinatorLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SmoothCoordinatorLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    boolean handled = super.dispatchTouchEvent(ev);
    if (handled && mAppBarLayout != null) {
      mAppBarLayout.dispatchTouchEvent(ev);
    }
    return handled;
  }

  @Override
  public void onLayoutChild(View child, int layoutDirection) {
    super.onLayoutChild(child, layoutDirection);
    if (child instanceof SmoothAppBarLayout) {
      mAppBarLayout = (SmoothAppBarLayout) child;
    }
  }
}
