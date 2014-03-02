package com.android_object_recog.object_recog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

public class Utilities {
	
	public static ArrayList<File> getJPGFiles(File dir) {
		File[] files = dir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File file, String name) {
				return name.endsWith(".jpg");
			}
		});
		
		Arrays.sort(files);
		ArrayList<File> jpgFiles = new ArrayList<File>();

		for (File file : files) {
			jpgFiles.add(file);
		}
		
		return jpgFiles;
	}

	public static ArrayList<Mat> getImageMats(ArrayList<File> imageFiles) {
		ArrayList<Mat> imageMats = new ArrayList<Mat>();
		
		for (File image : imageFiles) {
			Mat fullSizeTrainImg = Highgui.imread(image.getPath());
			Mat resizedTrainImg = new Mat();
			Imgproc.resize(fullSizeTrainImg, resizedTrainImg, new Size(640, 480), 0, 0, Imgproc.INTER_CUBIC);
			imageMats.add(resizedTrainImg);
		}
		
		return imageMats;
	}
	
	public static ArrayList<String> getFileNames(ArrayList<File> imageFiles) {
		ArrayList<String> fileNames = new ArrayList<String>();
		
		for (File image : imageFiles) {
			fileNames.add(image.getName().substring(0, image.getName().lastIndexOf(".")));
		}
		
		return fileNames;
	}

	public static void copyFile(File src, File dst) throws IOException {
		InputStream inputStream = new FileInputStream(src);
		OutputStream outputStream = new FileOutputStream(dst);

		byte[] buf = new byte[1024];
		int len;
		while ((len = inputStream.read(buf)) > 0) {
			outputStream.write(buf, 0, len);
		}
		inputStream.close();
		outputStream.close();
	}
}
