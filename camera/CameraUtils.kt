package com.predixtor.beePic.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat.checkSelfPermission

const val REQUEST_CAMERA_PERMISSION = 1010;


fun checkCameraPermission(context: Context): Boolean {
    if (!isPlatformLessThan23()) {
        if (checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
    }
    return true
}


private fun isPlatformLessThan23(): Boolean {
    return Build.VERSION.SDK_INT < 23
}


fun requestCameraPermission(context: Context) {
    if (!isPlatformLessThan23()) {
        val activity = context as Activity
        requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
    }
}


fun startCamera(context: Context, folderPath: String) {
    val openCameraIntent = Intent(context, CameraActivity::class.java)
    openCameraIntent.putExtra("subFolderPath", folderPath)
    context.startActivity(openCameraIntent)
}





