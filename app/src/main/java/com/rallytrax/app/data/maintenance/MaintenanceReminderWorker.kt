package com.rallytrax.app.data.maintenance

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rallytrax.app.R
import com.rallytrax.app.data.local.dao.MaintenanceDao
import com.rallytrax.app.data.local.dao.VehicleDao
import com.rallytrax.app.data.local.entity.MaintenanceScheduleEntity
import com.rallytrax.app.notifications.NotificationChannels
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Daily WorkManager task that checks all maintenance schedules,
 * updates their status based on date/odometer, and fires notifications.
 *
 * Notification cascade: 30 days, 7 days, 1 day, due date, then every 7 days when overdue.
 */
@HiltWorker
class MaintenanceReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val maintenanceDao: MaintenanceDao,
    private val vehicleDao: VehicleDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val schedules = maintenanceDao.getAllActiveSchedules()
            val now = System.currentTimeMillis()

            for (schedule in schedules) {
                val updatedStatus = computeStatus(schedule, now)
                if (updatedStatus != schedule.status) {
                    maintenanceDao.updateSchedule(schedule.copy(status = updatedStatus))
                }

                // Check if notification should fire
                if (shouldNotify(schedule, now)) {
                    val vehicle = vehicleDao.getVehicleById(schedule.vehicleId)
                    val vehicleName = vehicle?.name ?: "Your vehicle"
                    sendNotification(vehicleName, schedule.serviceType, updatedStatus)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Maintenance reminder worker failed", e)
            Result.retry()
        }
    }

    private fun computeStatus(schedule: MaintenanceScheduleEntity, now: Long): String {
        val nextDueDate = schedule.nextDueDate ?: return MaintenanceScheduleEntity.STATUS_UPCOMING

        val daysUntilDue = TimeUnit.MILLISECONDS.toDays(nextDueDate - now)

        return when {
            daysUntilDue < 0 -> MaintenanceScheduleEntity.STATUS_OVERDUE
            daysUntilDue <= 30 -> MaintenanceScheduleEntity.STATUS_DUE_SOON
            else -> MaintenanceScheduleEntity.STATUS_UPCOMING
        }
    }

    private fun shouldNotify(schedule: MaintenanceScheduleEntity, now: Long): Boolean {
        val nextDueDate = schedule.nextDueDate ?: return false
        val daysUntilDue = TimeUnit.MILLISECONDS.toDays(nextDueDate - now)

        // Notification cascade: 30, 7, 1, 0 days before, then every 7 days overdue
        return daysUntilDue.toInt() in listOf(30, 7, 1, 0) ||
            (daysUntilDue < 0 && (-daysUntilDue) % 7 == 0L)
    }

    private fun sendNotification(vehicleName: String, serviceType: String, status: String) {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)

        val title = when (status) {
            MaintenanceScheduleEntity.STATUS_OVERDUE -> "Maintenance Overdue"
            MaintenanceScheduleEntity.STATUS_DUE_SOON -> "Maintenance Due Soon"
            else -> "Maintenance Reminder"
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("$vehicleName: $serviceType")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(serviceType.hashCode(), notification)
    }

    companion object {
        private const val TAG = "MaintenanceReminderWorker"
        const val CHANNEL_ID = NotificationChannels.MAINTENANCE
        const val WORK_NAME = "maintenance_reminder_check"
    }
}
