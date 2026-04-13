package com.amu.jeeplinkadmin

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.amu.jeeplinkadmin.Home.Dashboard
import com.amu.jeeplinkadmin.Home.Login
import com.amu.jeeplinkadmin.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            navigateTo(Login())
        }
    }

    fun navigateTo(fragment: Fragment, addToBackStack: Boolean = false) {
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
        if (addToBackStack) transaction.addToBackStack(null)
        transaction.commit()
    }
}