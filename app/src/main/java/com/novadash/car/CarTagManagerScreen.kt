package com.novadash.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.novadash.R
import dagger.hilt.android.EntryPointAccessors

/**
 * In-car tag manager. Tapping a tag removes it; "Add" opens a keyboard screen (only usable while
 * parked — the host disables text entry while driving); "Reset" restores the defaults. Shares the
 * same [com.novadash.data.TagPresetsRepository] singleton as the phone app and the save screen.
 */
class CarTagManagerScreen(carContext: CarContext) : Screen(carContext) {

    private val tagPresets = EntryPointAccessors
        .fromApplication(carContext.applicationContext, MomentsEntryPoint::class.java)
        .tagPresetsRepository()

    override fun onGetTemplate(): Template {
        val limit = carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        val tags = tagPresets.tags.value.take(limit)

        val list = ItemList.Builder()
        if (tags.isEmpty()) {
            list.setNoItemsMessage("No tags — tap Add (parked).")
        } else {
            tags.forEach { tag ->
                list.addItem(
                    Row.Builder()
                        .setTitle(tag)
                        .addText("Tap to remove")
                        .setOnClickListener {
                            tagPresets.remove(tag)
                            CarToast.makeText(carContext, "Removed: $tag", CarToast.LENGTH_SHORT).show()
                            invalidate()
                        }
                        .build(),
                )
            }
        }

        val addAction = Action.Builder()
            .setTitle("Add")
            .setOnClickListener { screenManager.push(CarTagInputScreen(carContext)) }
            .build()
        // Icon-only: an ActionStrip allows only one action with a custom title (that's "Add").
        val resetAction = Action.Builder()
            .setIcon(
                CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_reset))
                    .build(),
            )
            .setOnClickListener {
                tagPresets.resetToDefaults()
                CarToast.makeText(carContext, "Tags reset", CarToast.LENGTH_SHORT).show()
                invalidate()
            }
            .build()

        return ListTemplate.Builder()
            .setSingleList(list.build())
            .setTitle("Manage tags")
            .setHeaderAction(Action.BACK)
            .setActionStrip(ActionStrip.Builder().addAction(addAction).addAction(resetAction).build())
            .build()
    }
}

/**
 * Keyboard screen for adding a new tag. The car host only enables text entry while parked;
 * submitting adds the tag and returns to the manager.
 */
class CarTagInputScreen(carContext: CarContext) : Screen(carContext) {

    private val tagPresets = EntryPointAccessors
        .fromApplication(carContext.applicationContext, MomentsEntryPoint::class.java)
        .tagPresetsRepository()

    override fun onGetTemplate(): Template =
        SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {}

            override fun onSearchSubmitted(searchText: String) {
                val t = searchText.trim()
                if (t.isNotEmpty()) {
                    tagPresets.add(t)
                    CarToast.makeText(carContext, "Added: $t", CarToast.LENGTH_SHORT).show()
                }
                screenManager.pop()
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("New tag")
            .setShowKeyboardByDefault(true)
            .build()
}
