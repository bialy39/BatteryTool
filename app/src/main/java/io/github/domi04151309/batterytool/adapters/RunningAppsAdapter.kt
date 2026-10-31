package io.github.domi04151309.batterytool.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import io.github.domi04151309.batterytool.R
import io.github.domi04151309.batterytool.data.RunningAppItem

/**
 * Rows of the "Running apps" tab. Only the kill button reacts to touches, the row itself stays
 * not clickable. A fresh click listener is installed on every bind, so recycled rows never fire
 * the callback of the item they showed before.
 *
 * A row with `packageName == null` is the empty-state placeholder: it has nothing to kill, so the
 * Kill button is hidden, no listener is attached and its summary is allowed to wrap.
 */
class RunningAppsAdapter(
    private val items: List<RunningAppItem>,
    private val onKill: (position: Int) -> Unit,
) : RecyclerView.Adapter<RunningAppsAdapter.ViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder =
        ViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.list_item_running, parent, false),
        )

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val item = items[position]
        val isPlaceholder = item.packageName == null
        holder.title.text = item.title
        holder.summary.text = item.summary
        holder.summary.maxLines = if (isPlaceholder) PLACEHOLDER_SUMMARY_MAX_LINES else APP_SUMMARY_MAX_LINES
        holder.drawable.setImageDrawable(item.icon)
        holder.kill.visibility = if (isPlaceholder) View.GONE else View.VISIBLE
        holder.kill.setOnClickListener(
            item.packageName?.let {
                View.OnClickListener { onKill(holder.bindingAdapterPosition) }
            },
        )
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val drawable: ImageView = view.findViewById(R.id.drawable)
        val title: TextView = view.findViewById(R.id.title)
        val summary: TextView = view.findViewById(R.id.summary)
        val kill: MaterialButton = view.findViewById(R.id.kill)
    }

    companion object {
        private const val APP_SUMMARY_MAX_LINES = 1
        private const val PLACEHOLDER_SUMMARY_MAX_LINES = 4
    }
}
