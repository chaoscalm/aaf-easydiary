package me.blog.korn123.easydiary.activities

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.PowerManager
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.style.RelativeSizeSpan
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.simplemobiletools.commons.extensions.addBit
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.removeBit
import com.simplemobiletools.commons.helpers.isOreoPlus
import kotlinx.android.synthetic.main.activity_dev.*
import me.blog.korn123.easydiary.R
import me.blog.korn123.easydiary.extensions.config
import me.blog.korn123.easydiary.extensions.makeSnackBar
import me.blog.korn123.easydiary.helper.NOTIFICATION_CHANNEL_DESCRIPTION
import me.blog.korn123.easydiary.helper.NOTIFICATION_CHANNEL_ID
import me.blog.korn123.easydiary.helper.NOTIFICATION_CHANNEL_NAME
import me.blog.korn123.easydiary.receivers.AlarmReceiver
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.pow

class DevActivity : EasyDiaryActivity() {

    /***************************************************************************************************
     *   override functions
     *
     ***************************************************************************************************/
    private lateinit var mAlarm: Alarm
    var mAlarmSequence = 0
    var mAlarmDays = 0

    /***************************************************************************************************
     *   global properties
     *
     ***************************************************************************************************/
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev)
        setSupportActionBar(toolbar)
        supportActionBar?.run {
            title = "Easy-Diary Dev Mode"
            setDisplayHomeAsUpEnabled(true)
        }

        initProperties()
        initDevUI()
        bindEvent()
    }

    /***************************************************************************************************
     *   etc functions
     *
     ***************************************************************************************************/
    private fun initProperties() {
        config.use24HourFormat = false
        val calendar = Calendar.getInstance(Locale.getDefault())
        var minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60
        minutes += calendar.get(Calendar.MINUTE)
        mAlarm = Alarm(0, minutes, 0, isEnabled = false, vibrate = false, soundTitle = "", soundUri = "", label = "")
    }

    private fun initDevUI() {
        updateAlarmTime()

        val dayLetters = resources.getStringArray(R.array.week_day_letters).toList() as ArrayList<String>
        val dayIndexes = arrayListOf(0, 1, 2, 3, 4, 5, 6)
        dayIndexes.forEach {
            val pow = 2.0.pow(it.toDouble()).toInt()
            val day = layoutInflater.inflate(R.layout.alarm_day, edit_alarm_days_holder, false) as TextView
            day.text = dayLetters[it]

            val isDayChecked = mAlarmDays and pow != 0
            day.background = getProperDayDrawable(isDayChecked)

            day.setTextColor(if (isDayChecked) config.backgroundColor else config.textColor)
            day.setOnClickListener {
                val selectDay = mAlarmDays and pow == 0
                mAlarmDays = if (selectDay) {
                    mAlarmDays.addBit(pow)
                } else {
                    mAlarmDays.removeBit(pow)
                }
                day.background = getProperDayDrawable(selectDay)
                day.setTextColor(if (selectDay) config.backgroundColor else config.textColor)
            }

            edit_alarm_days_holder.addView(day)
        }
    }

    private fun getProperDayDrawable(selected: Boolean): Drawable {
        val drawableId = if (selected) R.drawable.circle_background_filled else R.drawable.circle_background_stroke
        val drawable = ContextCompat.getDrawable(this, drawableId)
        drawable!!.applyColorFilter(config.textColor)
        return drawable
    }

    private fun bindEvent() {
        edit_alarm_time.setOnClickListener {
            TimePickerDialog(this, timeSetListener, mAlarm.timeInMinutes / 60, mAlarm.timeInMinutes % 60, DateFormat.is24HourFormat(this)).show()
        }

        test01.setOnClickListener {
            makeSnackBar(getNextAlarmTime())

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val targetMS = System.currentTimeMillis() + (getNextSecond() * 1000)
            val alarm = Alarm(mAlarmSequence++, 0, 0, isEnabled = false, vibrate = false, soundTitle = "", soundUri = "", label = "")
            AlarmManagerCompat.setAlarmClock(alarmManager, targetMS, getOpenAlarmTabIntent(), getAlarmIntent(alarm))
        }
    }

    private val timeSetListener = TimePickerDialog.OnTimeSetListener { view, hourOfDay, minute ->
        mAlarm.timeInMinutes = hourOfDay * 60 + minute
        updateAlarmTime()
    }

    private fun updateAlarmTime() {
        edit_alarm_time.text = getFormattedTime(mAlarm.timeInMinutes * 60, false, true)
    }

    private fun getNextSecond(): Int {
        val calendar = Calendar.getInstance(Locale.getDefault())
        var second = calendar.get(Calendar.HOUR_OF_DAY) * 60 * 60
        second += calendar.get(Calendar.MINUTE) * 60
        return mAlarm.timeInMinutes * 60 - second
    }

    private fun getNextAlarmTime(): String {
        val nextSecond = getNextSecond()
        val hours: Int = nextSecond / 3600
        val minutes: Int = (nextSecond % 3600) / 60
        val second: Int = (nextSecond % 216000) / 60
        return String.format("[%d] %d시간 %d분 %d초 후에 일기장 알람~", nextSecond, hours, minutes, second)
    }

    companion object {
        const val ALARM_ID = "alarm_id"
    }
}


/***************************************************************************************************
 *   extensions
 *
 ***************************************************************************************************/
data class Alarm(var id: Int, var timeInMinutes: Int, var days: Int, var isEnabled: Boolean, var vibrate: Boolean, var soundTitle: String,
                 var soundUri: String, var label: String)

fun Context.isScreenOn() = (getSystemService(Context.POWER_SERVICE) as PowerManager).isScreenOn

fun Context.getOpenAlarmTabIntent(): PendingIntent {
    val diaryMainIntent = Intent(this, DiaryMainActivity::class.java)
    return PendingIntent.getActivity(this, 1000, diaryMainIntent, PendingIntent.FLAG_UPDATE_CURRENT)
}

fun Context.getAlarmIntent(alarm: Alarm): PendingIntent {
    val intent = Intent(this, AlarmReceiver::class.java)
    intent.putExtra(DevActivity.ALARM_ID, alarm.id)
    return PendingIntent.getBroadcast(this, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}

fun Context.showAlarmNotification(alarm: Alarm) {
    val pendingIntent = getOpenAlarmTabIntent()
    val notification = getAlarmNotification(pendingIntent, alarm)
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(alarm.id, notification)
}

@SuppressLint("NewApi")
fun Context.getAlarmNotification(pendingIntent: PendingIntent, alarm: Alarm): Notification {

    if (isOreoPlus()) {
        // Create the NotificationChannel
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, importance)
        mChannel.description = NOTIFICATION_CHANNEL_DESCRIPTION
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        val notificationManager = getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
    }

    val resultNotificationBuilder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
    resultNotificationBuilder
            .setDefaults(Notification.DEFAULT_ALL)
            .setWhen(System.currentTimeMillis())
            .setSmallIcon(R.drawable.ic_launcher_round)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_round))
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentTitle("title")
            .setContentText("content")
            .setContentIntent(pendingIntent)
    return resultNotificationBuilder.build()
}

fun Context.getFormattedTime(passedSeconds: Int, showSeconds: Boolean, makeAmPmSmaller: Boolean): SpannableString {
    val use24HourFormat = config.use24HourFormat
    val hours = (passedSeconds / 3600) % 24
    val minutes = (passedSeconds / 60) % 60
    val seconds = passedSeconds % 60

    return if (!use24HourFormat) {
        val formattedTime = formatTo12HourFormat(showSeconds, hours, minutes, seconds)
        val spannableTime = SpannableString(formattedTime)
        val amPmMultiplier = if (makeAmPmSmaller) 0.4f else 1f
        spannableTime.setSpan(RelativeSizeSpan(amPmMultiplier), spannableTime.length - 5, spannableTime.length, 0)
        spannableTime
    } else {
        val formattedTime = formatTime(showSeconds, use24HourFormat, hours, minutes, seconds)
        SpannableString(formattedTime)
    }
}

fun Context.formatTo12HourFormat(showSeconds: Boolean, hours: Int, minutes: Int, seconds: Int): String {
    val appendable = getString(if (hours >= 12) R.string.p_m else R.string.a_m)
    val newHours = if (hours == 0 || hours == 12) 12 else hours % 12
    return "${formatTime(showSeconds, false, newHours, minutes, seconds)} $appendable"
}

fun formatTime(showSeconds: Boolean, use24HourFormat: Boolean, hours: Int, minutes: Int, seconds: Int): String {
    val hoursFormat = if (use24HourFormat) "%02d" else "%01d"
    var format = "$hoursFormat:%02d"

    return if (showSeconds) {
        format += ":%02d"
        String.format(format, hours, minutes, seconds)
    } else {
        String.format(format, hours, minutes)
    }
}

fun Activity.showOverLockScreen() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
}
