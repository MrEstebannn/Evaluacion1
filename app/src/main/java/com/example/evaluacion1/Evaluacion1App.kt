package com.example.evaluacion1

import android.app.Application
import com.example.evaluacion1.repository.RepositoryProvider

class Evaluacion1App : Application() {
    override fun onCreate() {
        super.onCreate()
        RepositoryProvider.init(this)
    }
}