/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

abstract class BaseIDEActivity : AppCompatActivity() {

  open val subscribeToEvents: Boolean = false

  open var enableSystemBarTheming: Boolean = true

  open val navigationBarColor: Int
    get() = 0xFF121212.toInt() // Default dark color

  open val statusBarColor: Int
    get() = 0xFF121212.toInt() // Default dark color

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
    // Cancel any ongoing coroutines when activity is destroyed
  }

  override fun onStart() {
    super.onStart()
  }

  override fun onStop() {
    super.onStop()
  }

  fun loadFragment(fragment: Fragment, id: Int) {
    val transaction = supportFragmentManager.beginTransaction()
    transaction.replace(id, fragment)
    transaction.commit()
  }

  protected open fun preSetContentLayout() {}

  protected abstract fun bindLayout(): View
}