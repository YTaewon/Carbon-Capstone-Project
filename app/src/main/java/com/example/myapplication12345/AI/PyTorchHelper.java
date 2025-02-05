package com.example.myapplication12345.AI;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import android.content.res.AssetManager;
import java.nio.FloatBuffer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class PyTorchHelper {
    private Module model;

    public PyTorchHelper(AssetManager assetManager) {
        try {
            model = Module.load(assetFilePath(assetManager, "model.pt"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String assetFilePath(AssetManager assetManager, String assetName) throws Exception {
        File file = new File(assetManager.toString(), assetName);
        if (file.exists()) return file.getAbsolutePath();

        InputStream is = assetManager.open(assetName);
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buffer = new byte[is.available()];
        is.read(buffer);
        fos.write(buffer);
        fos.close();
        is.close();

        return file.getAbsolutePath();
    }

    public float[] predict(float[] inputData) {
        Tensor inputTensor = Tensor.fromBlob(inputData, new long[]{1, 2});
        Tensor outputTensor = model.forward(IValue.from(inputTensor)).toTensor();
        return outputTensor.getDataAsFloatArray();
    }
}
