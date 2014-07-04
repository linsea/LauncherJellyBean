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

import android.view.View;

import com.launcherjellybean.android.DropTarget.DragObject;

/**
 * 可以发起拖动动作的源的抽象,即在它上面可以发起拖动. 如Folder, workspace等.
 * 这个接口抽象的作用主要是回调,当它上面的Item拖动删除或Drop到某目标后能够收到回调通知并作出处理动作.
 * Interface defining an object that can originate a drag.
 */
public interface DragSource {
    /**
     * 是否支持拖动删除
     * @return whether items dragged from this source supports
     */
    boolean supportsFlingToDelete();

    /**
     * 删除完成后的回调方法
     * A callback specifically made back to the source after an item from this source has been flung
     * to be deleted on a DropTarget.  In such a situation, this method will be called after
     * onDropCompleted, and more importantly, after the fling animation has completed.
     */
    void onFlingToDeleteCompleted();

    /**
     * 当DragSource上一个Item, drop到DropTarget上,完成后的回调方法
     * A callback made back to the source after an item from this source has been dropped on a
     * DropTarget.
     */
    void onDropCompleted(View target, DragObject d, boolean isFlingToDelete, boolean success);
}
