/*
 * Copyright (C) 2014 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import com.amaze.filemanager.ProgressListener;
import com.amaze.filemanager.R;
import com.amaze.filemanager.RegisterCallback;
import com.amaze.filemanager.activities.MainActivity;
import com.amaze.filemanager.utils.DataPackage;
import com.amaze.filemanager.utils.Futils;
import com.amaze.filemanager.utils.HFile;
import com.stericson.RootTools.RootTools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CopyService extends Service {
    HashMap<Integer, Boolean> hash = new HashMap<Integer, Boolean>();
    public HashMap<Integer, DataPackage> hash1 = new HashMap<Integer, DataPackage>();
    Notification notification;
    boolean rootmode;
    NotificationManager mNotifyManager;
    NotificationCompat.Builder mBuilder;
    Context c;
    @Override
    public void onCreate() {
        c = getApplicationContext();
        SharedPreferences Sp=PreferenceManager.getDefaultSharedPreferences(this);
        rootmode=Sp.getBoolean("rootmode",false);
        registerReceiver(receiver3, new IntentFilter("copycancel"));
    }


    boolean foreground=true;
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle b = new Bundle();
        ArrayList<String> files = intent.getStringArrayListExtra("FILE_PATHS");
        ArrayList<String> names=intent.getStringArrayListExtra("FILE_NAMES");
        String FILE2 = intent.getStringExtra("COPY_DIRECTORY");
        mNotifyManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        b.putInt("id", startId);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notificationIntent.putExtra("openprocesses",true);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        mBuilder = new NotificationCompat.Builder(c);
        mBuilder.setContentIntent(pendingIntent);
        mBuilder.setContentTitle(getResources().getString(R.string.copying))

                .setSmallIcon(R.drawable.ic_content_copy_white_36dp);
        if(foreground){
            startForeground(Integer.parseInt("456"+startId),mBuilder.build());
        foreground=false;
        }
        b.putBoolean("move", intent.getBooleanExtra("move", false));
        b.putString("FILE2", FILE2);
        b.putStringArrayList("names",names);
        b.putStringArrayList("files", files);
        hash.put(startId, true);
        DataPackage intent1 = new DataPackage();
        intent1.setName(files.get(0));
        intent1.setTotal(0);
        intent1.setDone(0);
        intent1.setId(startId);
        intent1.setP1(0);
        intent1.setP2(0);
        intent1.setMove(intent.getBooleanExtra("move", false));
        intent1.setCompleted(false);
        hash1.put(startId,intent1);
        new Doback().execute(b);

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }
    // Binder given to clients

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */



    ProgressListener progressListener;


    public void onDestroy() {
        this.unregisterReceiver(receiver3);
    }

    public class Doback extends AsyncTask<Bundle, Void, Integer> {
        ArrayList<String> files,names;
        boolean move;
        public Doback() {
        }

        protected Integer doInBackground(Bundle... p1) {
            String FILE2 = p1[0].getString("FILE2");
            int id = p1[0].getInt("id");
            files = p1[0].getStringArrayList("files");
            names=p1[0].getStringArrayList("names");
            move=p1[0].getBoolean("move");
            new copy().execute(id, files,names, FILE2,move);

            // TODO: Implement this method
            return id;
        }

        @Override
        public void onPostExecute(Integer b) {
            publishResults("", 0, 0, b, 0, 0, true, move);
            hash.put(b,false);
            boolean stop=true;
            for(int a:hash.keySet()){
            if(hash.get(a))stop=false;
            }
            if(stop)
            stopSelf(b);

        }

    }

    private void publishResults(String a, int p1, int p2, int id, long total, long done, boolean b, boolean move) {
        if (hash.get(id)) {

            mBuilder.setProgress(100, p1, false);
            mBuilder.setOngoing(true);
            int title = R.string.copying;
            if (move) title = R.string.moving;
            mBuilder.setContentTitle(utils.getString(c, title));
            mBuilder.setContentText(new File(a).getName() + " " + utils.readableFileSize(done) + "/" + utils.readableFileSize(total));
            int id1 = Integer.parseInt("456" + id);
            mNotifyManager.notify(id1, mBuilder.build());
            if (p1 == 100 || total == 0) {
                mBuilder.setContentTitle("Copy completed");
                if (move)
                    mBuilder.setContentTitle("Move Completed");
                mBuilder.setContentText("");
                mBuilder.setProgress(0, 0, false);
                mBuilder.setOngoing(false);
                mBuilder.setAutoCancel(true);
                mNotifyManager.notify(id1, mBuilder.build());
                publishCompletedResult(id, id1);
            }
                DataPackage intent = new DataPackage();
                intent.setName(a);
                intent.setTotal(total);
                intent.setDone(done);
                intent.setId(id);
                intent.setP1(p1);
                intent.setP2(p2);
                intent.setMove(move);
                intent.setCompleted(b);
                hash1.put(id,intent);
            try {
                if(progressListener!=null){
                    progressListener.onUpdate(intent);
                    if(b)progressListener.refresh();
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else publishCompletedResult(id, Integer.parseInt("456" + id));
    }
    public void publishCompletedResult(int id,int id1){
        try {
            mNotifyManager.cancel(id1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    class copy {
        public copy() {
        }

        long getTotalBytes(ArrayList<String> files, boolean smb) {
            long totalBytes = 0l;

            try {
                for (int i = 0; i < files.size(); i++) {
                    HFile f1 = new HFile(files.get(i));
                    if (f1.isDirectory()) {
                        totalBytes = totalBytes + f1.folderSize();
                    } else {
                        totalBytes = totalBytes + f1.length();
                    }
                }
            } catch (Exception e) {
            }

            return totalBytes;
        }
        long totalBytes = 0L, copiedBytes = 0L;
        int lastpercent=0;

        public void execute(int id, final ArrayList<String> files,ArrayList<String> names, final String FILE2, final boolean move) {
            if (utils.checkFolder((FILE2), c) == 1) {
                totalBytes = getTotalBytes(files, false);
                for (int i = 0; i < files.size(); i++) {
                    HFile f1 = new HFile(files.get(i));

                    try {

                        if (hash.get(id))
                            copyFiles((f1), new HFile(FILE2, names.get(i),f1.isDirectory()), id, move);
                        else
                            stopSelf(id);
                    } catch (Exception e) {
                        System.out.println("amaze " + e);
                        publishResults("" + e, 0, 0, id, 0, 0, false, move);
                        stopSelf(id);
                    }


                }
                if (move) {
                    boolean b = hash.get(id);
                    if (b)
                        for (String a : files) {
                            new HFile(a).delete(c);
                        }
                }
                Intent intent = new Intent("loadlist");
                sendBroadcast(intent);
            } else if (rootmode) {
                boolean m = true;
                for (int i = 0; i < files.size(); i++) {
                    boolean b = RootTools.copyFile(getCommandLineString(files.get(i)), getCommandLineString(FILE2), true, true);
                    if (!b && files.get(i).contains("/0/"))
                        b = RootTools.copyFile(getCommandLineString(files.get(i).replace("/0/", "/legacy/")), getCommandLineString(FILE2)+"/"+names.get(i), true, true);
                    if (!b) m = false;
                    utils.scanFile(FILE2 + "/" + names.get(i), c);
                }
                if (move && m) {
                    new DeleteTask(getContentResolver(), c).execute((files));
                }

                Intent intent = new Intent("loadlist");
                sendBroadcast(intent);

            } else {
                System.out.println("Not Allowed");
            }
        }
        private static final String UNIX_ESCAPE_EXPRESSION = "(\\(|\\)|\\[|\\]|\\s|\'|\"|`|\\{|\\}|&|\\\\|\\?)";

        private  String getCommandLineString(String input) {
            return input.replaceAll(UNIX_ESCAPE_EXPRESSION, "\\\\$1");
        }

        private void copyFiles(HFile sourceFile, HFile targetFile, int id,boolean move) throws IOException {
            if (sourceFile.isDirectory()) {
                if (!targetFile.exists()) targetFile.mkdir(c);
                /*try {
                    targetFile.setLastModified(sourceFile.lastModified());
                } catch (Exception e) {
                    e.printStackTrace();
                }*/
                ArrayList<String[]> filePaths = sourceFile.listFiles(false);
                for (String[] filePath : filePaths) {
                    HFile file=new HFile((filePath[0]));
                    HFile destFile = new HFile(targetFile.getPath(), file.getName(),file.isDirectory());
                    copyFiles(file, destFile, id, move);
                }
            } else {
                long size = sourceFile.length();
                InputStream in = sourceFile.getInputStream();
                OutputStream out= targetFile.getOutputStream(c);
                copy(in, out, size, id, sourceFile.getName(), move);
                /*
                        try {
                            targetFile.setLastModified(sourceFile.lastModified());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }*/
                        if(!targetFile.isSmb())
                        utils.scanFile(targetFile.getPath(), c);
            }}
        long time=System.nanoTime()/500000000;
        AsyncTask asyncTask;
        void calculateProgress(final String name,final long fileBytes,final int id,final long
                size,final boolean move){
            if(asyncTask!=null && asyncTask.getStatus() == AsyncTask.Status.RUNNING)asyncTask.cancel(true);
            asyncTask=new AsyncTask<Void,Void,Void>(){
                int p1,p2;
                @Override
                protected Void doInBackground(Void... voids) {
                    p1 = (int) ((copiedBytes / (float) totalBytes) * 100);
                    p2=(int) ((fileBytes / (float) size) * 100);
                    lastpercent = (int)copiedBytes;
                    return null;
                }@Override
            public void onPostExecute(Void v){
                    publishResults(name, p1,p2 , id, totalBytes, copiedBytes, false, move);
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        void copy(InputStream stream,OutputStream outputStream,long size,int id,String name,boolean move) throws IOException {
            long  fileBytes = 0l;
            // txtDetails.append("Copying " + sourceFile.getAbsolutePath() + " ... ");
            BufferedInputStream in = new BufferedInputStream(stream);
            BufferedOutputStream out=new BufferedOutputStream(outputStream);
            if (outputStream == null)return;
                byte[] buffer = new byte[1024*60];

                int length;
                //copy the file content in bytes
                while ((length = in.read(buffer)) > 0) {
                    boolean b = hash.get(id);
                    if (b) {
                        out.write(buffer, 0, length);
                        copiedBytes += length;
                        fileBytes += length;
                        long time1=System.nanoTime()/500000000;
                        if(((int)time1)>((int)(time))){
                            calculateProgress(name,fileBytes,id,size,move);
                            time=System.nanoTime()/500000000;
                        }

                    } else {
                        publishCompletedResult(id, Integer.parseInt("456" + id));
                        in.close();
                        out.close();
                        stopSelf(id);
                        return;
                    }}
            in.close();
            out.close();
            stream.close();
            outputStream.close();
        }
           }

    Futils utils = new Futils();
    private BroadcastReceiver receiver3 = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            hash.put(intent.getIntExtra("id", 1), false);

        }
    };
    RegisterCallback registerCallback= new RegisterCallback.Stub() {
        @Override
        public void registerCallBack(ProgressListener p) throws RemoteException {
            progressListener=p;
        }

        @Override
        public List<DataPackage> getCurrent() throws RemoteException {
            List<DataPackage> dataPackages=new ArrayList<>();
            for (int i : hash1.keySet()) {
               dataPackages.add(hash1.get(i));
            }
            return dataPackages;
        }
    };
    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        return registerCallback.asBinder();
    }
}
