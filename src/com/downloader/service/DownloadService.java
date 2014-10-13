
package com.downloader.service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.util.Log;

import com.downloader.util.AvailableSpaceHandler;

public class DownloadService extends Service {
    private static final String TAG = "DownloadService";

    // Intent extra
    public static final String INTENT_EXTRA_LOCAL_PATH = "com.downloadservice.extra.localpath";
    public static final String INTENT_EXTRA_RECEIVER = "com.downloadservice.extra.receiver";
    public static final String INTENT_EXTRA_URL = "com.downloadservice.extra.url";

    // Action
    public static final String ACTION_COM_DOWNLOAD_CANCEL = "action.com.download.cancel";
    public static final String ACTION_COM_DOWNLOAD_START = "action.com.download,start";

    // Status code and key
    public static final int STATUS_UPDATE_PROGRESS = 10;
    public static final int STATUS_FINISHED_WITH_ERROR_CODE_NONE = 20;
    public static final int STATUS_FINISHED_WITH_ERROR_CODE = 30;
    public static final String STATUS_UPDATE_PROGRESS_KEY_PROGRESS = "progress";
    public static final String STATUS_UPDATE_PROGRESS_KEY_MAX = "max";
    public static final String STATUS_FINISHED_WITH_ERROR_CODE_KEY_ERROR = "error";

    // Error code
    public static final int ERRORCODE_GET_FILE_LENGTH_FAILED = -1;
    public static final int ERRORCODE_NO_ENOUGH_SPACE_LEFT = -2;
    public static final int ERRORCODE_HTTP_DOWNLOAD_FAILED = -3;
    public static final int ERRORCODE_USER_CANCELED = -4;

    // Http
    private static final int DEFAULT_HTTP_TIMEOUT = 10 * 1000;
    private static final int DEFAULT_SIZE_PER_THREAD = 1024 * 1024 * 2;// 2M
    private static final long KEEP_ALIVE_TIME = 600;

    // Thread
    private static final int DEFAULT_DOWNLOAD_THREAD_NUM = 3;
    private static final int CORE_SIZE = 5;
    private static final int MAX_SIZE = CORE_SIZE + 5;
    private static final int QUEUE_SIZE = 100;

    //
    private static final long STOP_SELF_DELAY = TimeUnit.SECONDS.toMillis(30L);

    //
    private Map<String, DownloadTask> mTaskMap;
    private ExecutorService mThreadPool;
    private boolean mRedelivery;
    private ArrayList<Future<?>> mFutureList;
    private Handler mHandler;
    private final Runnable mStopSelfRunnable = new Runnable() {
        @Override
        public void run() {
            stopSelf();
        }
    };

    private final Runnable mWorkDoneRunnable = new Runnable() {
        @Override
        public void run() {
            if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
                throw new IllegalStateException(
                        "This runnable can only be called in the Main thread!");
            }

            final ArrayList<Future<?>> futureList = mFutureList;
            for (int i = 0; i < futureList.size(); i++) {
                if (futureList.get(i).isDone()) {
                    futureList.remove(i);
                    i--;
                }
            }

            if (futureList.isEmpty()) {
                mHandler.postDelayed(mStopSelfRunnable, STOP_SELF_DELAY);
            }
        }
    };

    public void setIntentRedelivery(boolean enabled) {
        mRedelivery = enabled;
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        super.onCreate();
        mThreadPool = new ThreadPoolExecutor(
                CORE_SIZE,
                MAX_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(QUEUE_SIZE),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        mHandler = new Handler();
        mFutureList = new ArrayList<Future<?>>();
        mTaskMap = new HashMap<String, DownloadService.DownloadTask>(5);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        Log.i(TAG, "onStart");
        if (intent != null && !TextUtils.isEmpty(intent.getAction())) {
            if (intent.getAction().equals(ACTION_COM_DOWNLOAD_START)) {
                mHandler.removeCallbacks(mStopSelfRunnable);
                mFutureList.add(mThreadPool.submit(new IntentRunnable(intent)));
            } else if (intent.getAction().equals(ACTION_COM_DOWNLOAD_CANCEL)) {
                onCancel(intent);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        onStart(intent, startId);
        return mRedelivery ? START_REDELIVER_INTENT : START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "onDestroy");
        super.onDestroy();
        mThreadPool.shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class IntentRunnable implements Runnable {
        private final Intent mIntent;

        public IntentRunnable(Intent intent) {
            mIntent = intent;
        }

        public void run() {
            onHandleIntent(mIntent);
            String url = mIntent.getStringExtra(DownloadService.INTENT_EXTRA_URL);
            mTaskMap.remove(url);
            mHandler.removeCallbacks(mWorkDoneRunnable);
            mHandler.post(mWorkDoneRunnable);
        }
    }

    protected void onHandleIntent(Intent intent) {
        Log.i(TAG, "onHandleIntent");
        ResultReceiver receiver = intent.getParcelableExtra(INTENT_EXTRA_RECEIVER);
        String url = intent.getStringExtra(DownloadService.INTENT_EXTRA_URL);
        String localPath = intent.getStringExtra(DownloadService.INTENT_EXTRA_LOCAL_PATH);
        DownloadTask downloadTask = new DownloadTask(localPath, url, receiver);
        mTaskMap.put(url, downloadTask);
        downloadTask.run();
    }

    protected void onCancel(Intent intent) {
        Log.i(TAG, "onCancel");
        String url = intent.getStringExtra(DownloadService.INTENT_EXTRA_URL);
        DownloadTask downloadTask = mTaskMap.remove(url);
        if (downloadTask != null) {
            downloadTask.cancel();
        }
    }

    private void reportProgress(ResultReceiver receiver, long step, long max) {
        Log.i(TAG, "reportProgress");
        Bundle data = new Bundle();
        data.putLong(STATUS_UPDATE_PROGRESS_KEY_MAX, max);
        data.putLong(STATUS_UPDATE_PROGRESS_KEY_PROGRESS, step);
        receiver.send(STATUS_UPDATE_PROGRESS, data);
    }

    private void reportSucess(ResultReceiver receiver) {
        Log.i(TAG, "reportSucess");
        receiver.send(STATUS_FINISHED_WITH_ERROR_CODE_NONE, null);
    }

    private void reportDownloadFailure(ResultReceiver receiver, int errorCode) {
        Log.w(TAG, "reportDownloadFailure, " + errorCode);
        Bundle data = new Bundle();
        data.putInt(STATUS_FINISHED_WITH_ERROR_CODE_KEY_ERROR, errorCode);
        receiver.send(STATUS_FINISHED_WITH_ERROR_CODE, data);
    }

    private class DownloadTask {
        private final String mLocalPath;
        private final String mUrlString;
        private final ResultReceiver mReceiver;
        private long mTotalLength;
        private boolean mErrorOccured;
        private boolean mCanceled;

        public DownloadTask(String localPath, String urlString, ResultReceiver receiver) {
            mLocalPath = localPath;
            mUrlString = urlString;
            mReceiver = receiver;
            mTotalLength = 0;
            mErrorOccured = false;
            mCanceled = false;
        }

        public void run() {
            Log.i(TAG, "run");
            mTotalLength = getFileLength(mUrlString);
            if (mTotalLength <= 0) {
                reportDownloadFailure(mReceiver, ERRORCODE_GET_FILE_LENGTH_FAILED);
                return;
            }
            if (mTotalLength > AvailableSpaceHandler.getExternalAvailableSpaceInBytes()) {
                reportDownloadFailure(mReceiver, ERRORCODE_NO_ENOUGH_SPACE_LEFT);
                return;
            }

            handleDownload();
        }

        public void cancel() {
            Log.w(TAG, "cancel");
            mCanceled = true;
        }

        protected void handleDownload() {
            Log.i(TAG, "handleDownload");
            long statTime = System.currentTimeMillis();
            int threadNum = (int) (mTotalLength / DEFAULT_SIZE_PER_THREAD) + 1;
            if (threadNum > DEFAULT_DOWNLOAD_THREAD_NUM) {
                threadNum = DEFAULT_DOWNLOAD_THREAD_NUM;
            }

            final File file = new File(mLocalPath);

            CountDownLatch countDownLatch = new CountDownLatch(threadNum);
            long spanSize = (long) Math.ceil((float) mTotalLength
                    / threadNum);

            for (int i = 0; i < threadNum; i++) {
                long startPos = i * spanSize;
                long endPos = (i + 1) * spanSize - 1;
                if ((threadNum - 1) == i || endPos >= mTotalLength) {
                    endPos = mTotalLength - 1;
                }
                Log.w(TAG, "startPos=" + startPos);
                Log.w(TAG, "endPos=" + endPos);
                RandomAccessFile randomAccessFile = null;
                try {
                    randomAccessFile = new RandomAccessFile(file, "rw");
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "", e);
                    mErrorOccured = true;
                    break;
                }

                if (!mErrorOccured && !mCanceled) {
                    mThreadPool.submit(new SaveFileThread(
                            randomAccessFile,
                            countDownLatch,
                            mUrlString,
                            startPos,
                            endPos,
                            i
                            ));
                }
            }

            if (!mErrorOccured && !mCanceled)
                try {
                    countDownLatch.await();// 异步变同步
                } catch (InterruptedException e) {
                    Log.e(TAG, "", e);
                }

            if (mErrorOccured) {
                reportDownloadFailure(mReceiver,
                        ERRORCODE_HTTP_DOWNLOAD_FAILED);
            } else if (mCanceled) {
                reportDownloadFailure(mReceiver,
                        ERRORCODE_USER_CANCELED);
            } else {
                reportSucess(mReceiver);
            }

            long timeUsed = System.currentTimeMillis() - statTime;
            Log.w(TAG, "handleDownload exited, use " + DEFAULT_DOWNLOAD_THREAD_NUM
                    + " threads to download, time used = "
                    + timeUsed);

        }

        public long getFileLength(String urlString) {
            Log.i(TAG, "getFileLength");
            long length = 0;
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept-Encoding", "identity");
                conn.setReadTimeout(DEFAULT_HTTP_TIMEOUT);
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    length = conn.getContentLength();
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "getFileLength", e);
            }
            return length;
        }

        class SaveFileThread implements Runnable {
            private final RandomAccessFile mRAF;
            private final CountDownLatch mCDL;
            private final String mUrl;
            private final long mStartPos;
            private final long mEndPos;
            private final long mThreadId;

            private HttpURLConnection mHttpConn;

            public SaveFileThread(RandomAccessFile raf, CountDownLatch cdl, String
                    url, long startPos,
                    long endPos, int threadId) {
                mRAF = raf;
                mCDL = cdl;
                mUrl = url;
                mStartPos = startPos;
                mEndPos = endPos;
                mThreadId = threadId;
            }

            private InputStream getInputStreamByPos() {
                Log.i(TAG, "getInputStreamByPos");
                try {
                    if (!TextUtils.isEmpty(mUrl)) {
                        if (mStartPos >= 0 && mEndPos >= 0 && mStartPos < mEndPos) {
                            URL url = new URL(mUrl);
                            mHttpConn = (HttpURLConnection) url.openConnection();
                            mHttpConn.setRequestMethod("GET");
                            mHttpConn.setAllowUserInteraction(true);
                            mHttpConn.setRequestProperty("Keep-Alive", "turnoff");
                            mHttpConn.setConnectTimeout(DEFAULT_HTTP_TIMEOUT);
                            mHttpConn.setRequestProperty("RANGE", "bytes=" + mStartPos + "-" +
                                    mEndPos);
                            return new BufferedInputStream(mHttpConn.getInputStream(),
                                    DEFAULT_SIZE_PER_THREAD);
                        }
                    }
                } catch (Exception ex) {
                    return null;
                }
                return null;
            }

            public void run() {
                Log.w(TAG, mThreadId + " is running");
                try {
                    Thread.sleep(mThreadId * 3000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "", e);
                }

                InputStream is = getInputStreamByPos();
                if (is != null) {
                    try {
                        mRAF.seek(mStartPos);

                        byte[] by = new byte[DEFAULT_SIZE_PER_THREAD];
                        int length = -1;
                        while (0 < (length = is.read(by)) && !mCanceled) {
                            this.mRAF.write(by, 0, length);
                            reportProgress(mReceiver, length, mTotalLength);
                        }
                        is.close();
                        mRAF.close();
                    } catch (IOException e) {
                        mErrorOccured = true;
                        Log.e(TAG, "", e);
                    }
                } else {
                    mErrorOccured = true;
                }
                if (mHttpConn != null) {
                    mHttpConn.disconnect();
                }
                mCDL.countDown();
                Log.w(TAG, mThreadId + " exited");
            }
        }
    }
}
