android-multithread-downloader-lib
==================================

android-multithread-downloader-lib


### usage
```
String mUrl1 = "https://codeload.github.com/cheyiliu/android-multithread-downloader-lib/zip/master";
String mLocalPath1 = "/mnt/sdcard/android-multithread-downloader-lib.zip";

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
```


### todo
* 断点续传 (思路，本次暂停时保存文件同时，保存每个下载子任务的start end状态)

### more reading
* http://stackoverflow.com/questions/3028306/download-a-file-with-android-and-showing-the-progress-in-a-progressdialog

