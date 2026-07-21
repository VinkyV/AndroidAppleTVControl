package dev.vinkyv.tvcontrol.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.vinkyv.tvcontrol.AtvController

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val atvController = AtvController(app.applicationContext)
}