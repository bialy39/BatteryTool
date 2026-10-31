package io.github.domi04151309.batterytool.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.domi04151309.batterytool.R
import io.github.domi04151309.batterytool.data.SimpleListItem
import io.github.domi04151309.batterytool.interfaces.RecyclerViewHelperInterface

class SimpleListAdapter(
    private val items: List<SimpleListItem>,
    private val helperInterface: RecyclerViewHelperInterface,
) : RecyclerView.Adapter<SimpleListAdapter.ViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder =
        ViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.list_item_simple, parent, false),
        )

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        if (items[position].title.isEmpty()) {
            holder.title.visibility = View.GONE
        } else {
            holder.title.visibility = View.VISIBLE
            holder.title.text = items[position].title
        }
        if (items[position].summary.isEmpty()) {
            holder.summary.visibility = View.GONE
        } else {
            holder.summary.visibility = View.VISIBLE
            holder.summary.text = items[position].summary
        }
        holder.drawable.setImageDrawable(items[position].icon)
        holder.itemView.setOnClickListener { helperInterface.onItemClicked(position) }
        bindAutostartSwitch(holder, position)
    }

    private fun bindAutostartSwitch(
        holder: ViewHolder,
        position: Int,
    ) {
        val item = items[position]
        holder.autostart.setOnCheckedChangeListener(null)
        if (!item.isApp) {
            holder.autostart.visibility = View.GONE
            return
        }
        holder.autostart.visibility = View.VISIBLE
        holder.autostart.isChecked = item.autostartAllowed
        setAutostartState(holder, item.autostartAllowed)
        holder.autostart.setOnCheckedChangeListener { _, isChecked ->
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                item.autostartAllowed = isChecked
                setAutostartState(holder, isChecked)
                helperInterface.onAutostartToggled(currentPosition, isChecked)
            }
        }
    }

    private fun setAutostartState(
        holder: ViewHolder,
        allowed: Boolean,
    ) {
        ViewCompat.setStateDescription(
            holder.autostart,
            holder.itemView.context.getString(
                if (allowed) R.string.autostart_on else R.string.autostart_off,
            ),
        )
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val drawable: ImageView = view.findViewById(R.id.drawable)
        val title: TextView = view.findViewById(R.id.title)
        val summary: TextView = view.findViewById(R.id.summary)
        val autostart: MaterialSwitch = view.findViewById(R.id.autostart_switch)
    }
}
