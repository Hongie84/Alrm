package com.alrm.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alrm.alarm.Alarm
import com.alrm.alarm.ScheduleType
import com.alrm.alarm.Season
import com.alrm.databinding.ItemAlarmBinding
import java.util.Calendar
import java.util.Locale

class AlarmAdapter(
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onEdit:   (Alarm) -> Unit,
    private val onDelete: (Alarm) -> Unit
) : ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(DiffCallback) {

    inner class AlarmViewHolder(private val binding: ItemAlarmBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(alarm: Alarm) {
            binding.textTime.text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
            binding.textLabel.text = alarm.label.ifEmpty { "Alarm" }
            binding.textSchedule.text = describeSchedule(alarm)
            binding.switchEnabled.isChecked = alarm.enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(alarm, isChecked)
            }
            binding.root.setOnClickListener { onEdit(alarm) }
            binding.root.setOnLongClickListener {
                onDelete(alarm)
                true
            }
        }

        private fun describeSchedule(alarm: Alarm): String {
            return when (alarm.scheduleType) {
                ScheduleType.ONCE -> "Once"
                ScheduleType.DAYS_OF_WEEK -> {
                    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    val selected = days.filterIndexed { i, _ ->
                        alarm.isDaySelected(i + 1) // Calendar.SUNDAY=1
                    }
                    if (selected.isEmpty()) "No days selected"
                    else selected.joinToString(", ")
                }
                ScheduleType.MONTHS -> {
                    val months = listOf("Jan","Feb","Mar","Apr","May","Jun",
                        "Jul","Aug","Sep","Oct","Nov","Dec")
                    val selected = months.filterIndexed { i, _ ->
                        alarm.isMonthSelected(i)
                    }
                    if (selected.isEmpty()) "No months" else selected.joinToString(", ")
                }
                ScheduleType.SEASONS -> {
                    val selected = Season.entries.filter { alarm.isSeasonSelected(it) }
                    if (selected.isEmpty()) "No seasons"
                    else selected.joinToString(", ") { it.label }
                }
                ScheduleType.CALENDAR_EVENT -> {
                    "Calendar: ${alarm.calendarKeywords.ifEmpty { "any event" }}"
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val binding = ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlarmViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Alarm>() {
        override fun areItemsTheSame(a: Alarm, b: Alarm) = a.id == b.id
        override fun areContentsTheSame(a: Alarm, b: Alarm) = a == b
    }
}
