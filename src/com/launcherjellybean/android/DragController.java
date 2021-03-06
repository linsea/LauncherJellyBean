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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;

import com.launcherjellybean.android.R;

import java.util.ArrayList;

/**
 * 处理拖放的Controller, 既然是MVC中的C,那么肯定有M和V,所以与之关联的类(成员变量)
 * 应该有:
 * Launcher,它是最上面的展示层,逻辑变化最后要体现在它上面,给人看到变化,所以
 * 它是View层(其实是它里面的DragLayer视图层,它是通过Launcher.getDragLayer()间接得到的.)
 * 
 * DropTarget.DragObject,它是Drag动作的封装对象,它里面包含了拖动源,被拖动的对象.它们类似
 * 于MVC中的M, 这些模型封装了与这个拖动动作相关的数据对象.
 * 
 * ArrayList<DropTarget>,潜在的可能并且可以接收Drop动作的对象们.
 * 
 * DragScroller, 实际上是workspace,拖动时可能进入边缘的滚动区域,此时workspace左或右滑动.
 * 所以也与workspace也关联进来了.
 * 
 * mScrollView, 实际上是DragLayer,拖动进入边缘时也要滚动.
 * 
 * 总之想想一个拖动与哪些要素相关,就需要把它作为成员变量包含进来.
 * 
 * Class for initiating a drag within a view or across multiple views.
 */
public class DragController {
    private static final String TAG = "Launcher.DragController";

    /** Indicates the drag is a move.  */
    public static int DRAG_ACTION_MOVE = 0;

    /** Indicates the drag is a copy.  */
    public static int DRAG_ACTION_COPY = 1;

    private static final int SCROLL_DELAY = 500;//拖到边缘第一次滑动的延迟时间
    private static final int RESCROLL_DELAY = 750;//拖到边缘不动,再次滑动时的延迟时间
    private static final int VIBRATE_DURATION = 15;

    private static final boolean PROFILE_DRAWING_DURING_DRAG = false;//调试用的变量

    private static final int SCROLL_OUTSIDE_ZONE = 0;
    private static final int SCROLL_WAITING_IN_ZONE = 1;

    static final int SCROLL_NONE = -1;
    static final int SCROLL_LEFT = 0;
    static final int SCROLL_RIGHT = 1;

    //拖住一个图标,然后猛地向上掷出去,图标就会往上方的删除区域飞去,最后删除掉.
    //此值表示向上掷出去时,方向与Y轴的夹角最大为35度,大于此值掷出去不会被删除.
    private static final float MAX_FLING_DEGREES = 35f;

    private Launcher mLauncher;
    private Handler mHandler;
    private final Vibrator mVibrator;

    // temporaries to avoid gc thrash
    private Rect mRectTemp = new Rect();
    private final int[] mCoordinatesTemp = new int[2];

    /** Whether or not we're dragging. */
    private boolean mDragging;

    /** 按下事件时点的X坐标. coordinate of the down event. */
    private int mMotionDownX;

    /** 按下事件时点的Y坐标.Y coordinate of the down event. */
    private int mMotionDownY;

    /** 距离左/右边缘可以滚动的宽度,单位(dp)
     * the area at the edge of the screen that makes the workspace go left
     *   or right while you're dragging.
     */
    private int mScrollZone;

    /**Drag动作相关信息的封装*/
    private DropTarget.DragObject mDragObject;

    /** Who can receive drop events */
    private ArrayList<DropTarget> mDropTargets = new ArrayList<DropTarget>();
    private ArrayList<DragListener> mListeners = new ArrayList<DragListener>();
    /**接收拖放删除的目标对象,就是最顶上面接收删除的区域*/
    private DropTarget mFlingToDeleteDropTarget;

    /** The window token used as the parent for the DragView. */
    private IBinder mWindowToken;

    /** The view that will be scrolled when dragging to the left and right edges of the screen. 
     * 拖动到边缘时会滚动的视图, 即DragLayer层,进入边缘时DragLayer层也要相应地滚动,不仅仅是workspace滑动.*/
    private View mScrollView;

    private View mMoveTarget;

    /**实际上是workspace*/
    private DragScroller mDragScroller;
    private int mScrollState = SCROLL_OUTSIDE_ZONE;
    private ScrollRunnable mScrollRunnable = new ScrollRunnable();

    //最后接收Drop动作的目标对象.
    //是否拖动的过程中,这个对象会一直变化着,如一个图标在移动的过程中,在文件夹之间游走时,
    //这个值也在多个文件夹之间变化着呢???
    private DropTarget mLastDropTarget;

    private InputMethodManager mInputMethodManager;

    private int mLastTouch[] = new int[2];
    private long mLastTouchUpTime = -1;
    private int mDistanceSinceScroll = 0;

    private int mTmpPoint[] = new int[2];
    private Rect mDragLayerRect = new Rect();

    protected int mFlingToDeleteThresholdVelocity;
    private VelocityTracker mVelocityTracker;

    /**
     * Interface to receive notifications when a drag starts or stops
     */
    interface DragListener {
        
        /**
         * A drag has begun
         * 
         * @param source An object representing where the drag originated
         * @param info The data associated with the object that is being dragged
         * @param dragAction The drag action: either {@link DragController#DRAG_ACTION_MOVE}
         *        or {@link DragController#DRAG_ACTION_COPY}
         */
        void onDragStart(DragSource source, Object info, int dragAction);
        
        /**
         * The drag has ended
         */
        void onDragEnd();
    }
    
    /**
     * Used to create a new DragLayer from XML.
     *
     * @param context The application's context.
     */
    public DragController(Launcher launcher) {
        Resources r = launcher.getResources();
        mLauncher = launcher;
        mHandler = new Handler();
        mScrollZone = r.getDimensionPixelSize(R.dimen.scroll_zone);
        mVelocityTracker = VelocityTracker.obtain();
        mVibrator = (Vibrator) launcher.getSystemService(Context.VIBRATOR_SERVICE);

        float density = r.getDisplayMetrics().density;
        mFlingToDeleteThresholdVelocity = //为什么是负数??? 因为向上滑动
                (int) (r.getInteger(R.integer.config_flingToDeleteMinVelocity) * density);
    }

    public boolean dragging() {
        return mDragging;
    }

    /**
     * Starts a drag.
     *
     * @param v The view that is being dragged
     * @param bmp The bitmap that represents the view being dragged
     * @param source An object representing where the drag originated
     * @param dragInfo The data associated with the object that is being dragged
     * @param dragAction The drag action: either {@link #DRAG_ACTION_MOVE} or
     *        {@link #DRAG_ACTION_COPY}
     * @param dragRegion Coordinates within the bitmap b for the position of item being dragged.
     *          Makes dragging feel more precise, e.g. you can clip out a transparent border
     * @param initialDragViewScale 图标View的Bitmap的放大因子,即放大了几倍.
     */
    public void startDrag(View v, Bitmap bmp, DragSource source, Object dragInfo, int dragAction,
            Rect dragRegion, float initialDragViewScale) {
        int[] loc = mCoordinatesTemp;
        mLauncher.getDragLayer().getLocationInDragLayer(v, loc);
        int dragLayerX = loc[0] + v.getPaddingLeft() +
                (int) ((initialDragViewScale * bmp.getWidth() - bmp.getWidth()) / 2);
        int dragLayerY = loc[1] + v.getPaddingTop() +
                (int) ((initialDragViewScale * bmp.getHeight() - bmp.getHeight()) / 2);

        startDrag(bmp, dragLayerX, dragLayerY, source, dragInfo, dragAction, null, dragRegion,
                initialDragViewScale);

        if (dragAction == DRAG_ACTION_MOVE) {
            v.setVisibility(View.GONE);
        }
    }

    /**
     * Starts a drag.
     *
     * @param b The bitmap to display as the drag image.  It will be re-scaled to the
     *          enlarged size.
     * @param dragLayerX The x position in the DragLayer of the left-top of the bitmap.
     * @param dragLayerY The y position in the DragLayer of the left-top of the bitmap.
     * @param source An object representing where the drag originated
     * @param dragInfo The data associated with the object that is being dragged
     * @param dragAction The drag action: either {@link #DRAG_ACTION_MOVE} or
     *        {@link #DRAG_ACTION_COPY}
     * @param dragRegion Coordinates within the bitmap b for the position of item being dragged.
     *          Makes dragging feel more precise, e.g. you can clip out a transparent border
     */
    public void startDrag(Bitmap b, int dragLayerX, int dragLayerY,
            DragSource source, Object dragInfo, int dragAction, Point dragOffset, Rect dragRegion,
            float initialDragViewScale) {
        if (PROFILE_DRAWING_DURING_DRAG) {
            android.os.Debug.startMethodTracing("Launcher");
        }

        // Hide soft keyboard, if visible
        if (mInputMethodManager == null) {
            mInputMethodManager = (InputMethodManager)
                    mLauncher.getSystemService(Context.INPUT_METHOD_SERVICE);
        }
        mInputMethodManager.hideSoftInputFromWindow(mWindowToken, 0);

        for (DragListener listener : mListeners) {
            listener.onDragStart(source, dragInfo, dragAction);
        }

        final int registrationX = mMotionDownX - dragLayerX;
        final int registrationY = mMotionDownY - dragLayerY;

        final int dragRegionLeft = dragRegion == null ? 0 : dragRegion.left;
        final int dragRegionTop = dragRegion == null ? 0 : dragRegion.top;

        //设置mDragging=true，表示拖拽已经开始  
        //在DragLayer的onInterceptTouchEvent()中根据这个值判断是否拦截MotionEvent
        mDragging = true;

        //实例化DragObject，表示拖拽信息封装对象  
        //封装了拖拽对象的信息  
        mDragObject = new DropTarget.DragObject();

        mDragObject.dragComplete = false;
        mDragObject.xOffset = mMotionDownX - (dragLayerX + dragRegionLeft);
        mDragObject.yOffset = mMotionDownY - (dragLayerY + dragRegionTop);
        mDragObject.dragSource = source;
        mDragObject.dragInfo = dragInfo;

        mVibrator.vibrate(VIBRATE_DURATION);//开始拖动时,振动一下.
        
        //开始画被拖动对象的视图
        final DragView dragView = mDragObject.dragView = new DragView(mLauncher, b, registrationX,
                registrationY, 0, 0, b.getWidth(), b.getHeight(), initialDragViewScale);

        if (dragOffset != null) {
            dragView.setDragVisualizeOffset(new Point(dragOffset));
        }
        if (dragRegion != null) {
            dragView.setDragRegion(new Rect(dragRegion));
        }
        //将拖拽的图标显示在DragLayer中 
        dragView.show(mMotionDownX, mMotionDownY);
        handleMoveEvent(mMotionDownX, mMotionDownY);//转到处理移动事件
    }

    /**
     * Draw the view into a bitmap.
     */
    Bitmap getViewBitmap(View v) {
        v.clearFocus();
        v.setPressed(false);

        boolean willNotCache = v.willNotCacheDrawing();
        v.setWillNotCacheDrawing(false);

        // Reset the drawing cache background color to fully transparent
        // for the duration of this operation
        int color = v.getDrawingCacheBackgroundColor();
        v.setDrawingCacheBackgroundColor(0);
        float alpha = v.getAlpha();
        v.setAlpha(1.0f);

        if (color != 0) {
            v.destroyDrawingCache();
        }
        v.buildDrawingCache();
        Bitmap cacheBitmap = v.getDrawingCache();
        if (cacheBitmap == null) {
            Log.e(TAG, "failed getViewBitmap(" + v + ")", new RuntimeException());
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(cacheBitmap);

        // Restore the view
        v.destroyDrawingCache();
        v.setAlpha(alpha);
        v.setWillNotCacheDrawing(willNotCache);
        v.setDrawingCacheBackgroundColor(color);

        return bitmap;
    }

    /**
     * Call this from a drag source view like this:
     *
     * <pre>
     *  @Override
     *  public boolean dispatchKeyEvent(KeyEvent event) {
     *      return mDragController.dispatchKeyEvent(this, event)
     *              || super.dispatchKeyEvent(event);
     * </pre>
     */
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mDragging;
    }

    public boolean isDragging() {
        return mDragging;
    }

    /**
     * Stop dragging without dropping.
     */
    public void cancelDrag() {
        if (mDragging) {
            if (mLastDropTarget != null) {
                mLastDropTarget.onDragExit(mDragObject);
            }
            mDragObject.deferDragViewCleanupPostAnimation = false;
            mDragObject.cancelled = true;
            mDragObject.dragComplete = true;
            mDragObject.dragSource.onDropCompleted(null, mDragObject, false, false);
        }
        endDrag();
    }
    public void onAppsRemoved(ArrayList<ApplicationInfo> apps, Context context) {
        // Cancel the current drag if we are removing an app that we are dragging
        if (mDragObject != null) {
            Object rawDragInfo = mDragObject.dragInfo;
            if (rawDragInfo instanceof ShortcutInfo) {
                ShortcutInfo dragInfo = (ShortcutInfo) rawDragInfo;
                for (ApplicationInfo info : apps) {
                    // Added null checks to prevent NPE we've seen in the wild
                    if (dragInfo != null &&
                        dragInfo.intent != null &&
                        info.intent != null) {
                        boolean isSamePackage = dragInfo.getPackageName().equals(
                                info.getPackageName());
                        if (isSamePackage) {
                            cancelDrag();
                            return;
                        }
                    }
                }
            }
        }
    }

    private void endDrag() {
        if (mDragging) {
            mDragging = false;
            clearScrollRunnable();
            boolean isDeferred = false;
            if (mDragObject.dragView != null) {
                isDeferred = mDragObject.deferDragViewCleanupPostAnimation;
                if (!isDeferred) {
                    mDragObject.dragView.remove();//不需要DragView了，将其删除  
                }
                mDragObject.dragView = null;
            }

            // Only end the drag if we are not deferred
            if (!isDeferred) {
                for (DragListener listener : mListeners) {//调用回调方法，通知拖拽结束。  
                    listener.onDragEnd();
                }
            }
        }

        releaseVelocityTracker();
    }

    /**
     * This only gets called as a result of drag view cleanup being deferred in endDrag();
     */
    void onDeferredEndDrag(DragView dragView) {
        dragView.remove();

        // If we skipped calling onDragEnd() before, do it now
        for (DragListener listener : mListeners) {
            listener.onDragEnd();
        }
    }

    void onDeferredEndFling(DropTarget.DragObject d) {
        d.dragSource.onFlingToDeleteCompleted();
    }

    /**
     * Clamps the position to the drag layer bounds.
     */
    private int[] getClampedDragLayerPos(float x, float y) {
        mLauncher.getDragLayer().getLocalVisibleRect(mDragLayerRect);
        mTmpPoint[0] = (int) Math.max(mDragLayerRect.left, Math.min(x, mDragLayerRect.right - 1));
        mTmpPoint[1] = (int) Math.max(mDragLayerRect.top, Math.min(y, mDragLayerRect.bottom - 1));
        return mTmpPoint;
    }

    long getLastGestureUpTime() {
        if (mDragging) {
            return System.currentTimeMillis();
        } else {
            return mLastTouchUpTime;
        }
    }

    void resetLastGestureUpTime() {
        mLastTouchUpTime = -1;
    }

    /**
     * 从发起Drag动作的源层(DragLayer层)调用这个方法.
     * Call this from a drag source view.
     */
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        @SuppressWarnings("all") // suppress dead code warning
        final boolean debug = false;
        if (debug) {
            Log.d(Launcher.TAG, "DragController.onInterceptTouchEvent " + ev + " mDragging="
                    + mDragging);
        }

        // Update the velocity tracker
        acquireVelocityTrackerAndAddMovement(ev);

        final int action = ev.getAction();
        final int[] dragLayerPos = getClampedDragLayerPos(ev.getX(), ev.getY());
        final int dragLayerX = dragLayerPos[0];
        final int dragLayerY = dragLayerPos[1];

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_DOWN:
                // Remember location of down touch
                mMotionDownX = dragLayerX;
                mMotionDownY = dragLayerY;
                mLastDropTarget = null;
                break;
            case MotionEvent.ACTION_UP:
                mLastTouchUpTime = System.currentTimeMillis();
                if (mDragging) {
                    PointF vec = isFlingingToDelete(mDragObject.dragSource);
                    if (vec != null) {
                        dropOnFlingToDeleteTarget(dragLayerX, dragLayerY, vec);
                    } else {
                        drop(dragLayerX, dragLayerY);
                    }
                }
                endDrag();//结束拖拽过程
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelDrag();
                break;
        }

        return mDragging;
    }

    /**
     * Sets the view that should handle move events.
     */
    void setMoveTarget(View view) {
        mMoveTarget = view;
    }    

    public boolean dispatchUnhandledMove(View focused, int direction) {
        return mMoveTarget != null && mMoveTarget.dispatchUnhandledMove(focused, direction);
    }

    private void clearScrollRunnable() {
        mHandler.removeCallbacks(mScrollRunnable);
        if (mScrollState == SCROLL_WAITING_IN_ZONE) {
            mScrollState = SCROLL_OUTSIDE_ZONE;
            mScrollRunnable.setDirection(SCROLL_RIGHT);
            mDragScroller.onExitScrollArea();
            mLauncher.getDragLayer().onExitScrollArea();
        }
    }

    /**
     * 主要处理拖拽过程中需要处理的事务。
     * 包括：1、在更新图标在屏幕中的位置，并刷新UI。
     * 2、判断图标当前所处的位置。包括SCROLL_OUTSIDE_ZONE和SCROLL_WAITING_IN_ZONE，
     * 对处于SCROLL_WAITING_IN_ZONE位置时，需要根据具体的位置，向前或向后切换显示的屏幕。
     * 再回到上面假设的情况中。则此时只是简单的刷新了位置信息，并重新绘制图标。
     * @param x
     * @param y
     */
    private void handleMoveEvent(int x, int y) {
        mDragObject.dragView.move(x, y);//更新在DragLayer中的位置  

        // Drop on someone?
        final int[] coordinates = mCoordinatesTemp;
        //根据当前的位置寻找DropTarget对象来放置图标  
        DropTarget dropTarget = findDropTarget(x, y, coordinates);
        mDragObject.x = coordinates[0];
        mDragObject.y = coordinates[1];
        if (dropTarget != null) {
            DropTarget delegate = dropTarget.getDropTargetDelegate(mDragObject);
            if (delegate != null) {
                dropTarget = delegate;
            }

            if (mLastDropTarget != dropTarget) {
                if (mLastDropTarget != null) {
                    mLastDropTarget.onDragExit(mDragObject);//从最后一次记录的DropTarget中退出  
                }
                dropTarget.onDragEnter(mDragObject);//进入到当前寻找到的DropTarget 
            }
            dropTarget.onDragOver(mDragObject);
        } else {
            if (mLastDropTarget != null) {
                mLastDropTarget.onDragExit(mDragObject);
            }
        }
        mLastDropTarget = dropTarget;

        // After a scroll, the touch point will still be in the scroll region.
        // Rather than scrolling immediately, require a bit of twiddling to scroll again
        final int slop = ViewConfiguration.get(mLauncher).getScaledWindowTouchSlop();
        mDistanceSinceScroll +=
            Math.sqrt(Math.pow(mLastTouch[0] - x, 2) + Math.pow(mLastTouch[1] - y, 2));
        mLastTouch[0] = x;
        mLastTouch[1] = y;
        final int delay = mDistanceSinceScroll < slop ? RESCROLL_DELAY : SCROLL_DELAY;

        //判断当前拖拽的图标是否处于ScrollZone即滑动区域。  
        //并且根据在哪个一个ScrollZone来处理屏幕滑动的方向。
        if (x < mScrollZone) {
            if (mScrollState == SCROLL_OUTSIDE_ZONE) {
                mScrollState = SCROLL_WAITING_IN_ZONE;
                if (mDragScroller.onEnterScrollArea(x, y, SCROLL_LEFT)) {
                    mLauncher.getDragLayer().onEnterScrollArea(SCROLL_LEFT);
                    mScrollRunnable.setDirection(SCROLL_LEFT);
                    mHandler.postDelayed(mScrollRunnable, delay);
                }
            }
        } else if (x > mScrollView.getWidth() - mScrollZone) {
            if (mScrollState == SCROLL_OUTSIDE_ZONE) {
                mScrollState = SCROLL_WAITING_IN_ZONE;
                if (mDragScroller.onEnterScrollArea(x, y, SCROLL_RIGHT)) {
                    mLauncher.getDragLayer().onEnterScrollArea(SCROLL_RIGHT);
                    mScrollRunnable.setDirection(SCROLL_RIGHT);
                    mHandler.postDelayed(mScrollRunnable, delay);
                }
            }
        } else {
            clearScrollRunnable();
        }
    }

    public void forceMoveEvent() {
        if (mDragging) {
            handleMoveEvent(mDragObject.x, mDragObject.y);
        }
    }

    /**
     * 从发起Drag动作的源层(DragLayer层)调用这个方法.
     * 
     * Call this from a drag source view. 
     */
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mDragging) {
            return false;
        }

        // Update the velocity tracker
        acquireVelocityTrackerAndAddMovement(ev);

        final int action = ev.getAction();
        final int[] dragLayerPos = getClampedDragLayerPos(ev.getX(), ev.getY());
        final int dragLayerX = dragLayerPos[0];
        final int dragLayerY = dragLayerPos[1];

        switch (action) {
        case MotionEvent.ACTION_DOWN:
            // Remember where the motion event started
            mMotionDownX = dragLayerX;
            mMotionDownY = dragLayerY;
            
            //判断当前的触点是否处于屏幕边缘的ScrollZone，当处于这个区域时  
            //状态mScrollState将转变为SCROLL,并且在一定时间的停留之后，屏幕滑动到另一屏。 
            if ((dragLayerX < mScrollZone) || (dragLayerX > mScrollView.getWidth() - mScrollZone)) {
                mScrollState = SCROLL_WAITING_IN_ZONE;
                mHandler.postDelayed(mScrollRunnable, SCROLL_DELAY);
            } else {
                mScrollState = SCROLL_OUTSIDE_ZONE;
            }
            break;
        case MotionEvent.ACTION_MOVE:
            handleMoveEvent(dragLayerX, dragLayerY);//调用handleMoveEvent()处理图标移动  
            break;
        case MotionEvent.ACTION_UP:
            // Ensure that we've processed a move event at the current pointer location.
            handleMoveEvent(dragLayerX, dragLayerY);
            mHandler.removeCallbacks(mScrollRunnable);//取消可能存放在消息队列中的滑动任务

            if (mDragging) {
                PointF vec = isFlingingToDelete(mDragObject.dragSource);
                if (vec != null) {
                    dropOnFlingToDeleteTarget(dragLayerX, dragLayerY, vec);
                } else {
                    drop(dragLayerX, dragLayerY);//根据目前相对DragLayer的坐标，将图标“降落”到指定的DropTarget上
                }
            }
            endDrag();
            break;
        case MotionEvent.ACTION_CANCEL:
            mHandler.removeCallbacks(mScrollRunnable);
            cancelDrag();
            break;
        }

        return true;
    }

    /**
     * 判断是否是快速滑动删除图标.
     * Determines whether the user flung the current item to delete it.
     * @return 包括X,Y轴方向的速度向量.或者如果此动作不是掷出删除操作,则为null.
     * the vector at which the item was flung, or null if no fling was detected.
     */
    private PointF isFlingingToDelete(DragSource source) {
        if (mFlingToDeleteDropTarget == null) return null;
        if (!source.supportsFlingToDelete()) return null;

        ViewConfiguration config = ViewConfiguration.get(mLauncher);
        mVelocityTracker.computeCurrentVelocity(1000, config.getScaledMaximumFlingVelocity());

      //因为是向上滑动,这里速度是负数,用小于则速度的绝对值大于定义的阈值.
        if (mVelocityTracker.getYVelocity() < mFlingToDeleteThresholdVelocity) {
            // Do a quick dot product test to ensure that we are flinging upwards
            PointF vel = new PointF(mVelocityTracker.getXVelocity(),
                    mVelocityTracker.getYVelocity());
            PointF upVec = new PointF(0f, -1f);
            float theta = (float) Math.acos(((vel.x * upVec.x) + (vel.y * upVec.y)) /
                    (vel.length() * upVec.length()));
            if (theta <= Math.toRadians(MAX_FLING_DEGREES)) {
                return vel;
            }
        }
        return null;
    }

    /**drop到屏幕顶上方的目标区域*/
    private void dropOnFlingToDeleteTarget(float x, float y, PointF vel) {
        final int[] coordinates = mCoordinatesTemp;

        mDragObject.x = coordinates[0];
        mDragObject.y = coordinates[1];

        // Clean up dragging on the target if it's not the current fling delete target otherwise,
        // start dragging to it.
        if (mLastDropTarget != null && mFlingToDeleteDropTarget != mLastDropTarget) {
            mLastDropTarget.onDragExit(mDragObject);//这里清理一下,移出了mLastDropTarget.
        }

        // Drop onto the fling-to-delete target
        boolean accepted = false;
        mFlingToDeleteDropTarget.onDragEnter(mDragObject);
        // We must set dragComplete to true _only_ after we "enter" the fling-to-delete target for
        // "drop"
        mDragObject.dragComplete = true;
        mFlingToDeleteDropTarget.onDragExit(mDragObject);
        if (mFlingToDeleteDropTarget.acceptDrop(mDragObject)) {
            mFlingToDeleteDropTarget.onFlingToDelete(mDragObject, mDragObject.x, mDragObject.y,
                    vel);
            accepted = true;
        }
        mDragObject.dragSource.onDropCompleted((View) mFlingToDeleteDropTarget, mDragObject, true,
                accepted);
    }

    private void drop(float x, float y) {
        final int[] coordinates = mCoordinatesTemp;
        //根据当前的坐标查找适合的DropTarget对象 
        final DropTarget dropTarget = findDropTarget((int) x, (int) y, coordinates);

        mDragObject.x = coordinates[0];
        mDragObject.y = coordinates[1];
        boolean accepted = false;
        if (dropTarget != null) {
            mDragObject.dragComplete = true;
            dropTarget.onDragExit(mDragObject);
            if (dropTarget.acceptDrop(mDragObject)) {
            	//将拖拽的对象放置到指定的DropTarget对象中。  
                dropTarget.onDrop(mDragObject);
                accepted = true;
            }
        }
        mDragObject.dragSource.onDropCompleted((View) dropTarget, mDragObject, false, accepted);
    }

    private DropTarget findDropTarget(int x, int y, int[] dropCoordinates) {
        final Rect r = mRectTemp;

        final ArrayList<DropTarget> dropTargets = mDropTargets;
        final int count = dropTargets.size();
        for (int i=count-1; i>=0; i--) {
            DropTarget target = dropTargets.get(i);
            if (!target.isDropEnabled())
                continue;

            target.getHitRect(r);

            // Convert the hit rect to DragLayer coordinates
            target.getLocationInDragLayer(dropCoordinates);
            r.offset(dropCoordinates[0] - target.getLeft(), dropCoordinates[1] - target.getTop());

            mDragObject.x = x;
            mDragObject.y = y;
            if (r.contains(x, y)) {
                DropTarget delegate = target.getDropTargetDelegate(mDragObject);
                if (delegate != null) {
                    target = delegate;
                    target.getLocationInDragLayer(dropCoordinates);
                }

                // Make dropCoordinates relative to the DropTarget
                dropCoordinates[0] = x - dropCoordinates[0];
                dropCoordinates[1] = y - dropCoordinates[1];

                return target;
            }
        }
        return null;
    }

    /**
     * 
     * @param scroller实际上是workspace
     */
    public void setDragScoller(DragScroller scroller) {
        mDragScroller = scroller;
    }

    public void setWindowToken(IBinder token) {
        mWindowToken = token;
    }

    /**
     * Sets the drag listener which will be notified when a drag starts or ends.
     */
    public void addDragListener(DragListener l) {
        mListeners.add(l);
    }

    /**
     * Remove a previously installed drag listener.
     */
    public void removeDragListener(DragListener l) {
        mListeners.remove(l);
    }

    /**
     * Add a DropTarget to the list of potential places to receive drop events.
     */
    public void addDropTarget(DropTarget target) {
        mDropTargets.add(target);
    }

    /**
     * Don't send drop events to <em>target</em> any more.
     */
    public void removeDropTarget(DropTarget target) {
        mDropTargets.remove(target);
    }

    /**
     * Sets the current fling-to-delete drop target.
     */
    public void setFlingToDeleteDropTarget(DropTarget target) {
        mFlingToDeleteDropTarget = target;
    }

    private void acquireVelocityTrackerAndAddMovement(MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
    }

    private void releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /**
     * Set which view scrolls for touch events near the edge of the screen.
     */
    public void setScrollView(View v) {
        mScrollView = v;
    }

    DragView getDragView() {
        return mDragObject.dragView;
    }

    private class ScrollRunnable implements Runnable {
        private int mDirection;

        ScrollRunnable() {
        }

        public void run() {
            if (mDragScroller != null) {
                if (mDirection == SCROLL_LEFT) {
                    mDragScroller.scrollLeft();
                } else {
                    mDragScroller.scrollRight();
                }
                mScrollState = SCROLL_OUTSIDE_ZONE;//到滚动区域之外了
                mDistanceSinceScroll = 0;
                mDragScroller.onExitScrollArea();
                mLauncher.getDragLayer().onExitScrollArea();

                if (isDragging()) {//如果还没有放手
                    // Force an update so that we can requeue the scroller if necessary
                    forceMoveEvent();//重新进入Move的过程中,可能会在边缘,可能连续滑动到下页
                }
            }
        }

        void setDirection(int direction) {
            mDirection = direction;
        }
    }
}
