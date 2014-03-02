package com.android_object_recog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import com.android_object_recog.object_recog.ObjectRecognizer;
import com.android_object_recog.object_recog.Utilities;
import com.android_object_recog.R;

import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements CvCameraViewListener2 {

	private Mat mRgba;
	private Mat mGray;

	private CameraBridgeViewBase cameraView;
	private LinearLayout scrollLinearLayout;

	private Handler handler;

	private String detectedObj;
	private String lastDetectedObj;

	private ObjectRecognizer recognizer;
	private TextToSpeech synthesizer;

	private static final int CAPTURE_IMAGE = 100;
	private static final int TTS_CHECK = 200;

	private boolean isSynthesizerInitialized = false;

	private ArrayList<File> imageFiles;

	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
			case LoaderCallbackInterface.SUCCESS: {
				cameraView.enableView();
				cameraView.setFocusable(true);

				recognizer = new ObjectRecognizer(getFilesDir());
			}
				break;
			default: {
				super.onManagerConnected(status);
			}
				break;
			}
		}
	};

	@Override
	public void onResume() {
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this,
				mLoaderCallback);
	}

	TextView zeroObjects;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		scrollLinearLayout = (LinearLayout) findViewById(R.id.scrollLinearLayout);

		detectedObj = "-";

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		handler = new Handler();

		cameraView = (CameraBridgeViewBase) findViewById(R.id.cameraView);
		cameraView.setVisibility(View.VISIBLE);
		cameraView.setCvCameraViewListener(this);

		Intent checkIntent = new Intent();
		checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
		startActivityForResult(checkIntent, TTS_CHECK);

		// prepare textview to insert if database gets empty
		zeroObjects = new TextView(this);
		zeroObjects.setText("There are no objects in your database yet."
				+ "\nStart by clicking the \"New Object\" button above");
		zeroObjects.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.WRAP_CONTENT));
		zeroObjects.setPadding(5, 5, 5, 5);

		imageFiles = Utilities.getJPGFiles(getFilesDir());
		if (imageFiles.size() > 0) {
			for (File file : imageFiles) {
				addImageThumbnail(file, -1);
			}
		} else {
			scrollLinearLayout.addView(zeroObjects);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if (cameraView != null)
			cameraView.disableView();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (cameraView != null)
			cameraView.disableView();
	}

	@Override
	public void onCameraViewStarted(int width, int height) {
	}

	@Override
	public void onCameraViewStopped() {
	}

	@Override
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		mRgba = inputFrame.rgba();
		mGray = inputFrame.gray();
		lastDetectedObj = detectedObj;
		detectedObj = recognizer.recognize(mGray);

		if (isSynthesizerInitialized) {
			if (!detectedObj.equals("-")
					&& !detectedObj.equals(lastDetectedObj)) {
				synthesizer.speak(detectedObj, TextToSpeech.QUEUE_ADD, null);
			}
		}

		handler.post(new EditViewRunnable());

		return mRgba;
	}

	private class EditViewRunnable implements Runnable {
		@Override
		public void run() {
			TextView detectedObjTextView = (TextView) findViewById(R.id.detectedObjTextView);
			detectedObjTextView.setText(detectedObj);
		}
	}

	private class TTSInitListener implements OnInitListener {
		@Override
		public void onInit(int status) {
			isSynthesizerInitialized = true;
		}
	}

	// creates a horizontal linear layout containing the thumbnail of the image
	// in the given file together with its name
	// and inserts it in the activity's scrollView
	private void addImageThumbnail(File file, int index) {
		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.HORIZONTAL);

		ImageView image = new ImageView(this);
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = 4;
		Bitmap thumbnail = ThumbnailUtils.extractThumbnail(
				BitmapFactory.decodeFile(file.getPath(), options), 64, 64);
		image.setImageBitmap(thumbnail);
		image.setPadding(5, 5, 5, 5);

		TextView text = new TextView(this);
		text.setText(file.getName().substring(0,
				file.getName().lastIndexOf(".")));
		text.setPadding(5, 5, 5, 5);

		layout.addView(image);
		layout.addView(text);
		layout.setOnClickListener(viewObject());

		if (index != -1) {
			scrollLinearLayout.addView(layout, index);
		} else {
			scrollLinearLayout.addView(layout);
		}
	}

	int clickedImgIdx;

	// creates a dialog to show a larger image of the clicked object
	View.OnClickListener viewObject() {
		return new View.OnClickListener() {
			public void onClick(View view) {
				clickedImgIdx = scrollLinearLayout.indexOfChild(view);
				File imageFile = imageFiles.get(clickedImgIdx);

				final Dialog imageDialog = new Dialog(MainActivity.this);
				imageDialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);

				LinearLayout dialogLayout = new LinearLayout(MainActivity.this);
				dialogLayout.setLayoutParams(new LayoutParams(
						LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
				dialogLayout.setOrientation(LinearLayout.VERTICAL);
				imageDialog.addContentView(dialogLayout, new LayoutParams(
						LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

				ImageView fullSizeImage = new ImageView(MainActivity.this);
				fullSizeImage.setLayoutParams(new LayoutParams(
						LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inSampleSize = 2;
				Bitmap fullSizeBM = BitmapFactory.decodeFile(
						imageFile.getPath(), options);
				fullSizeImage.setImageBitmap(fullSizeBM);
				fullSizeImage.setAdjustViewBounds(true);
				dialogLayout.addView(fullSizeImage);

				LinearLayout buttonsLayout = new LinearLayout(MainActivity.this);
				buttonsLayout.setLayoutParams(new LayoutParams(
						LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
				buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);

				Button deleteBtn = new Button(MainActivity.this);
				deleteBtn.setText("Delete");
				deleteBtn.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						cameraView.disableView();
						imageDialog.dismiss();
						// delete file
						File toBeDeteled = imageFiles.get(clickedImgIdx);
						toBeDeteled.delete();

						// adjust UI
						imageFiles.remove(clickedImgIdx);
						scrollLinearLayout.removeViewAt(clickedImgIdx);

						// adjust recognizer
						recognizer.removeObject(clickedImgIdx);
						cameraView.enableView();

						// tell user
						if (isSynthesizerInitialized) {
							synthesizer.speak(
									"Object "
											+ toBeDeteled.getName().substring(
													0,
													toBeDeteled.getName()
															.lastIndexOf("."))
											+ " successfully deleted",
									TextToSpeech.QUEUE_FLUSH, null);
						}

						// if database gets empty insert zeroObjects textview
						if (imageFiles.size() == 0) {
							scrollLinearLayout.addView(zeroObjects);
						}
					}
				});
				buttonsLayout.addView(deleteBtn);

				Button cancelBtn = new Button(MainActivity.this);
				cancelBtn.setText("Cancel");
				cancelBtn.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						imageDialog.dismiss();
					}
				});

				buttonsLayout.addView(cancelBtn);
				dialogLayout.addView(buttonsLayout);

				imageDialog.show();

			}
		};
	}

	private String newImgFilename;
	private static File tempDir = Environment
			.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

	// pops up a dialog where user enters the name of the new object and
	// continues to capture its image using a camera
	public void addObject(View view) {
		cameraView.disableView();

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		final EditText input = new EditText(this);
		builder.setCancelable(false)
				.setTitle("New Object")
				.setMessage("Choose a name for your new object")
				.setView(input)
				.setPositiveButton("Proceed", null)
				.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int whichButton) {
								dialog.cancel();
								cameraView.enableView();
							}
						});

		final AlertDialog dialog = builder.create();

		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface d) {
				Button b = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
				b.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						newImgFilename = input.getText().toString();
						if (newImgFilename != null
								&& newImgFilename.length() <= 127
								&& newImgFilename.matches("[a-zA-Z1-9 ]+")
								&& !newImgFilename.matches(" +")) {
							Intent camera = new Intent(
									MediaStore.ACTION_IMAGE_CAPTURE);
							File tempFile = new File(tempDir, newImgFilename
									+ ".jpg");
							try {
								tempFile.createNewFile();
								Uri imageUri = Uri.fromFile(tempFile);
								camera.putExtra(MediaStore.EXTRA_OUTPUT,
										imageUri);
								startActivityForResult(camera, CAPTURE_IMAGE);
								Toast instructionsToast = Toast
										.makeText(
												getBaseContext(),
												"For best results:\n"
														+ "- Fill the photo with your object.\n"
														+ "- Avoid harsh lighting.",
												Toast.LENGTH_LONG);
								instructionsToast.show();

							} catch (IOException e) {
								Toast invalidToast = Toast
										.makeText(
												MainActivity.this,
												"Invalid name.\nObject was not created",
												Toast.LENGTH_SHORT);
								synthesizer.speak("Invalid name",
										TextToSpeech.QUEUE_FLUSH, null);
								invalidToast.show();
								cameraView.enableView();
							} finally {
								dialog.dismiss();
							}
						} else {
							dialog.setMessage("Choose a name for your new object\n\nThe name you entered is invalid. Please retry.");
						}
					}
				});
			}
		});

		dialog.show();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// if TTS check
		if (requestCode == TTS_CHECK) {
			if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
				// success, create the TTS instance
				synthesizer = new TextToSpeech(this, new TTSInitListener());
				synthesizer.setLanguage(Locale.US);
			} else {
				// missing data, install it
				Intent installIntent = new Intent();
				installIntent
						.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
				startActivity(installIntent);
				synthesizer = new TextToSpeech(this, new TTSInitListener());
				synthesizer.setLanguage(Locale.US);
			}
		}
		// if new object has been added to application database
		if (requestCode == CAPTURE_IMAGE) {
			if (resultCode == RESULT_OK) {

				File newFile = new File(getFilesDir(), newImgFilename + ".jpg");
				File tempFile = new File(tempDir, newImgFilename + ".jpg");

				try {
					Utilities.copyFile(tempFile, newFile);
				} catch (IOException e) {

				}

				tempFile.delete();

				// apply change to UI
				// if database was previously empty, remove zeroObjects textview
				if (imageFiles.size() == 0) {
					scrollLinearLayout.removeView(zeroObjects);
				}
				imageFiles.add(newFile);
				Collections.sort(imageFiles);
				int newFileIdx = imageFiles.indexOf(newFile);
				addImageThumbnail(newFile, newFileIdx);

				// tell user
				if (isSynthesizerInitialized) {
					synthesizer.speak("Object " + newImgFilename
							+ " successfully created",
							TextToSpeech.QUEUE_FLUSH, null);
				}

			} else if (resultCode == RESULT_CANCELED) {
				super.onActivityResult(requestCode, resultCode, data);
			} else {
				super.onActivityResult(requestCode, resultCode, data);
			}
		}
	}
}
