/*
 * Copyright 2018, The Android Open Source Project
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

package com.team.sandbox.viewmodels

import android.app.Application
import androidx.lifecycle.*
import com.team.sandbox.database.CustomDatabaseDao
import com.team.sandbox.database.ScheduleItem
import com.team.sandbox.utils.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class ScheduleItemListViewModel(

    val database: CustomDatabaseDao,
    application: Application
) : AndroidViewModel(application) {


    private var lastScheduleItem = MutableLiveData<ScheduleItem?>()

    private val allScheduledItems= database.getAllScheduleItems()

    /**
     * Converted allScheduledItemsto Spanned for displaying.
     */
    val allScheduledItemsString = Transformations.map(allScheduledItems) { allScheduledItems->
        formatNights(allScheduledItems, application.resources)
    }

    /**
     * If lastScheduleItem has not been set, then the START button should be visible.
     */
    val startButtonVisible = Transformations.map(lastScheduleItem) {
        null == it
    }

    /**
     * If lastScheduleItem has been set, then the STOP button should be visible.
     */
    val stopButtonVisible = Transformations.map(lastScheduleItem) {
        null != it
    }

    /**
     * If there are any allScheduledItems in the database, show the CLEAR button.
     */
    val clearButtonVisible = Transformations.map(allScheduledItems ) {
        it?.isNotEmpty()
    }

    /**
     * Request a toast by setting this value to true.
     *
     * This is private because we don't want to expose setting this value to the Fragment.
     */
    private var _showSnackbarEvent = MutableLiveData<Boolean>()

    /**
     * If this is true, immediately `show()` a toast and call `doneShowingSnackbar()`.
     */
    val showSnackBarEvent: LiveData<Boolean>
        get() = _showSnackbarEvent

    /**
     * Variable that tells the Fragment to navigate to a specific [SleepQualityFragment]
     *
     * This is private because we don't want to expose setting this value to the Fragment.
     */

    private val _navigateToSleepQuality = MutableLiveData<ScheduleItem>()

    /**
     * Call this immediately after calling `show()` on a toast.
     *
     * It will clear the toast request, so if the user rotates their phone it won't show a duplicate
     * toast.
     */

    fun doneShowingSnackbar() {
        _showSnackbarEvent.value = false
    }

    /**
     * If this is non-null, immediately navigate to [SleepQualityFragment] and call [doneNavigating]
     */
    val navigateToSleepQuality: LiveData<ScheduleItem>
        get() = _navigateToSleepQuality

    /**
     * Call this immediately after navigating to [SleepQualityFragment]
     *
     * It will clear the navigation request, so if the user rotates their phone it won't navigate
     * twice.
     */
    fun doneNavigating() {
        _navigateToSleepQuality.value = null
    }

    init {
        initializeTonight()
    }

    private fun initializeTonight() {
        viewModelScope.launch {
            lastScheduleItem.value = getLastScheduledItemFromDatabase()
        }
    }

    /**
     *  Handling the case of the stopped app or forgotten recording,
     *  the start and end times will be the same.j
     *
     *  If the start time and end time are not the same, then we do not have an unfinished
     *  recording.
     */
    private suspend fun getLastScheduledItemFromDatabase(): ScheduleItem? {
        var item = database.getLastScheduledItem()
        if (item?.endTimeMilli != item?.startTimeMilli) {
            item = null
        }
        return item
    }

    private suspend fun clear() {
        database.clear()
    }

    private suspend fun update(night: ScheduleItem) {
        database.update(night)
    }

    private suspend fun insert(night: ScheduleItem) {
        database.insert(night)
    }

    /**
     * Executes when the START button is clicked.
     */
    fun onStartTracking() {
        viewModelScope.launch {
            // Create a new night, which captures the current time,
            // and insert it into the database.
            val newNight = ScheduleItem()

            insert(newNight)

            lastScheduleItem.value = getLastScheduledItemFromDatabase()
        }
    }

    /**
     * Executes when the STOP button is clicked.
     */
    fun onStopTracking() {
        viewModelScope.launch {
            // In Kotlin, the return@label syntax is used for specifying which function among
            // several nested ones this statement returns from.
            // In this case, we are specifying to return from launch(),
            // not the lambda.
            val oldNight = lastScheduleItem.value ?: return@launch

            // Update the night in the database to add the end time.
            oldNight.endTimeMilli = System.currentTimeMillis()

            update(oldNight)

            // Set state to navigate to the SleepQualityFragment.
            _navigateToSleepQuality.value = oldNight
        }
    }

    /**
     * Executes when the CLEAR button is clicked.
     */
    fun onClear() {
        viewModelScope.launch {
            // Clear the database table.
            clear()

            // And clear lastScheduleItem since it's no longer in the database
            lastScheduleItem.value = null
        }

        // Show a snackbar message, because it's friendly.
        _showSnackbarEvent.value = true
    }

}