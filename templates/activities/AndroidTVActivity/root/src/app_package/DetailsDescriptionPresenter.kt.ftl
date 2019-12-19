package ${escapeKotlinIdentifiers(packageName)}

import ${getMaterialComponentName('android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter', useAndroidX)}

class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {

    override fun onBindDescription(
            viewHolder: AbstractDetailsDescriptionPresenter.ViewHolder,
            item: Any) {
        val movie = item as Movie

        viewHolder.title.text = movie.title
        viewHolder.subtitle.text = movie.studio
        viewHolder.body.text = movie.description
    }
}
