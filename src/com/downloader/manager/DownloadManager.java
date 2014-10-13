
package com.downloader.manager;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

import com.downloader.service.DownloadService;

public class DownloadManager {
    private final Context mContext;
    private static DownloadManager sInstance;

    private DownloadManager(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public synchronized static DownloadManager from(Context context) {
        if (sInstance == null) {
            sInstance = new DownloadManager(context);
        }

        return sInstance;
    }

    public void download(String url, String localFullPath, DownloadListener downloadListener) {
        DownloadReceiver downloadReceiver = new DownloadReceiver(downloadListener);
        Intent intent = new Intent(mContext, DownloadService.class);
        intent.setAction(DownloadService.ACTION_COM_DOWNLOAD_START);
        intent.putExtra(DownloadService.INTENT_EXTRA_RECEIVER, downloadReceiver);
        intent.putExtra(DownloadService.INTENT_EXTRA_URL, url);
        intent.putExtra(DownloadService.INTENT_EXTRA_LOCAL_PATH, localFullPath);
        mContext.startService(intent);
    }

    public void cancel(String url) {
        Intent intent = new Intent(mContext, DownloadService.class);
        intent.setAction(DownloadService.ACTION_COM_DOWNLOAD_CANCEL);
        intent.putExtra(DownloadService.INTENT_EXTRA_URL, url);
        mContext.startService(intent);
    }

    public interface DownloadListener {
        void onSucess();

        void onFail(int reason);

        void onProgress(long progress, long max);
    }

    private class DownloadReceiver extends ResultReceiver {
        private final DownloadListener mDownloadListener;

        public DownloadReceiver(DownloadListener l) {
            super(new Handler(Looper.getMainLooper()));
            mDownloadListener = l;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            switch (resultCode) {
                case DownloadService.STATUS_FINISHED_WITH_ERROR_CODE_NONE:
                    mDownloadListener.onSucess();
                    break;
                case DownloadService.STATUS_UPDATE_PROGRESS:
                    long progress = resultData
                            .getLong(DownloadService.STATUS_UPDATE_PROGRESS_KEY_PROGRESS);
                    long max = resultData
                            .getLong(DownloadService.STATUS_UPDATE_PROGRESS_KEY_MAX);
                    mDownloadListener.onProgress(progress, max);
                    break;
                case DownloadService.STATUS_FINISHED_WITH_ERROR_CODE:
                    int reason = resultData
                            .getInt(DownloadService.STATUS_FINISHED_WITH_ERROR_CODE_KEY_ERROR);
                    mDownloadListener.onFail(reason);
                    break;
                default:
                    break;
            }
        }

    }

}
