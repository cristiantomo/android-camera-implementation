package com.predixtor.beePic.ui.camera;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.predixtor.beePic.data.repository.BeePicRepository;
import com.predixtor.beePic.data.database.camera.MyCamera;

import java.util.List;

public class CameraActivityViewModel extends AndroidViewModel {

    private BeePicRepository mRepository;

    public CameraActivityViewModel(@NonNull Application application) {
        super(application);
        mRepository = new BeePicRepository(application);
    }


    //**********************************************************************************************
    /*                               CAMERA DATABASE                                              */
    //**********************************************************************************************

    public void InsertMyCamerainDb(MyCamera myCamera) {
        mRepository.insertMyCamerainDb(myCamera);
    }

    public void updateMyCameraInDb(MyCamera myCamera) {
        mRepository.updateMyCameraInDb(myCamera);
    }

    public MyCamera getMyCameraFromDb(int cameraId) {
        return mRepository.getMyCameraFromDb(cameraId);
    }

    public boolean isMyCameraAddedToDb(int mCameraId) {
        boolean myCameraIsAdded = false;

        List<Integer> DbIdsList = mRepository.loadMyCameraIds();

        if (DbIdsList == null) {
            return myCameraIsAdded;
        }
        for (int i = 0; i < DbIdsList.size(); i++) {
            if (mCameraId == DbIdsList.get(i)) {
                myCameraIsAdded = true;
                break;
            }
        }
        return myCameraIsAdded;
    }

}
