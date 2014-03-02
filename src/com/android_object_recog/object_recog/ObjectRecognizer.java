package com.android_object_recog.object_recog;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DMatch;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;

public class ObjectRecognizer {

	private FeatureDetector detector;
	private DescriptorExtractor descriptor;
	private DescriptorMatcher matcher;

	private ArrayList<Mat> trainImages;
	private ArrayList<MatOfKeyPoint> trainKeypoints;
	private ArrayList<Mat> trainDescriptors;
	private ArrayList<String> objectNames;

	private MatchingStrategy matchingStrategy = MatchingStrategy.RATIO_TEST;

	private int numMatches;
	private int matchIndex;
	private int[] numMatchesInImage;

	public ObjectRecognizer(File trainDir) {
		
		ArrayList<File> jpgFiles = Utilities.getJPGFiles(trainDir);
		trainImages = Utilities.getImageMats(jpgFiles);
		objectNames = Utilities.getFileNames(jpgFiles);

		detector = FeatureDetector.create(FeatureDetector.ORB);
		descriptor = DescriptorExtractor.create(DescriptorExtractor.ORB);
		matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);

		trainKeypoints = new ArrayList<MatOfKeyPoint>();
		trainDescriptors = new ArrayList<Mat>();

		for (int i = 0; i < trainImages.size(); i++) {
			trainKeypoints.add(new MatOfKeyPoint());
			detector.detect(trainImages.get(i), trainKeypoints.get(i));
			trainDescriptors.add(new Mat());
			descriptor.compute(trainImages.get(i), trainKeypoints.get(i),
					trainDescriptors.get(i));
		}
		matcher.add(trainDescriptors);
		matcher.train();
	}

	public void removeObject(int clickedImgIdx) {
		trainImages.remove(clickedImgIdx);
		objectNames.remove(clickedImgIdx);
		trainKeypoints.remove(clickedImgIdx);
		trainDescriptors.remove(clickedImgIdx);
		
		matcher.clear();
		matcher.add(trainDescriptors);
		matcher.train();
	}

	public String recognize(Mat mGray) {
		MatOfKeyPoint keypoints = new MatOfKeyPoint();
		Mat descriptors = new Mat();
		List<MatOfDMatch> matches = new LinkedList<MatOfDMatch>();

		detector.detect(mGray, keypoints);
		descriptor.compute(mGray, keypoints, descriptors);
		return match(keypoints, descriptors, matches, matchingStrategy);
	}

	// Parameters for matching
	public static final double RATIO_TEST_RATIO = 0.92;
	public static final int RATIO_TEST_MIN_NUM_MATCHES = 32;

	public String match(MatOfKeyPoint keypoints, Mat descriptors,
			List<MatOfDMatch> matches, MatchingStrategy matchingStrategy) {
		return match_ratioTest(descriptors, matches, RATIO_TEST_RATIO,
				RATIO_TEST_MIN_NUM_MATCHES);

	}
	
	private String match_ratioTest(Mat descriptors, List<MatOfDMatch> matches,
			double ratio, int minNumMatches) {
		getMatches_ratioTest(descriptors, matches, ratio);
		return getDetectedObjIndex(matches, minNumMatches);
	}

	// adds to the matches list matches that satisfy the ratio test with ratio
	// ratio
	private void getMatches_ratioTest(Mat descriptors,
			List<MatOfDMatch> matches, double ratio) {
		LinkedList<MatOfDMatch> knnMatches = new LinkedList<MatOfDMatch>();
		DMatch bestMatch, secondBestMatch;

		matcher.knnMatch(descriptors, knnMatches, 2);
		for (MatOfDMatch matOfDMatch : knnMatches) {
			bestMatch = matOfDMatch.toArray()[0];
			secondBestMatch = matOfDMatch.toArray()[1];
			if (bestMatch.distance / secondBestMatch.distance <= ratio) {
				MatOfDMatch goodMatch = new MatOfDMatch();
				goodMatch.fromArray(new DMatch[] { bestMatch });
				matches.add(goodMatch);
			}
		}
	}

	// uses the list of matches to count the number of matches to each database
	// object. The object with the maximum such number nmax is considered to
	// have been recognized if nmax > minNumMatches.
	// if for a query descriptor there exists multiple matches to train
	// descriptors of the same train image, all such matches are counted as only
	// one match.
	// returns the name of the object detected, or "-" if no object is detected.
	private String getDetectedObjIndex(List<MatOfDMatch> matches,
			int minNumMatches) {
		numMatchesInImage = new int[trainImages.size()];
		matchIndex = -1;
		numMatches = 0;

		for (MatOfDMatch matOfDMatch : matches) {
			DMatch[] dMatch = matOfDMatch.toArray();
			boolean[] imagesMatched = new boolean[trainImages.size()];
			for (int i = 0; i < dMatch.length; i++) {
				if (!imagesMatched[dMatch[i].imgIdx]) {
					numMatchesInImage[dMatch[i].imgIdx]++;
					imagesMatched[dMatch[i].imgIdx] = true;
				}
			}
		}
		for (int i = 0; i < numMatchesInImage.length; i++) {
			if (numMatchesInImage[i] > numMatches) {
				matchIndex = i;
				numMatches = numMatchesInImage[i];
			}
		}
		if (numMatches < minNumMatches) {
			return "-";
		} else {
			return objectNames.get(matchIndex);
		}
	}
}
