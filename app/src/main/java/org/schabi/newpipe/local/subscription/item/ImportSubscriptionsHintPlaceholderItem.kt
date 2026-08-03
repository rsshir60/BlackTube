package org.schabi.newpipe.local.subscription.item

import android.view.View
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ListEmptyViewSubscriptionsBinding

/**
 * When there are no subscriptions, show a hint to the user about how to import subscriptions
 */
class ImportSubscriptionsHintPlaceholderItem : BindableItem<ListEmptyViewSubscriptionsBinding>() {
    override fun getLayout(): Int = R.layout.list_empty_view_subscriptions
    override fun bind(viewBinding: ListEmptyViewSubscriptionsBinding, position: Int) {}
    override fun getSpanSize(spanCount: Int, position: Int): Int = spanCount
    override fun initializeViewBinding(view: View) = ListEmptyViewSubscriptionsBinding.bind(view)
}

