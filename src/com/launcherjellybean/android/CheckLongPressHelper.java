/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.view.View;
/**检查长按事件的辅助类.
 * 传入一个View,然后就可以帮助检查这个View有没有被长按事件监听器处理过.*/
public class CheckLongPressHelper {
    private View mView;//被长按的视图
    private boolean mHasPerformedLongPress;//是否已处理了长按事件
    private CheckForLongPress mPendingCheckForLongPress;

    /**检查视图是否已处理长按事件的Runnable*/
    class CheckForLongPress implements Runnable {
        public void run() {
            if ((mView.getParent() != null) && mView.hasWindowFocus()//为什么要这个条件?
                    && !mHasPerformedLongPress) {
                if (mView.performLongClick()) {
                    //设置视图内部的按下状态.
                    //实际上这里我们"偷偷地"执行了Pressed应该执行的动作,(即View的OnLongClickListener)
                    //但为了使其他的人能够通过检查pressed状态处理它自己的逻辑,故在此恢复其内部pressed状态.
                    mView.setPressed(false);
                    mHasPerformedLongPress = true;//返回检查结果
                }
            }
        }
    }

    public CheckLongPressHelper(View v) {
        mView = v;
    }

    /**做以下准备动作:当长按的时间满足触发长按事件时,调用其OnLongClickListener执行处理方法,
     * 如果这个View没有OnLongClickListener或不处理此事件,则检查的结果为false*/
    public void postCheckForLongPress() {
        mHasPerformedLongPress = false;

        if (mPendingCheckForLongPress == null) {
            mPendingCheckForLongPress = new CheckForLongPress();
        }
        //要等长按事件的时间过期之后,调用长按事件的listener或context menu处理方法,
        //看它们是否能处理这个事件
        mView.postDelayed(mPendingCheckForLongPress, LauncherApplication.getLongPressTimeout());
    }

    /**取消检查及长按事件的Runnable任务*/
    public void cancelLongPress() {
        mHasPerformedLongPress = false;
        if (mPendingCheckForLongPress != null) {
            mView.removeCallbacks(mPendingCheckForLongPress);
            mPendingCheckForLongPress = null;
        }
    }

    public boolean hasPerformedLongPress() {
        return mHasPerformedLongPress;
    }
}
