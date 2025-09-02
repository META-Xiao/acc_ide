package com.acc_ide.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.acc_ide.core.common.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.greenrobot.eventbus.EventBus

abstract class BaseIDEActivity : AppCompatActivity() {

    open val subscribeToEvents: Boolean = false

    open var enableSystemBarTheming: Boolean = true

    open val navigationBarColor: Int
        get() = getColor(android.R.color.black)

    open val statusBarColor: Int
        get() = getColor(android.R.color.black)

    /**
     * [CoroutineScope] for executing tasks with the [Default][Dispatchers.Default] dispatcher.
     */
    val activityScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (enableSystemBarTheming) {
            window?.apply {
                navigationBarColor = this@BaseIDEActivity.navigationBarColor
                statusBarColor = this@BaseIDEActivity.statusBarColor
            }
        }
        super.onCreate(savedInstanceState)
        preSetContentLayout()
        setContentView(bindLayout())
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel any running coroutines
    }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this) && subscribeToEvents) {
            EventBus.getDefault().register(this)
        }
    }

    override fun onStop() {
        super.onStop()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }

    fun loadFragment(fragment: Fragment, id: Int) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(id, fragment)
        transaction.commit()
    }

    protected open fun preSetContentLayout() {}

    protected abstract fun bindLayout(): View
}