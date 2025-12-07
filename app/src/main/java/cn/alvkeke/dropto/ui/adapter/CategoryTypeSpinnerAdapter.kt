package cn.alvkeke.dropto.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.Category.Companion.typeToIconResource
import cn.alvkeke.dropto.data.Category.Companion.typeToName

class CategoryTypeSpinnerAdapter(
    context: Context,
    resource: Int,
    private val types: Array<Category.Type>
) : ArrayAdapter<Category.Type>(
    context, resource,
    types
) {
    override fun getCount(): Int {
        return types.size
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getCustomView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getCustomView(position, convertView, parent)
    }

    private fun getCustomView(pos: Int, view: View?, parent: ViewGroup): View {
        var view = view
        val holder: ViewHolder

        if (view == null) {
            view = LayoutInflater.from(context)
                .inflate (R.layout.spinner_item_category_type,
                    parent, false)
            holder = ViewHolder()
            holder.text = view.findViewById(R.id.spinner_category_type_text)
            holder.icon = view.findViewById(R.id.spinner_category_type_icon)
            view.tag = holder
        } else {
            holder = view.tag as ViewHolder
        }

        val type = types[pos]
        holder.text.text = typeToName(type)
        holder.icon.setImageResource(typeToIconResource(type))
        return view
    }

    private class ViewHolder {
        lateinit var icon: ImageView
        lateinit var text: TextView
    }
}
