/***************************************************************************
*   Copyright (C) 2009  Casey Link <unnamedrambler@gmail.com>             *
*   Copyright (C) 2007-2008 sibyl project http://code.google.com/p/sibyl/ *
*                                                                         *
*   This program is free software; you can redistribute it and/or modify  *
*   it under the terms of the GNU General Public License as published by  *
*   the Free Software Foundation; either version 3 of the License, or     *
*   (at your option) any later version.                                   *
*                                                                         *
*   This program is distributed in the hope that it will be useful,       *
*   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*   GNU General Public License for more details.                          *
*                                                                         *
*   You should have received a copy of the GNU General Public License     *
*   along with this program; if not, write to the                         *
*   Free Software Foundation, Inc.,                                       *
*   51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.         *
***************************************************************************/
package com.mp3tunes.android.player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Formatter;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.LockerContext;
import com.binaryelysium.mp3tunes.api.LockerData;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.activity.Login;
import com.mp3tunes.android.player.content.DbKeys;
import com.mp3tunes.android.player.content.DbTables;
import com.mp3tunes.android.player.content.LockerDb;
import com.mp3tunes.android.player.service.PlaybackService;
import com.mp3tunes.android.player.service.IPlaybackService;
import com.mp3tunes.android.player.util.RefreshSessionTask;

/**
 * Simple static types and enums.
 *
 */
public class Music
{

    public static enum Meta
    {
        TRACK, ARTIST, ALBUM, PLAYLIST, CURRENT_PLAYLIST, RADIO;
    }
    
    public static final String DOWNLOADED_TRACKS_ID   = "-1";
    public static final String DOWNLOADED_TRACKS_NAME = "Local music";
    
    public static String[] ID = {DbKeys.ID};
    
    public static String[] TOKEN = {DbKeys.TYPE, 
    								DbKeys.TOKEN, 
    								DbKeys.COUNT};
    
    public static String[] ARTIST = {DbKeys.ID, 
    								 DbKeys.ARTIST_NAME, 
    								 DbKeys.ALBUM_COUNT, 
    								 DbKeys.TRACK_COUNT};
    
    public static String[] PLAYLIST = {DbKeys.ID, 
    								   DbKeys.PLAYLIST_NAME, 
    								   DbKeys.FILE_COUNT, 
    								   DbKeys.FILE_NAME, 
    								   DbKeys.PLAYLIST_ORDER};
    
    public static String[] ALBUM  = {DbKeys.ID, 
    								 DbKeys.ALBUM_NAME, 
    								 DbKeys.ARTIST_ID, 
    								 DbKeys.ARTIST_NAME, 
    								 DbKeys.TRACK_COUNT, 
    								 DbKeys.COVER_URL};
    
    public static String[] TRACK = {DbKeys.ID, 
        							DbKeys.TITLE, 
        							DbKeys.ARTIST_NAME, 
        							DbKeys.ARTIST_ID, 
        							DbKeys.ALBUM_NAME, 
        							DbKeys.ALBUM_ID,
        							DbKeys.TRACK, 
        							DbKeys.PLAY_URL, 
        							DbKeys.DOWNLOAD_URL, 
        							DbKeys.TRACK_LENGTH, 
        							DbKeys.COVER_URL,
        							DbKeys.ORDINAL};
    
    public static String[] TRACK_FOR_BROWSER = {
        DbKeys.ID, 
        DbKeys.TITLE, 
        DbKeys.ARTIST_NAME, 
        DbKeys.ALBUM_NAME,  
        DbKeys.TRACK_LENGTH
    };
    

    public static String[] TRACKP = {DbTables.TRACK + "." + DbKeys.ID, 
		 							 DbKeys.TITLE,  
		 							 DbKeys.ARTIST_NAME, 
		 							 DbKeys.ARTIST_ID, 
		 							 DbKeys.ALBUM_NAME, 
		 							 DbKeys.ALBUM_ID,
		 							 DbKeys.TRACK, 
		 							 DbKeys.PLAY_URL, 
		 							 DbKeys.DOWNLOAD_URL, 
		 							 DbKeys.TRACK_LENGTH, 
		 							 DbKeys.COVER_URL};

  //This mapping corresponds to the field indexes above
    public static final class PLAYLIST_MAPPING 
    {
        public static final int ID = 0;
        public static final int PLAYLIST_NAME = 1;
        public static final int FILE_COUNT = 2;
        public static final int FILE_NAME = 3;
        public static final int PLAYLIST_ORDER = 4;
    }
    
    //This mapping corresponds to the field indexes above
    public static final class ARTIST_MAPPING 
    {
        public static final int ID = 0;
        public static final int ARTIST_NAME = 1;
        public static final int ALBUM_COUNT = 2;
        public static final int TRACK_COUNT = 3;
    }
    
    //This mapping corresponds to the field indexes above
    public static final class ALBUM_MAPPING 
    {
        public static final int ID = 0;
        public static final int ALBUM_NAME = 1;
        public static final int ARTIST_ID = 2;
        public static final int ARTIST_NAME = 3;
        public static final int TRACK_COUNT = 4;
        public static final int COVER_URL = 5;
    }
    
    public static int[] TRACKP_MAPPING = { 0,1,2,3,4,5,6,7,8,9,10};
    
    
    public static final class TRACK_FOR_BROWSER_MAPPING 
    {
        public static final int ID = 0;
        public static final int TITLE = 1;
        public static final int ARTIST_NAME = 2;
        public static final int ALBUM_NAME = 3;
        public static final int TRACK_LENGTH = 4;
    }

    //This mapping corresponds to the field indexes above
    public static final class TRACK_MAPPING 
    {
        public static final int ID = 0;
        public static final int TITLE = 1;
        public static final int ARTIST_NAME = 2;
        public static final int ARTIST_ID = 3;
        public static final int ALBUM_NAME = 4;
        public static final int ALBUM_ID = 5;
        public static final int TRACKNUM = 6;
        public static final int PLAY_URL = 7;
        public static final int DOWNLOAD_URL = 8;
        public static final int TRACK_LENGTH = 9;
        public static final int COVER_URL = 10;
    }
    
    
    
    public static final class RepeatMode {
        // each song will be played once
        public static final int NO_REPEAT = 0; 
        // the current song will be repeated while repeatmode is REPEAT_SONG
        public static final int REPEAT_SONG = 1;  
        // the current playlist will be repeated when finished
        public static final int REPEAT_PLAYLIST = 2;   
    }
    
    public static final class ShuffleMode {
        // songs are played in the order of the playlist
        public static final int NORMAL = 0;
        // tracks are played randomly
        public static final int TRACKS = 1;
        // artists are played randomly
        public static final int ARTISTS = 2;
        // albums are played randomly
        public static final int ALBUMS = 3;
        public static int getText(int mode){
            switch(mode){
                case TRACKS: return R.string.shuffle_tracks;
                case ARTISTS: return R.string.shuffle_artists;
                case ALBUMS: return R.string.shuffle_albums;
                default : return R.string.shuffle_none;
            }
        }
    }
    
    
    private static LockerDb        sDb = null;
    private static ArrayList<Context> sDbConnectionMap = new ArrayList<Context>();

    //This function checks for a session.  If we do not have a session
    //it will try to get one from the stored username and password
    //if it cannot then it will take you to the login page.
    public static void ensureSession(Activity a)
    {
        LockerContext context = LockerContext.instance();
        if (context.haveSession()) return;
        RefreshSessionTask task = new RefreshSessionTask(a);
        if (task.doInForground()) {
            Intent intent = new Intent(a, Login.class);
            a.startActivity(intent);
            a.finish();
        }
    }
    
    static public void clearData(Context context)
    {
        ContextWrapper cw       = new ContextWrapper(context);
        SharedPreferences settings = cw.getSharedPreferences(Login.PREFS, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("auto_login", false);
        editor.commit();
        Music.getDb(context).clearDB();
        try {
            Music.sService.stop();
        } catch (RemoteException e) {
        } catch (Exception e) {
        }
    }
    
    
    public static boolean unconnectFromDb( Context context )
    {
        boolean res = sDbConnectionMap.remove( context );
        if( sDbConnectionMap.isEmpty() )
        {
            if (sDb != null) {
                sDb.close();
                sDb = null;
            }
        }
        return res;
    }
    
    public static IPlaybackService sService = null;
    private static HashMap<Context, ServiceBinder> sConnectionMap = new HashMap<Context, ServiceBinder>();
    
    public static boolean bindToService(Context context) {
        return bindToService(context, null);
    }

    public static boolean bindToService(Context context, ServiceConnection callback) {
        context.startService(new Intent(context, PlaybackService.class));
        ServiceBinder sb = new ServiceBinder(callback);
        sConnectionMap.put(context, sb);
        return context.bindService((new Intent()).setClass(context,
                PlaybackService.class), sb, 0);
    }
    
    public static void unbindFromService(Context context) {
        ServiceBinder sb = (ServiceBinder) sConnectionMap.remove(context);
        if (sb == null) {
            Log.e("MusicUtils", "Trying to unbind for unknown Context");
            return;
        }
        context.unbindService(sb);
        if (sConnectionMap.isEmpty()) {
            // presumably there is nobody interested in the service at this point,
            // so don't hang on to the ServiceConnection
            sService = null;
        }
    }

    private static class ServiceBinder implements ServiceConnection {
        ServiceConnection mCallback;
        ServiceBinder(ServiceConnection callback) {
            mCallback = callback;
        }
        
        public void onServiceConnected(ComponentName className, android.os.IBinder service) {
            sService = IPlaybackService.Stub.asInterface(service);
//            initAlbumArtCache();
            if (mCallback != null) {
                mCallback.onServiceConnected(className, service);
            }
        }
        
        public void onServiceDisconnected(ComponentName className) {
            if (mCallback != null) {
                mCallback.onServiceDisconnected(className);
            }
            sService = null;
        }
    }
    
    
    /*  Try to use String.format() as little as possible, because it creates a
     *  new Formatter every time you call it, which is very inefficient.
     *  Reusing an existing Formatter more than tripled the speed of
     *  makeTimeString().
     *  This Formatter/StringBuilder are also used by makeAlbumSongsLabel()
     */
    private static StringBuilder  sFormatBuilder = new StringBuilder();
    private static Formatter      sFormatter     = new Formatter(sFormatBuilder, Locale.getDefault());
    private static final Object[] sTimeArgs      = new Object[5];

    public static String makeTimeString(Context context, long secs) {
        String durationformat = context.getString(R.string.durationformat);
        
        /* Provide multiple arguments so the format can be changed easily
         * by modifying the xml.
         */
        sFormatBuilder.setLength(0);

        final Object[] timeArgs = sTimeArgs;
        timeArgs[0] = secs / 3600;
        timeArgs[1] = secs / 60;
        timeArgs[2] = (secs / 60) % 60;
        timeArgs[3] = secs;
        timeArgs[4] = secs % 60;

        return sFormatter.format(durationformat, timeArgs).toString();
    }
    
    public interface Defs {
        public final static int OPEN_URL          = 0;
        public final static int ADD_TO_PLAYLIST   = 1;
        public final static int USE_AS_RINGTONE   = 2;
        public final static int PLAYLIST_SELECTED = 3;
        public final static int NEW_PLAYLIST      = 4;
        public final static int PLAY_SELECTION    = 5;
        public final static int GOTO_START        = 6;
        public final static int GOTO_PLAYBACK     = 7;
        public final static int PARTY_SHUFFLE     = 8;
        public final static int SHUFFLE_ALL       = 9;
        public final static int DELETE_ITEM       = 10;
        public final static int SCAN_DONE         = 11;
        public final static int QUEUE             = 12;
        public final static int CHILD_MENU_BASE   = 13; // this should be the last item
        public final static int SHOW_PLAYLIST     = 14;
    }
    
    public static void setSpinnerState(Activity a, boolean visible) {
        if( visible ) {
            // start the progress spinner
            a.getWindow().setFeatureInt(
                    Window.FEATURE_INDETERMINATE_PROGRESS,
                    Window.PROGRESS_INDETERMINATE_ON);

            a.getWindow().setFeatureInt(
                    Window.FEATURE_INDETERMINATE_PROGRESS,
                    Window.PROGRESS_VISIBILITY_ON);
        } else {
            // stop the progress spinner
            a.getWindow().setFeatureInt(
                    Window.FEATURE_INDETERMINATE_PROGRESS,
                    Window.PROGRESS_VISIBILITY_OFF);
        }
    }
    
    public static String makeAlbumsLabel(Context context, int numalbums, int numsongs, boolean isUnknown) {
        // There are two formats for the albums/songs information:
        // "N Song(s)"  - used for unknown artist/album
        // "N Album(s)" - used for known albums
        
        StringBuilder songs_albums = new StringBuilder();

        Resources r = context.getResources();
        if (isUnknown) {
            if (numsongs == 1) {
                songs_albums.append(context.getString(R.string.onesong));
            } else {
                String f = r.getQuantityText(R.plurals.Nsongs, numsongs).toString();
                sFormatBuilder.setLength(0);
                sFormatter.format(f, Integer.valueOf(numsongs));
                songs_albums.append(sFormatBuilder);
            }
        } else {
            String f = r.getQuantityText(R.plurals.Nalbums, numalbums).toString();
            sFormatBuilder.setLength(0);
            sFormatter.format(f, Integer.valueOf(numalbums));
            songs_albums.append(sFormatBuilder);
            songs_albums.append(context.getString(R.string.albumsongseparator));
        }
        return songs_albums.toString();
    }
    
    /*
     * Returns true if a track is currently opened for playback (regardless
     * of whether it's playing or paused).
     */
    public static boolean isMusicPlaying() {
        if (sService != null) {
            try {
                return sService.isPlaying();
            } catch (RemoteException ex) {
            }
        }
        return false;
    }
    
    public static Id getCurrentTrackId() {
        if (sService != null) {
            try {
                return sService.getTrack().getId();
            } catch (RemoteException ex) {
            }
        }
        return null;
    }
    
    public static void playAll(Context context, Id[] list, int position )
    {
        playAll(context, list, position, false);   
    }
    
    private static void playAll(Context context, Id[] list, int position, boolean force_shuffle) {
        if (sService == null) {
            Toast.makeText(context, "Music service died", Toast.LENGTH_SHORT).show();
            return;
        }
        if (list.length == 0) {
            Log.d("MusicUtils", "attempt to play empty song list");
            // Don't try to play empty playlists. Nothing good will come of it.
            String message = context.getString(R.string.emptyplaylist, list.length);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (position < 0) {
                position = 0;
            }
            sService.createPlaybackList(idArrayToIdParcelArray(list));
            sService.startAt(position); 
        } catch (RemoteException ex) {
        } finally {
            Intent intent = new Intent("com.mp3tunes.android.player.PLAYER")
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        }
    }
    
    public static IdParcel[] idArrayToIdParcelArray(Id[] ids)
    {
        IdParcel[] output = new IdParcel[ids.length];
        for (int i = 0; i < ids.length; i++) {
            output[i] = new IdParcel(ids[i]);
        }
        return output;
    }
    
    public static Id[] lockerDataToIdArray(LockerData[] data)
    {
        Id[] output = new Id[data.length];
        for (int i = 0; i < data.length; i++) {
            output[i] = data[i].getId();
        }
        return output;
    }
    
    private static final HashMap<Integer, Drawable> sArtCache = new HashMap<Integer, Drawable>();
    private static final BitmapFactory.Options sBitmapOptionsCache = new BitmapFactory.Options();
    private static final BitmapFactory.Options sBitmapOptions = new BitmapFactory.Options();
    
    static
    {
        // for the cache, 
        // 565 is faster to decode and display
        // and we don't want to dither here because the image will be scaled down later
        sBitmapOptionsCache.inPreferredConfig = Bitmap.Config.RGB_565;
        sBitmapOptionsCache.inDither = false;

        sBitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        sBitmapOptions.inDither = false;
    }
    
    public static void initAlbumArtCache() 
    {
        clearAlbumArtCache();
    }

    public static void clearAlbumArtCache() 
    {
        synchronized( sArtCache ) {
            sArtCache.clear();
        }
    }
    
    public static Drawable getCachedArtwork(Context context, int artIndex, BitmapDrawable defaultArtwork) 
    {
        Drawable d = null;
        synchronized(sArtCache) 
        {
            d = sArtCache.get(artIndex);
        }
        if (d == null) {
            d = defaultArtwork;
            final Bitmap icon = defaultArtwork.getBitmap();
            int w = icon.getWidth();
            int h = icon.getHeight();
            Bitmap b = Music.getArtworkQuick(context, artIndex, w, h);
            if (b != null) {
                d = new FastBitmapDrawable(b);
                synchronized(sArtCache) {
                    // the cache may have changed since we checked
                    Drawable value = sArtCache.get(artIndex);
                    if (value == null) {
                        sArtCache.put(artIndex, d);
                    } else {
                        d = value;
                    }
                }
            }     
            
        } 
        return d;
    }
    
    public static Bitmap getArtworkQuick(Context context, int album_id, int w, int h)
    {
        // NOTE: There is in fact a 1 pixel border on the right side in the ImageView
        // used to display this drawable. Take it into account now, so we don't have to
        // scale later.
        w -= 1;
        boolean cacheEnabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean( "cacheart", false );
        Bitmap ret = null;
        try {
            String cacheDir = Environment.getExternalStorageDirectory() + "/mp3tunes/art/";
            String ext = ".jpg";
            if( cacheEnabled )
            {
                File f = new File( cacheDir + album_id + ext);
                
                if( f.exists() && f.canRead() )
                {
                    int sampleSize = 1;
                    // Compute the closest power-of-two scale factor 
                    // and pass that to sBitmapOptionsCache.inSampleSize, which will
                    // result in faster decoding and better quality
                    sBitmapOptionsCache.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile( f.getAbsolutePath(), sBitmapOptionsCache );
                    int nextWidth = sBitmapOptionsCache.outWidth >> 1;
                    int nextHeight = sBitmapOptionsCache.outHeight >> 1;
                    while (nextWidth>w && nextHeight>h) {
                        sampleSize <<= 1;
                        nextWidth >>= 1;
                        nextHeight >>= 1;
                    }
                    sBitmapOptionsCache.inSampleSize = sampleSize;
                    sBitmapOptionsCache.inJustDecodeBounds = false;
                    ret = BitmapFactory.decodeFile( f.getAbsolutePath(), sBitmapOptionsCache );
                }
            }
            if (ret != null) {
                // finally rescale to exactly the size we need
                if (sBitmapOptionsCache.outWidth != w || sBitmapOptionsCache.outHeight != h) {
                    Bitmap tmp = Bitmap.createScaledBitmap(ret, w, h, true);
                    // Bitmap.createScaledBitmap() can return the same bitmap
                    if (tmp != ret) ret.recycle();
                    ret = tmp;
                }
                
            }
        } catch(Exception e) {}
        return ret;
    }
    
 // A really simple BitmapDrawable-like class, that doesn't do
    // scaling, dithering or filtering.
    private static class FastBitmapDrawable extends Drawable {
        private Bitmap mBitmap;
        public FastBitmapDrawable(Bitmap b) {
            mBitmap = b;
        }
        @Override
        public void draw(Canvas canvas) {
            canvas.drawBitmap(mBitmap, 0, 0, null);
        }
        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
        @Override
        public void setAlpha(int alpha) {
        }
        @Override
        public void setColorFilter(ColorFilter cf) {
        }
    }

    public static LockerDb getDb(Context context)
    {
        if (sDb == null) {
            try {
                sDb = new LockerDb(context.getApplicationContext());
            } catch ( Exception e) {
                Log.e("Mp3tunes", Log.getStackTraceString(e));
            }
        }
        return sDb;
    }
    
    static public String getMP3tunesStorageRoot()
    {
        return makeDirInExternalStorage("mp3tunes");
    }
    
    static public String getMP3tunesMusicDir()
    {
        return makeDirInExternalStorage("mp3tunes/music");
    }
    
    static public String getMP3tunesCacheDir()
    {
        return makeDirInExternalStorage("mp3tunes/cache");
    }
    
    static private String makeDirInExternalStorage(String path)
    {
        File storageDir = Environment.getExternalStorageDirectory();
        if (storageDir.isDirectory()) {
            File mp3tunesDir = new File(storageDir, path);
            if (mp3tunesDir.isDirectory()) {
                return mp3tunesDir.getAbsolutePath();
            } else {
                Log.w("Mp3Tunes", "making: " + mp3tunesDir.getAbsolutePath());
                if (mp3tunesDir.mkdirs()) {
                    return mp3tunesDir.getAbsolutePath();
                }
                Log.w("Mp3Tunes", "making: " + mp3tunesDir.getAbsolutePath() + " failed");
            }
        }
        Log.w("Mp3Tunes", "Make Mp3Tunes directory failed");
        return null;
    }

    //TODO: add a setting for max cache size
    public static long getMaxCacheSize(Context context)
    {
        long size = -1;
        try {
            size = Long.valueOf(PreferenceManager
                .getDefaultSharedPreferences(context).getString("cache", "-1"));
            return size;
        } catch (Exception e) {}
        return size;
    }

    public static long getMinFreeStorageSize()
    {
        //for now make sure that the user has at least 50mb free
        return 52428800;
    }
}
