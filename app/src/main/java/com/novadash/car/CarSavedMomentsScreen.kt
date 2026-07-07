package com.novadash.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import dagger.hilt.android.EntryPointAccessors
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Lists the moments already saved (newest first: time + tag). Tapping one removes it. Shares the
 * same [com.novadash.data.MomentsRepository] singleton as the phone app and the save screen.
 */
class CarSavedMomentsScreen(carContext: CarContext) : Screen(carContext) {

    private val moments = EntryPointAccessors
        .fromApplication(carContext.applicationContext, MomentsEntryPoint::class.java)
        .momentsRepository()

    private val timeFmt = SimpleDateFormat("EEE HH:mm", Locale.getDefault())

    override fun onGetTemplate(): Template {
        val limit = carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        val recent = moments.moments.value.take(limit)

        val list = ItemList.Builder()
        if (recent.isEmpty()) {
            list.setNoItemsMessage("No saved moments yet.")
        } else {
            recent.forEach { m ->
                list.addItem(
                    Row.Builder()
                        .setTitle(timeFmt.format(m.epochMillis))
                        .addText(m.tag.ifBlank { "(no tag)" } + " · tap to remove")
                        .setOnClickListener {
                            moments.delete(m.id)
                            CarToast.makeText(carContext, "Removed", CarToast.LENGTH_SHORT).show()
                            invalidate()
                        }
                        .build(),
                )
            }
        }

        return ListTemplate.Builder()
            .setSingleList(list.build())
            .setTitle("Saved moments")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
