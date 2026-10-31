package io.github.domi04151309.batterytool.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import io.github.domi04151309.batterytool.R
import io.github.domi04151309.batterytool.data.SimpleListItem

/**
 * Rows of the "Running apps" tab. Only the kill button reacts to touches, the row itself stays
 * not clickable. A fresh click listener is installed on every bind, so recycled rows never fire
 * the callback of the item they showed before.
 */
class RunningAppsAdapter(
    private val items: List<SimpleListItem>,
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
        holder.title.text = items[position].title
        holder.summary.text = items[position].summary
        holder.drawable.setImageDrawable(items[position].icon)
        holder.kill.setOnClickListener { onKill(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val drawable: ImageView = view.findViewById(R.id.drawable)
        val title: TextView = view.findViewById(R.id.title)
        val summary: TextView = view.findViewById(R.id.summary)
        val kill: MaterialButton = view.findViewById(R.id.kill)
    }
}
