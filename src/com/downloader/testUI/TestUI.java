
package com.downloader.testUI;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.downloader.R;
import com.downloader.manager.DownloadManager;
import com.downloader.manager.DownloadManager.DownloadListener;

@SuppressLint("SdCardPath")
public class TestUI extends Activity {
    private static final String TAG = "TestUI";

    private ProgressBar mProgressBar1;
    private ProgressBar mProgressBar2;
    private ProgressBar mProgressBar3;
    private TextView mTextView;
    private boolean mFinshed[] = new boolean[3];
    private long[] mStartEndTime = new long[2];

    private Handler mHandler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    mProgressBar1.setMax(msg.arg2);
                    mProgressBar1.setProgress(msg.arg1);
                    break;
                case 2:
                    mProgressBar2.setMax(msg.arg2);
                    mProgressBar2.setProgress(msg.arg1);
                    break;
                case 3:
                    mProgressBar3.setMax(msg.arg2);
                    mProgressBar3.setProgress(msg.arg1);
                    break;
                case 4:
                    String text = "TimeUsed: " + (mStartEndTime[1] - mStartEndTime[0]) + " ms";
                    mTextView.setText(text);

                default:
                    break;
            }
            return false;
        }
    });

    // private String mUrl = "http://192.169.1.100/test/test.mp4";
    // private String mUrl =
    // "http://192.169.1.100/test/6520402478623371804/upgrade.json";
    // private String mUrl = "https://github.com/xbmc/xbmc/archive/master.zip";

    private String mUrl1 = "https://codeload.github.com/cheyiliu/android-multithread-downloader-lib/zip/master";
    private String mUrl2 = "https://codeload.github.com/cheyiliu/android-multithread-downloader-lib/zip/master";
    private String mUrl3 = "https://codeload.github.com/cheyiliu/android-multithread-downloader-lib/zip/master";
    private String mUrl4 = "https://codeload.github.com/cheyiliu/android-multithread-downloader-lib/zip/master";

    private String mLocalPath1 = "/mnt/sdcard/testa1.mp4";
    private String mLocalPath2 = "/mnt/sdcard/testa2.mp4";
    private String mLocalPath3 = "/mnt/sdcard/testa3.mp4";
    private String mLocalPath4 = "/mnt/sdcard/g18ref-ota-20140516.V0801.zip";

    // private String mLocalPath = "/mnt/sdcard/upgradea.json";
    // private String mLocalPath = "/mnt/sdcard/testa.mp4";
    // private String mLocalPath = "/mnt/sdcard/xbmc.zip";
    // private String mLocalPath = "/mnt/sdcard/xbmc2.zip";
    // private String mLocalPath = "/mnt/sdcard/g18ref-ota-20140516.V0801.zip";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_test_ui);
        mProgressBar1 = (ProgressBar) findViewById(R.id.progressBar1);
        mProgressBar2 = (ProgressBar) findViewById(R.id.progressBar2);
        mProgressBar3 = (ProgressBar) findViewById(R.id.progressBar3);
        mTextView = (TextView) findViewById(R.id.textView1);

        mStartEndTime[0] = System.currentTimeMillis();
        DownloadManager.from(this).download(mUrl1, mLocalPath1, new DownloadListener() {
            private long mProgress = 0;

            @Override
            public void onSucess() {
                Log.e("test1", this + "onSucess");
                mFinshed[0] = true;
                if (mFinshed[0] && mFinshed[1] && mFinshed[2]) {
                    mStartEndTime[1] = System.currentTimeMillis();
                    mHandler.sendEmptyMessage(4);
                }

            }

            @Override
            public void onProgress(long step, long max) {
                mProgress += step;
                Log.i("test1", this + "onProgress, " + mProgress + "/" + max);
                Message msg = mHandler.obtainMessage(1);
                msg.arg1 = (int) mProgress;
                msg.arg2 = (int) max;
                mHandler.sendMessage(msg);
            }

            @Override
            public void onFail(int reason) {
                Log.e("test1", this + "onFail, " + reason);
            }
        });

        DownloadManager.from(this).download(mUrl2, mLocalPath2, new DownloadListener() {
            private long mProgress = 0;

            @Override
            public void onSucess() {
                Log.e("test2", this + "onSucess");
                mFinshed[1] = true;
                if (mFinshed[0] && mFinshed[1] && mFinshed[2]) {
                    mStartEndTime[1] = System.currentTimeMillis();
                    mHandler.sendEmptyMessage(4);
                }

            }

            @Override
            public void onProgress(long step, long max) {
                mProgress += step;
                Log.i("test2", this + "onProgress, " + mProgress + "/" + max);
                Message msg = mHandler.obtainMessage(2);
                msg.arg1 = (int) mProgress;
                msg.arg2 = (int) max;
                mHandler.sendMessage(msg);
            }

            @Override
            public void onFail(int reason) {
                Log.e("test2", this + "onFail, " + reason);
            }
        });

        DownloadManager.from(this).download(mUrl3, mLocalPath3, new DownloadListener() {
            private long mProgress = 0;

            @Override
            public void onSucess() {
                Log.e("test3", this + "onSucess");
                mFinshed[2] = true;
                if (mFinshed[0] && mFinshed[1] && mFinshed[2]) {
                    mStartEndTime[1] = System.currentTimeMillis();
                    mHandler.sendEmptyMessage(4);
                }
            }

            @Override
            public void onProgress(long step, long max) {
                mProgress += step;
                Log.i("test3", this + "onProgress, " + mProgress + "/" + max);
                Message msg = mHandler.obtainMessage(3);
                msg.arg1 = (int) mProgress;
                msg.arg2 = (int) max;
                mHandler.sendMessage(msg);
            }

            @Override
            public void onFail(int reason) {
                Log.e("test3", this + "onFail, " + reason);
            }
        });

        DownloadManager.from(this).download(mUrl4, mLocalPath4, new DownloadListener() {
            private long mProgress = 0;

            @Override
            public void onSucess() {
                Log.e("test4", this + "onSucess");
            }

            @Override
            public void onProgress(long step, long max) {
                mProgress += step;
                Log.i("test4", this + "onProgress, " + mProgress + "/" + max);
                // Message msg = mHandler.obtainMessage(40);
                // msg.arg1 = (int) mProgress;
                // msg.arg2 = (int) max;
                // mHandler.sendMessage(msg);
            }

            @Override
            public void onFail(int reason) {
                Log.e("test4", this + "onFail, " + reason);
            }
        });

        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                DownloadManager.from(TestUI.this).cancel(mUrl4);
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        Log.w(TAG, "onDestroy");
        // File file = new File(mLocalPath);
        // if (file.exists()) {
        // file.deleteOnExit();
        // }
        super.onDestroy();
    }

}
