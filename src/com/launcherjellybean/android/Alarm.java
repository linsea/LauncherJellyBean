/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.os.Handler;

/**
 * 
 * 一个报警器线程,可设置未来某一时间触发, 并可以设置一个监听者,触发时回调监听者
 */
public class Alarm implements Runnable{
    // if we reach this time and the alarm hasn't been cancelled, call the listener
    private long mAlarmTriggerTime;//触发报警的未来某一时间点

    // if we've scheduled a call to run() (ie called mHandler.postDelayed), this variable is true.
    // We use this to avoid having multiple pending callbacks
    private boolean mWaitingForCallback;//是否已经发出过一个报警调度,即启动了报警计时器,等待通知监听者

    private Handler mHandler;
    private OnAlarmListener mAlarmListener;//报警时的回调接口
    private boolean mAlarmPending = false;//此报警器是未决的,不可用吗?

    public Alarm() {
        mHandler = new Handler();
    }

    public void setOnAlarmListener(OnAlarmListener alarmListener) {
        mAlarmListener = alarmListener;
    }

    // Sets the alarm to go off in a certain number of milliseconds. If the alarm is already set,
    // it's overwritten and only the new alarm setting is used
    public void setAlarm(long millisecondsInFuture) {//设置多少毫秒后报警
        long currentTime = System.currentTimeMillis();
        mAlarmPending = true;
        mAlarmTriggerTime = currentTime + millisecondsInFuture;
        if (!mWaitingForCallback) {
            mHandler.postDelayed(this, mAlarmTriggerTime - currentTime);
            mWaitingForCallback = true;
        }
    }

    public void cancelAlarm() {
        mAlarmTriggerTime = 0;
        mAlarmPending = false;
    }

    // this is called when our timer runs out
    public void run() {
        mWaitingForCallback = false;
        if (mAlarmTriggerTime != 0) {
            long currentTime = System.currentTimeMillis();
            if (mAlarmTriggerTime > currentTime) {//可能由于某些原因而调度提前,这里再次检查并重设
                // We still need to wait some time to trigger spring loaded mode--
                // post a new callback 
                mHandler.postDelayed(this, Math.max(0, mAlarmTriggerTime - currentTime));
                mWaitingForCallback = true;
            } else {
                mAlarmPending = false;
                if (mAlarmListener != null) {
                    mAlarmListener.onAlarm(this);
                }
            }
        }
    }

    /**此报警器是未决的,不可用吗?*/
    public boolean alarmPending() {
        return mAlarmPending;
    }
}

interface OnAlarmListener {
    public void onAlarm(Alarm alarm);
}
