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
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.novadash.R
import com.novadash.data.SavedMoment
import dagger.hilt.android.EntryPointAccessors

/**
 * Android Auto screen for flagging a moment while driving. Each user-defined quick tag is a big
 * tappable row: one tap stamps a moment with the current time and that tag (no text entry while
 * driving); a final "Save without a tag" row covers anything that doesn't fit a preset. The top
 * bar has icons for the saved-moments list and the tag manager. Tags are managed on the phone in
 * Settings; both sides share the same singleton repositories.
 */
class CarMomentsScreen(carContext: CarContext) : Screen(carContext) {

    private val entryPoint = EntryPointAccessors
        .fromApplication(carContext.applicationContext, MomentsEntryPoint::class.java)
    private val moments = entryPoint.momentsRepository()
    private val tagPresets = entryPoint.tagPresetsRepository()

    private fun saveMoment(tag: String) {
        moments.add(System.currentTimeMillis(), tag, SavedMoment.SOURCE_ANDROID_AUTO)
        val label = if (tag.isBlank()) "Moment saved" else "Saved: $tag"
        CarToast.makeText(carContext, label, CarToast.LENGTH_SHORT).show()
        invalidate()
    }

    override fun onGetTemplate(): Template {
        // The host caps how many rows may show while driving — respect it, leaving room for the
        // trailing "Save without a tag" row.
        val limit = carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        val tags = tagPresets.tags.value.take(maxOf(0, limit - 1))

        val list = ItemList.Builder()
        tags.forEach { tag ->
            list.addItem(
                Row.Builder()
                    .setTitle(tag)
                    .setOnClickListener { saveMoment(tag) }
                    .build(),
            )
        }
        list.addItem(
            Row.Builder()
                .setTitle("Save without a tag")
                .setOnClickListener { saveMoment("") }
                .build(),
        )

        // ActionStrip allows at most 2 actions, icon-only here (nav to the two sub-screens).
        val savedMoments = Action.Builder()
            .setIcon(
                CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_list))
                    .build(),
            )
            .setOnClickListener { screenManager.push(CarSavedMomentsScreen(carContext)) }
            .build()
        val manageTags = Action.Builder()
            .setIcon(
                CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_manage))
                    .build(),
            )
            .setOnClickListener { screenManager.push(CarTagManagerScreen(carContext)) }
            .build()

        return ListTemplate.Builder()
            .setSingleList(list.build())
            .setTitle("Save a moment")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                ActionStrip.Builder().addAction(savedMoments).addAction(manageTags).build(),
            )
            .build()
    }
}
