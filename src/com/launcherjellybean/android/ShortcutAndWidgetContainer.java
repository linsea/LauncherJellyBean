/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.launcherjellybean.android;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

/**套在CellLayout里面的一个ViewGroup,可以把CellLayout看作是一个Compound View
 * (Compound View都是ViewGroup,具体怎么实现Compound View参考官方文档),
 * ShortcutAndWidgetContainer是其中的一个View,添加入CellLayout的格子上的View
 * 实际上添加入ShortcutAndWidgetContainer中了.*/
public class ShortcutAndWidgetContainer extends ViewGroup {
    static final String TAG = "CellLayoutChildren";

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    private final int[] mTmpCellXY = new int[2];

    private final WallpaperManager mWallpaperManager;

    private int mCellWidth;
    private int mCellHeight;

    private int mWidthGap;
    private int mHeightGap;

    public ShortcutAndWidgetContainer(Context context) {
        super(context);
        mWallpaperManager = WallpaperManager.getInstance(context);
    }

    public void enableHardwareLayers() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setCellDimensions(int cellWidth, int cellHeight, int widthGap, int heightGap ) {
        mCellWidth = cellWidth;
        mCellHeight = cellHeight;
        mWidthGap = widthGap;
        mHeightGap = heightGap;
    }
    
    /**返回第x列,第y行的那个格子上的View,也即只要那个View包含了(x,y)这个格子*/
    public View getChildAt(int x, int y) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();
            //(x,y)这个格子在View里面
            if ((lp.cellX <= x) && (x < lp.cellX + lp.cellHSpan) &&
                    (lp.cellY <= y) && (y < lp.cellY + lp.cellVSpan)) {
                return child;
            }
        }
        return null;
    }

    @Override//叫子视图们在Canvas上画出自己
    protected void dispatchDraw(Canvas canvas) {
        @SuppressWarnings("all") // suppress dead code warning
        final boolean debug = false;
        if (debug) {
            // Debug drawing for hit space
            Paint p = new Paint();
            p.setColor(0x6600FF00);
            for (int i = getChildCount() - 1; i >= 0; i--) {
                final View child = getChildAt(i);
                final CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();
                //这里为了调试看得清楚格子的边缘,画出了边线.
                canvas.drawRect(lp.x, lp.y, lp.x + lp.width, lp.y + lp.height, p);
            }
        }
        //在调用父类方法之前加入一些自定义的控制,调试信息.
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            measureChild(child);
        }
        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSpecSize =  MeasureSpec.getSize(heightMeasureSpec);
        
        //这里只是简单地传入父亲对自己的尺寸限制,然后作为测量自己尺寸的结果,表示遵循父亲尺寸的要求.
        //因为在measureChild方法里面它同样要求自己的子视图绝对(MeasureSpec.EXACTLY)要遵循自己的尺寸要求,不要超越.
        setMeasuredDimension(widthSpecSize, heightSpecSize);//在onMeasure方法里面一定要调用这个方法
    }

    public void setupLp(CellLayout.LayoutParams lp) {
        lp.setup(mCellWidth, mCellHeight, mWidthGap, mHeightGap);
    }

    public void measureChild(View child) {
        final int cellWidth = mCellWidth;
        final int cellHeight = mCellHeight;
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();

        lp.setup(cellWidth, cellHeight, mWidthGap, mHeightGap);
        int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
        int childheightMeasureSpec = MeasureSpec.makeMeasureSpec(lp.height,
                MeasureSpec.EXACTLY);
        //这里只是传入对子视图的尺寸限制,然后叫子视图测量自己的尺寸
        child.measure(childWidthMeasureSpec, childheightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();

                int childLeft = lp.x;
                int childTop = lp.y;
                child.layout(childLeft, childTop, childLeft + lp.width, childTop + lp.height);

                if (lp.dropped) {
                    lp.dropped = false;

                    final int[] cellXY = mTmpCellXY;
                    getLocationOnScreen(cellXY);
                    mWallpaperManager.sendWallpaperCommand(getWindowToken(),
                            WallpaperManager.COMMAND_DROP,
                            cellXY[0] + childLeft + lp.width / 2,
                            cellXY[1] + childTop + lp.height / 2, 0, null);
                }
            }
        }
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (child != null) {
            Rect r = new Rect();
            child.getDrawingRect(r);
            requestRectangleOnScreen(r);
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();

        // Cancel long press for all children
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            child.cancelLongPress();
        }
    }

    @Override
    protected void setChildrenDrawingCacheEnabled(boolean enabled) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View view = getChildAt(i);
            view.setDrawingCacheEnabled(enabled);
            // Update the drawing caches
            if (!view.isHardwareAccelerated() && enabled) {
                view.buildDrawingCache(true);
            }
        }
    }

    @Override
    protected void setChildrenDrawnWithCacheEnabled(boolean enabled) {
        super.setChildrenDrawnWithCacheEnabled(enabled);
    }
}
