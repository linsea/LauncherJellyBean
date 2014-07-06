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
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Log;

/**
 * 可以接收Drop动作的对象的抽象,如Folder,workspace等可以接收drop动作.
 * 
 * Interface defining an object that can receive a drag.
 *
 */
public interface DropTarget {

    public static final String TAG = "DropTarget";

    /**
     * 拖动对象,抽象出了拖动相关的信息,如拖动时的悬空视图(DragView), 起始位置,偏移量,拖动发起源(DragSource).
     * 作为参数传给可以接收drop动作的DropTarget,然后DropTarget从中提取出信息进行相应的处理.
     * 最主要的处理方法是DropTarget#onDrop(DragObject)
     */
    class DragObject {
        public int x = -1;
        public int y = -1;

        /** X offset from the upper-left corner of the cell to where we touched.  */
        public int xOffset = -1;//X轴的拖动的偏移量

        /** Y offset from the upper-left corner of the cell to where we touched.  */
        public int yOffset = -1;//Y轴的拖动的偏移量

        /** This indicates whether a drag is in final stages, either drop or cancel. It
         * differentiates onDragExit, since this is called when the drag is ending, above
         * the current drag target, or when the drag moves off the current drag object.
         */
        public boolean dragComplete = false;

        /** 有放大了一点的拖动视图,拖动时会跟随移动. The view that moves around while you drag.  */
        public DragView dragView = null;

        /** The data associated with the object being dragged */
        public Object dragInfo = null;

        /** 从哪个拖动源发出的拖动, workspace/Folder . Where the drag originated */
        public DragSource dragSource = null;

        /** Post drag animation runnable */
        public Runnable postAnimationRunnable = null;

        /** Indicates that the drag operation was cancelled */
        public boolean cancelled = false;

        /**延迟删除 DragView, 要等drop动作的动画完了之后,才能删除DragView.
         * Defers removing the DragView from the DragLayer until after the drop animation. */
        public boolean deferDragViewCleanupPostAnimation = true;

        public DragObject() {
        }
    }

    public static class DragEnforcer implements DragController.DragListener {
        /**一个DragController.DragListener的简单实现,监视Drag的开始与结束,打印Log*/
    	int dragParity = 0;

        public DragEnforcer(Context context) {
            Launcher launcher = (Launcher) context;
            launcher.getDragController().addDragListener(this);
        }

        void onDragEnter() {
            dragParity++;
            if (dragParity != 1) {
                Log.e(TAG, "onDragEnter: Drag contract violated: " + dragParity);
            }
        }

        void onDragExit() {
            dragParity--;
            if (dragParity != 0) {
                Log.e(TAG, "onDragExit: Drag contract violated: " + dragParity);
            }
        }

        @Override
        public void onDragStart(DragSource source, Object info, int dragAction) {
            if (dragParity != 0) {
                Log.e(TAG, "onDragEnter: Drag contract violated: " + dragParity);
            }
        }

        @Override
        public void onDragEnd() {
            if (dragParity != 0) {
                Log.e(TAG, "onDragExit: Drag contract violated: " + dragParity);
            }
        }
    }

    /**
     * Used to temporarily disable certain drop targets
     *
     * @return boolean specifying whether this drop target is currently enabled
     */
    boolean isDropEnabled();

    /**
     * Handle an object being dropped on the DropTarget
     * 
     * @param source DragSource where the drag started
     * @param x X coordinate of the drop location
     * @param y Y coordinate of the drop location
     * @param xOffset Horizontal offset with the object being dragged where the original
     *          touch happened
     * @param yOffset Vertical offset with the object being dragged where the original
     *          touch happened
     * @param dragView The DragView that's being dragged around on screen.
     * @param dragInfo Data associated with the object being dragged
     * 
     */
    void onDrop(DragObject dragObject);

    void onDragEnter(DragObject dragObject);

    void onDragOver(DragObject dragObject);

    void onDragExit(DragObject dragObject);

    /**
     * Handle an object being dropped as a result of flinging to delete and will be called in place
     * of onDrop().  (This is only called on objects that are set as the DragController's
     * fling-to-delete target.
     */
    void onFlingToDelete(DragObject dragObject, int x, int y, PointF vec);

    /**
     * Allows a DropTarget to delegate drag and drop events to another object.
     *
     * Most subclasses will should just return null from this method.
     *
     * @param source DragSource where the drag started
     * @param x X coordinate of the drop location
     * @param y Y coordinate of the drop location
     * @param xOffset Horizontal offset with the object being dragged where the original
     *          touch happened
     * @param yOffset Vertical offset with the object being dragged where the original
     *          touch happened
     * @param dragView The DragView that's being dragged around on screen.
     * @param dragInfo Data associated with the object being dragged
     *
     * @return The DropTarget to delegate to, or null to not delegate to another object.
     */
    DropTarget getDropTargetDelegate(DragObject dragObject);

    /**
     * Check if a drop action can occur at, or near, the requested location.
     * This will be called just before onDrop.
     * 
     * @param source DragSource where the drag started
     * @param x X coordinate of the drop location
     * @param y Y coordinate of the drop location
     * @param xOffset Horizontal offset with the object being dragged where the
     *            original touch happened
     * @param yOffset Vertical offset with the object being dragged where the
     *            original touch happened
     * @param dragView The DragView that's being dragged around on screen.
     * @param dragInfo Data associated with the object being dragged
     * @return True if the drop will be accepted, false otherwise.
     */
    boolean acceptDrop(DragObject dragObject);

    // These methods are implemented in Views
    /**设置可以接收Drop动作的矩形区域*/
    void getHitRect(Rect outRect);
    void getLocationInDragLayer(int[] loc);
    int getLeft();
    int getTop();
}
