package com.mp3tunes.android.player.service;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.ResponseHandler;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.binaryelysium.mp3tunes.api.HttpClientCaller;
import com.binaryelysium.mp3tunes.api.Track;
import com.binaryelysium.mp3tunes.api.HttpClientCaller.Progress;
import com.binaryelysium.util.FileUtils;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.util.Pair;
import com.mp3tunes.android.player.util.StorageInfo;

public class TrackDownloader
{
    int                                 mNextJobId = 1;
    PriorityBlockingQueue<Job>          mQueue;
    boolean                             mDestroying;
    HttpClientCaller.Progress           mProgressCallback;
    private Job                         mJob;
    private OutputStreamResponseHandler mOutputHandler;
    private Object                      mChangingTrackLock;
    private Context                     mContext;
    
    private final int POLL = 0;
    
    static final public class Priority
    {
        static final int NOWPLAYING   = 400;
        static final int NEXTTRACK    = 300;
        static final int FUTURETRACK  = 200;
        static final int SKIPPEDTRACK = 100;
        static final int FORSTORAGE   = 0;
    }
    public TrackDownloader(Context context, Object lock)
    {
        mNextJobId         = 0;
        mQueue             = new PriorityBlockingQueue<Job>(10, new JobComparator());
        mDestroying        = false;
        mChangingTrackLock = lock;
        mContext           = context;
        mHandler.handleMessage(mHandler.obtainMessage(POLL));
    }
    
    //returns the Job Id of the track we are going to download
    public Pair<Integer, CachedTrack> downloadTrack(Track track,  int priority, String format, int bitrate)
    {
        try {
            //Create new job to get file
            String path = getCachePath(track.getFileKey(), format, bitrate);
            Logger.log("created file at: " + path);
            CachedTrack cached = new CachedTrack(track, path, HttpServer.pathToUrl(path), format, bitrate);
            Job job    = new Job(priority, cached, cached.getPlayUrl());
            addJob(job);
            return new Pair<Integer, CachedTrack>(job.id, cached);
        } catch (AlreadyDownloadedException e) {
            //we already have a file. First we see if there is a job running for it
            String path = e.getPath();
            Job job = getJobByPath(path);
            if (job != null) {
                job.priority = priority;
                return new Pair<Integer, CachedTrack>(job.id, job.track);
            }
            //There is no job running this means that 
            CachedTrack cached = new CachedTrack(track, path, HttpServer.pathToUrl(path), format, bitrate);
            cached.setStatus(CachedTrack.Status.finished);
            return new Pair<Integer, CachedTrack>(null, cached);
        }
    }

    //Note: It is not safe to stream files downloaded with this call until the job that is completed
    public Pair<Integer, CachedTrack> downloadTrack(Track track) throws AlreadyDownloadedException
    {
        try {
            String path = getLocalStoragePath(track);
            CachedTrack cached = new CachedTrack(track, path, HttpServer.pathToUrl(path), null, 0);
            Job job    = new Job(Priority.FORSTORAGE, cached, cached.getUrl());
            addJob(job);
            return new Pair<Integer, CachedTrack>(job.id, cached);
        } catch (AlreadyDownloadedException e) {
            //we already have a file. First we see if there is a job running for it
            String path = e.getPath();
            Job job = getJobByPath(path);
            if (job != null) {
                job.priority = Priority.FORSTORAGE;
                return new Pair<Integer, CachedTrack>(job.id, job.track);
            }
        
            //There is no job running this means that 
            CachedTrack cached = new CachedTrack(track, path, HttpServer.pathToUrl(path), null, 0);
            cached.setStatus(CachedTrack.Status.finished);
            return new Pair<Integer, CachedTrack>(null, cached);
        }
    }
    
    private Job getJobByPath(String path)
    {
        synchronized (mQueue) {
            for (Job job : mQueue) {
                if (job.track.getPath().equals(path)) 
                    return job;
            }
            if (mJob != null && mJob.track.getPath().equals(path))
                return mJob;
        }
        return null;
    }
    
    //This is kind of a weird function.  Its goal is to reset the priority of all
    //of the items in the queue to less than a certain value
    public void setMaxPriority(int priority)
    {
        synchronized (mQueue) {
            do {
                Job job = mQueue.poll();
                if (job == null) break;
                if (job.priority > Priority.SKIPPEDTRACK) {
                    job.priority = Priority.SKIPPEDTRACK;
                    mQueue.add(job);
                } else {
                    mQueue.add(job);
                    break;
                }
            } while (true);
        }
    }
    
    public void resetPriority(Integer id, int priority)
    {
        synchronized (mQueue) {
            
            if (mJob.priority == Priority.NOWPLAYING) {
                Logger.log("resetPriority(): Cancelling  download of: " + mJob.track.getTitle());
                mJob.cancelled = true;
            }
            
            Logger.log("resetPriority(): called with id: " + id.intValue() + " priority: " + priority + " num jobs: " + mQueue.size());
            for (Job job : mQueue) {
                Logger.log("resetPriority(): mQueue job " + job.id + " downloads: " + job.track.getTitle());
                if (id.equals(job.id)) {
                    mQueue.remove(job);
                    Logger.log("Changing priority of: " + job.track.getTitle() + " from: " + job.priority + " to: " + priority);
                    job.priority = priority;
                    if (mQueue.add(job))
                        Logger.log("Changing priority successful");
                    else 
                        Logger.log("Changing priority failed");
                    break;
                }
            }
            Logger.log("resetPriority() done");
        }
    }
    
    private void addJob(Job job)
    {
        synchronized (job.track) {
            job.track.setStatus(CachedTrack.Status.queued);
            mQueue.add(job);     
            Logger.log("addJob(): job count: " + mQueue.size());
        }
    }

    private String getCachePath(String fileKey, String format, int bitrate) throws AlreadyDownloadedException
    {
        try {
            String dir = Music.getMP3tunesCacheDir();
            if (dir != null) {
                String name = fileKey + "_" + Integer.toString(bitrate) + "." + format;
                File file = new File(dir, name);
                if (file.createNewFile()) return file.getAbsolutePath();
                throw new AlreadyDownloadedException(file.getAbsolutePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private String getLocalStoragePath(Track track) throws AlreadyDownloadedException
    {
        try {
            String dir = Music.getMP3tunesMusicDir();
            if (dir != null) {
                String name = track.getArtistName() + "-" + track.getTitle();
                name = name.replaceAll(" ", "_");
                name = name.replace("/", "_slash_");
                name = name.replace(".", "_dot_");
                name += ".tmp";
                File file = new File(dir, name);
                if (file.createNewFile()) return file.getAbsolutePath();
                throw new AlreadyDownloadedException(file.getAbsolutePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private class Job
    {
        int              id;
        int              priority;
        CachedTrack      track;
        String           url;
        FileOutputStream stream;
        Boolean          cancelled;
        
        
        public Job(int priority, CachedTrack track, String url) 
        {
            this.id        = mNextJobId;
            this.priority  = priority;
            this.track     = track;
            this.url       = url;
            this.cancelled = false;
            try {
                Logger.log("Creating stream for file: " + track.getPath());
                this.stream   = new FileOutputStream(track.getPath());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                assert(false);
                //This should be an impossible situation.  We literally just successfully created this file a few microseconds ago.
                //If it is no longer there then what can we do?
            }

            mNextJobId++;
        }

    }
    
    public class JobComparator implements Comparator<Job>
    {

        public int compare(Job first, Job second)
        {
            if (first.priority > second.priority) return -1;
            if (first.priority < second.priority) return  1;
            return 0;
        }
        
    }
    
    
    private final Handler mHandler = new Handler() {
        
        private int mCount = 0;
        private void pollNext(long delay) 
        {
            Message msg = mHandler.obtainMessage(POLL);
            mHandler.removeMessages(POLL);
            mHandler.sendMessageDelayed(msg, delay);
        }

        
        void fail()
        {
            Logger.log("Download of track: '" + mJob.track.getTitle() + "' by: '" + mJob.track.getArtistName() + "' Failed");
            Logger.log("At: " + mJob.url);
            mJob.track.setStatus(CachedTrack.Status.failed);
        }
        
        void succeed()
        {
            Logger.log("Download of track: '" + mJob.track.getTitle() + "' by: '" + mJob.track.getArtistName() + "' successful");
            Logger.log("At: " + mJob.url);
            mJob.track.setStatus(CachedTrack.Status.finished);
        }
        
        //We need to have this function to support using lockerget.  When we run a lockerget we do not know the type
        //of audio file we are going to get until we parse the headers.  Since we create the file stream before we parse
        //the headers this means that for lockergets we download the file to a .tmp location and move the file to the correct
        //extention once that is done
        void moveToPermenentLocation()
        {
            if (mJob.track.getStatus() == CachedTrack.Status.finished ) {
                String contentType = mOutputHandler.getContentType();
                //If we did not know what the file format was when we started the download then we need to see
                //if we can get it from the contentType
                if (mJob.track.getPath().endsWith(".tmp")) {
                    if (contentType != null) {
                        String oldFile = mJob.track.getPath();
                        String newFile = copyFileToCorrectExtension(contentType, oldFile);
                        if (newFile != null) {
                            mJob.track.setPath(newFile);
                            mJob.track.setCachedUrl(HttpServer.pathToUrl(newFile));
                            if (!new File(oldFile).delete()) {
                                Logger.log("Failed to delete old file: " + oldFile);
                            }
                        } else {
                            fail();
                        }
                    } else {
                        //This should be a problem with our server so we want to crash here so we know about it
                        //ASAP.  If we do find that a network error can get us here then we need to make sure 
                        //that call stream returns false or throws and IOException
                        assert(false);
                    }
                }
            }
        }
        
        boolean setupNextJob()
        {
            if (mCount > 240) mCount = 0;
            mCount++;
            if (mCount == 1) Logger.log("handleMessage() waiting on lock");
            
            synchronized (mChangingTrackLock) {
                if (mCount == 1) Logger.log("handleMessage() obtained lock");
                
                synchronized (mQueue) {
                try {
                    if (mJob.track.getStatus() == CachedTrack.Status.failed &&
                        mJob.priority          != Priority.NOWPLAYING       &&
                        mJob.cancelled         != true) {
                            mJob.stream.close();
                            mJob.stream = new FileOutputStream(mJob.track.getPath());
                            mJob.track.setStatus(CachedTrack.Status.downloading);
                            mQueue.add(mJob);
                    }
                } catch (Exception e) {}

                    for (Job job : mQueue) {
                            Logger.log("Priority of: " + job.track.getTitle() + " is: " + job.priority);
                    }
                    mJob = mQueue.poll();
                }
                if (mJob == null) return false;
                Logger.log("handleMessage(): job count: " + mQueue.size());
                Logger.log("");
                mJob.track.setStatus(CachedTrack.Status.downloading);
                mOutputHandler = new OutputStreamResponseHandler(mJob);
                Logger.log("Begining download of track: '" + mJob.track.getTitle() + "' by: '" + mJob.track.getArtistName() + "'");
                Logger.log("At: " + mJob.url);
                Logger.log("Priority: " + mJob.priority);
                if (mCount == 1) Logger.log("handleMessage() giving up lock");
                
                //check to make sure we have cache space
                freeCacheSpace();
            }
            return true;
        }
        
        void handleSocketException()
        {
            try {
                if (mOutputHandler.mReturn) {
                    succeed();
                } else {
                    fail();
                }
            } catch (Exception e) {
                Logger.log(e);
                fail();
            }
        }
        
        void performDownload() throws IOException
        {
            if (!HttpClientCaller.getInstance().callStream(mJob.url, mOutputHandler, null)) {
                throw new IOException();
            }
            succeed();
        }
        
        public void handleMessage(Message msg) {
            if (msg.what == POLL) {
                Thread t = new Thread() {
                    public void run() {
                        while (!mDestroying) {
                            if (!setupNextJob()) break;
                            try {
                                performDownload();
                            } catch (SocketTimeoutException e) {
                                handleSocketException();
                            } catch (SocketException e) {
                                handleSocketException();
                            } catch (IOException e) {
                                e.printStackTrace();
                                fail();
                            }
                            moveToPermenentLocation();
                            
                        }
                        mHandler.post(mPoll);
                    }
                };
                t.start();
            }
        }
        Runnable mPoll = new Runnable() {
            public void run()
            {
                pollNext(250);
            }
        };
        
        public String copyFileToCorrectExtension(String contentType, String file)
        {
            String extension = null;
            if (contentType.equals("audio/mpeg")) {
                extension += ".mp3";
            } else if (contentType.equals("audio/mp4")) {
                extension += ".mp4";
            } else if (contentType.equals("audio/ogg")) {
                extension += ".ogg";
            } else if (contentType.equals("audio/vorbis")) {
                extension += ".ogg";
            } else if (contentType.equals("application/ogg")) {
                extension += ".ogg";
            } else if (contentType.equals("audio/x-ms-wma")) {
                extension += ".wma";
            } else if (contentType.equals("video/quicktime")) {
                Log.w("Mp3Tunes", "inserting a video file video/quicktime");
                extension += ".mp4";
            } else if (contentType.equals("video/mp4")) {
                Log.w("Mp3Tunes", "inserting a video file video/mp4");
                extension += ".mp4";
            } else if (contentType.equals("video/x-ms-wmv")) {
                Log.w("Mp3Tunes", "inserting a video file video/x-ms-wmv");
                extension += ".wmv";
            }
            if (extension == null) return null;
            String newFile = file.replace(".tmp", extension);
            try {
                FileUtils.copyFile(file, newFile);
            } catch (IOException e) {
                return null;
            }
            return newFile;
        }
    };
    
    
    static private class OutputStreamResponseHandler implements ResponseHandler<Boolean>  
    {
        Job          mJob;
        String       mContentType;
        Object       mLock   = new Object();
        Boolean      mReturn = null;
        
        OutputStreamResponseHandler(Job job)
        {
            mJob = job;
        }
        
        public String getContentType()
        {
            return mContentType;
        }
        
        public Boolean handleResponse(HttpResponse response) throws ClientProtocolException, IOException 
        {
            if (mReturn != null) return mReturn;
            mReturn = false;
            
            String contentType = "";
            for (Header h :response.getAllHeaders()) {
                if (h.getName().equals("Content-Type")) mContentType = h.getValue();
            }
            
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                Long length = entity.getContentLength();
                InputStream input = entity.getContent();
                byte[] buffer = new byte[4096];
                int size  = 0;
                int total = 0;
                while (true) {
                    if (mJob.cancelled) return false;
                    try {
                        size = input.read(buffer);
                    } catch (SocketTimeoutException e) {
                        synchronized (mLock) {
                            //Sometimes our content length is somewhat off.  We want to report success
                            //in this case
                            if ((length - total) < 10000) {
                                Logger.log("Socket timed out close to the end.  Trying to handle");
                                mJob.track.mProgress.run(total, total);
                                mReturn = true;
                                return false;
                            }
                            Logger.log("Socket timed out in the middle of file. Got: " + total + " bytes. Expected: " + length + " bytes");
                            mReturn = false;
                            return false;   
                        }
                    }
                    if (size == -1) break;
                    mJob.stream.write(buffer, 0, size);
                    total += size;
                    mJob.track.mProgress.run(total, length);
                }
                return true;
            } else {
                return false;
            }
        }

        public HttpRequestRetryHandler myRetryHandler = new HttpRequestRetryHandler() {
            HttpRequestRetryHandler mFallthrough = new DefaultHttpRequestRetryHandler();
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context)
            {
                if (exception instanceof SocketException) {
                    Logger.log("Got SocketException delegating response back to the handler.");
                    while (true) {
                        synchronized (OutputStreamResponseHandler.this.mLock) {
                            if (OutputStreamResponseHandler.this.mReturn != null) break;
                        }
                    }
                    return true;    
                }
                return mFallthrough.retryRequest(exception, executionCount, context);
            }
        };
    }
    
    public class AlreadyDownloadedException extends Exception
    {
        private String mPath;
        private static final long serialVersionUID = -7663492585836784742L;
        public AlreadyDownloadedException(String path)
        {
            mPath = path;
        }
        public String getPath()
        {
            return mPath;
        }
    }
    public void clear()
    {
        mJob.cancelled = true;
        mQueue.clear();
    }
    
    private void freeCacheSpace()
    {
        StorageInfo info = new StorageInfo(mContext);
        while (info.needCacheSpace()) {
            freeSomeCacheSpace();
        }
    }
    
    private void freeSomeCacheSpace()
    {
        File file = getOldestFile(Music.getMP3tunesCacheDir());
        if (file == null) {
            Logger.log("Empty cache dir. Nothing to delete");
            return;
        }
        Logger.log("deleting: " + file.getAbsolutePath());
        file.delete();
    }
    
    private File getOldestFile(String dir) 
    {
        File fl = new File(dir);
        File[] files = fl.listFiles(new FileFilter() {                  
                public boolean accept(File file) {
                        return file.isFile();
                }
        });
        long lastMod = Long.MAX_VALUE;
        File choice = null;
        for (File file : files) {
                if (file.lastModified() < lastMod) {
                        choice = file;
                        lastMod = file.lastModified();
                }
        }
        return choice;
    }
}