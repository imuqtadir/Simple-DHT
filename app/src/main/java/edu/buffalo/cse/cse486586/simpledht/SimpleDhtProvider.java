package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

public class SimpleDhtProvider extends ContentProvider {

    public static String myPort=""; //Sender's Port
    public static String portStr=""; // myAVD name
    public static String predecessor; // my predecessor
    public static String successor; // my successor
    public static Cursor globalCursor;
    public static Cursor globalAccumulatedCursor;
    int TotalCountOfNodesInRing = 0;
    public static boolean waitForAccumulation=false;
    public static boolean waitForSingle =false;
   public static boolean waitForDelete = false;

    static final String TAG = SimpleDhtProvider.class.getSimpleName();

    static final int SERVER_PORT = 10000;

    static final String PROVIDER_NAME = "edu.buffalo.cse.cse486586.simpledht.provider";

    static final String URL = "content://" + PROVIDER_NAME + "/SimpleDhtActivity";

    static final Uri CONTENT_URI = Uri.parse(URL);
    Uri message_uri = Uri.parse("content://edu.buffalo.cse.cse486586.simpledht.provider");

    //Database columns
    static final String ID = "key";

    static final String MESSAGE = "value";

    public DBHelper db1;


    static final String DATABASE_NAME = "DHTDB";

    static final String TABLE_NAME = "message_tbl";

    static final int DATABASE_VERSION = 1;


    static final String CREATE_TABLE = "CREATE TABLE message_tbl (" + "key" + " TEXT, " + "value" + " TEXT NOT NULL)";

    //ContentProvider class starts here
    public static class DBHelper extends SQLiteOpenHelper {


        public DBHelper(Context context) {
            // TODO Auto-generated constructor stub
            super(context, DATABASE_NAME, null, DATABASE_VERSION);

        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            // TODO Auto-generated method stub

            db.execSQL(CREATE_TABLE);
        }


        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(DBHelper.class.getName(),
                    "Upgrading database from version " + oldVersion + " to " + newVersion + ". Old data will be destroyed");
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }
    }//ContentProvider Class ends here

//delete starts here
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {

        // TODO Auto-generated method stub
        SQLiteDatabase db = db1.getWritableDatabase();



        Log.e(TAG, "Inside Query where selection is" + selection);

        if (selection.equals("\"@\"")) {
            Log.e(TAG, "Delete Selection is matched to @");
            db.execSQL("delete from "+ TABLE_NAME);

            return 0;
        }
        //We need to check for * now
        if (selection.equals("\"*\"")) {

           // db.rawQuery("SELECT * FROM message_tbl;", null);
            db.execSQL("delete from "+ TABLE_NAME);

            deleteAllRecordInit(myPort, successor);
            Log.e(TAG, "Deleted all records across avds:");


            return 0;

        }
        else{
            Log.e(TAG,"Deleting specific record");
            db.delete(TABLE_NAME, "key=?",new String[] { selection.toString()});
        }

        return 0;
    }//Ends:Delete
    public int deleteAllRecordInit(String deleteInitiator, String mysuccessor){
        //
        Message queryMsgToSuccessor = new Message();
        queryMsgToSuccessor.successor = mysuccessor;
        queryMsgToSuccessor.sendPort = Integer.toString(Integer.valueOf(mysuccessor)*2);

        queryMsgToSuccessor.type = 7;
        queryMsgToSuccessor.queryStartPort = deleteInitiator;
        Log.e(TAG,"In deleteAllRecordInit, forwarding request to: "+queryMsgToSuccessor.sendPort+"Currently inside:"+myPort);
        Log.e(TAG,"Query Initiator is"+queryMsgToSuccessor.queryStartPort);

        new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryMsgToSuccessor);
        waitForDelete =true;
        while(waitForDelete){
            // Log.e(TAG,"In while for waitForAccumulation");


        }
        Log.e(TAG,"Must have got deleted all");

        return 0;

    }
    public void deleteAllRecordNotInit(String deleteInitiator, String mysuccessor){

        if(deleteInitiator.equals(myPort)){

            waitForDelete =false;
        }
        else {
            SQLiteDatabase db = db1.getWritableDatabase();

            db.execSQL("delete from "+ TABLE_NAME);


            Message deleteMsgToSuccessor =new Message();

            deleteMsgToSuccessor.sendPort = Integer.toString(Integer.valueOf(mysuccessor) * 2);

            deleteMsgToSuccessor.type = 7;
            deleteMsgToSuccessor.queryStartPort = deleteInitiator;


            Log.e(TAG, "Deleted my records, forwarding request to:" + deleteMsgToSuccessor.sendPort + "Currently inside:" + myPort);
            Log.e(TAG, "Delete Initiator is" + deleteMsgToSuccessor.queryStartPort);
            new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, deleteMsgToSuccessor);

        }
    }
    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        try {
            //Log.e(TAG,"Inside of Insert:"+myPort);
            long row;
            String currentKey = (String) values.get("key");
            String currentValue = (String) values.get("value");
            String hashValueOfKey = genHash(currentKey);
            String hashValueOfMyPort = genHash(portStr);
            if(successor==null || predecessor ==null) {
                Log.e(TAG,"successor and predecessor were null for the port:"+myPort);
                successor = portStr;
                predecessor = portStr;
            }
            String hashValueOfMySuccessor = genHash(successor);
            String hashValueOfMyPredecessor = genHash(predecessor);

            Log.e(TAG,"Trying Key is"+currentKey);
           // Log.e(TAG,"hash of avd:"+portStr+" is :"+hashValueOfMyPort);
            SQLiteDatabase db = db1.getWritableDatabase();


            //DHT logic starts here

            //Check if this is the only node that exists
            if(((hashValueOfMySuccessor.compareTo(hashValueOfMyPort)==0)) && (hashValueOfMyPort.compareTo(hashValueOfMyPredecessor)==0)){
                Log.e(TAG,"Only one node exists:");
                row = db.insert(TABLE_NAME, "", values);
                Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                Log.e(TAG, "Block:3 Insert SUCCESS");
                return newUri;

            } //Ends: Check if this is the only node that exists

            //There exists only two nodes then
           // else if((hashValueOfMySuccessor.compareTo(hashValueOfMyPredecessor))==0) {
              //  Log.e(TAG, "Two Nodes Exists");
                else { //classic case of insertion and check if your predecessor is greater than you. Handling corner case for values which are less than myself and my predecessor
                            if(((hashValueOfKey.compareTo(hashValueOfMyPort)<0)&&(hashValueOfKey.compareTo(hashValueOfMyPredecessor)>0)) || ((hashValueOfKey.compareTo(hashValueOfMyPort)<0) && (hashValueOfMyPredecessor.compareTo(hashValueOfMyPort)>0)) ){
                                Log.e(TAG,"Insert Block:1");
                                if((hashValueOfKey.compareTo(hashValueOfMyPredecessor)>0)) {
                                    //insert
                                    row = db.insert(TABLE_NAME, "", values);
                                    Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                                    Log.e(TAG, "Block:3 Insert SUCCESS");
                                    return newUri;
                                }
                                else if((hashValueOfMyPredecessor.compareTo(hashValueOfMyPort)>0)) {
                                    //insert


                                    row = db.insert(TABLE_NAME, "", values);
                                    Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                                    Log.e(TAG, "Block:3 Insert SUCCESS");
                                    return newUri;


                                }

                            }
                            else if((hashValueOfMyPort.compareTo(hashValueOfMyPredecessor)<0) && (hashValueOfKey.compareTo(hashValueOfMyPredecessor)>0) && (hashValueOfMyPort.compareTo(hashValueOfKey)<0)){
                                //insert
                               // Cursor resultCursor = getContext().getContentResolver().query(message_uri, null, currentKey, null, null);
                                String key1 = "key= '" + currentKey + "'";

                               // if (resultCursor.getCount() == 0) {
                                    row = db.insert(TABLE_NAME, "", values);
                                    Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                                    Log.e(TAG, "Block:3 Insert SUCCESS");
                                    return newUri;
                               /* } else {
                                    ContentValues args = new ContentValues();
                                    args.put("value", currentValue);
                                    args.put("key", currentKey);
                                    row = db.update(TABLE_NAME, args, key1, null);
                                    Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                                    getContext().getContentResolver().notifyChange(newUri, null);
                                    Log.v("insert", values.toString());
                                    Log.e(TAG, "Block:3 Insert SUCCESS"+currentKey);
                                    return newUri;
                                }*/
                            }
                            else{
                                //forward the request
                                Message insertToPass = new Message();
                                insertToPass.keyvalue = currentKey + "," + currentValue;
                                insertToPass.myPort = portStr;
                                insertToPass.sendPort = Integer.toString(Integer.valueOf(successor) * 2);
                                insertToPass.type = 4;
                                insertToPass.uri = uri.toString();
                                Log.e(TAG, "Block:4 Forward Request"+currentKey);
                                new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, insertToPass);
                            }


                    }
                /*
                if ((hashValueOfKey.compareTo(hashValueOfMyPort) > 0) && (hashValueOfMyPort.compareTo(hashValueOfMyPredecessor) < 0)) {
                    //insert
                    Log.e(TAG, " Trial Insert" + portStr);
                    Cursor resultCursor = getContext().getContentResolver().query(message_uri, null, currentKey, null, null);
                    String key1 = "key= '" + currentKey + "'";

                    if (resultCursor.getCount() == 0) {
                        row = db.insert(TABLE_NAME, "", values);
                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                        Log.e(TAG, "Insert SUCCESS");
                        return newUri;
                    } else {
                        ContentValues args = new ContentValues();
                        args.put("value", currentValue);
                        args.put("key", currentKey);
                        row = db.update(TABLE_NAME, args, key1, null);
                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                        getContext().getContentResolver().notifyChange(newUri, null);
                        Log.v("insert", values.toString());
                        Log.e(TAG, "Insert SUCCESS");
                        return newUri;


                    }
                } else if ((hashValueOfKey.compareTo(hashValueOfMyPort) > 0) && (hashValueOfMyPort.compareTo(hashValueOfMyPredecessor) > 0)) {
                    //forward to successor
                    Log.e(TAG, "TRIAL forward");


                    Message insertToPass = new Message();
                    insertToPass.keyvalue = currentKey + "," + currentValue;
                    insertToPass.myPort = portStr;
                    insertToPass.sendPort = Integer.toString(Integer.valueOf(successor) * 2);
                    insertToPass.type = 4;
                    insertToPass.uri = uri.toString();
                    Log.e(TAG, "Passing to my successor(Two Nodes)" + insertToPass.sendPort + "I am :" + portStr);
                    Log.e(TAG, "Passing key:" + currentKey + "Value:" + currentValue);
                    Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMyPort) < 0) " + (hashValueOfKey.compareTo(hashValueOfMyPort) < 0));
                    Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0)  " + (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0));
                    Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMySuccessor)>0)" + (hashValueOfKey.compareTo(hashValueOfMySuccessor) > 0));
                    new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, insertToPass);
                } else if ((hashValueOfKey.compareTo(hashValueOfMyPort) < 0) && (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0)) {
                    //insert to me
                    Log.e(TAG, " Trial Insert" + portStr);
                    Cursor resultCursor = getContext().getContentResolver().query(message_uri, null, currentKey, null, null);
                    String key1 = "key= '" + currentKey + "'";

                    if (resultCursor.getCount() == 0) {
                        row = db.insert(TABLE_NAME, "", values);
                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                        Log.e(TAG, "Insert SUCCESS");
                        return newUri;
                    } else {
                        ContentValues args = new ContentValues();
                        args.put("value", currentValue);
                        args.put("key", currentKey);
                        row = db.update(TABLE_NAME, args, key1, null);
                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                        getContext().getContentResolver().notifyChange(newUri, null);
                        Log.v("insert", values.toString());
                        Log.e(TAG, "Insert SUCCESS");
                        return newUri;


                    }
                }//end:trial
                //If key is greater than me and less than my successor then pass it to successor
                else if ((hashValueOfKey.compareTo(hashValueOfMyPort) > 0) && (hashValueOfKey.compareTo(hashValueOfMySuccessor) < 0)) {
                    //pass to successor
                    Log.e(TAG, "key is greater than me and less than my successor then pass it to successor");


                    Message insertToPass = new Message();
                    insertToPass.keyvalue = currentKey + "," + currentValue;
                    insertToPass.myPort = portStr;
                    insertToPass.sendPort = Integer.toString(Integer.valueOf(successor) * 2);
                    insertToPass.type = 4;
                    insertToPass.uri = uri.toString();
                    Log.e(TAG, "Passing to my successor(Two Nodes)" + insertToPass.sendPort + "I am :" + portStr);
                    Log.e(TAG, "Passing key:" + currentKey + "Value:" + currentValue);
                    Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMyPort) < 0) " + (hashValueOfKey.compareTo(hashValueOfMyPort) < 0));
                    Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0)  " + (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0));
                    Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMySuccessor)>0)" + (hashValueOfKey.compareTo(hashValueOfMySuccessor) > 0));
                    new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, insertToPass);

                }


                //if key is greater than me and greater than my successor
                else if ((hashValueOfKey.compareTo(hashValueOfMyPort) > 0) && (hashValueOfKey.compareTo(hashValueOfMySuccessor) > 0)) {
                    //insert into starting node
                    if (hashValueOfMyPort.compareTo(hashValueOfMyPredecessor) < 0) {
                        //insert into me
                        Log.e(TAG, "key is greater than me and greater than my successor, INSERTING" + portStr);
                        Cursor resultCursor = getContext().getContentResolver().query(message_uri, null, currentKey, null, null);
                        String key1 = "key= '" + currentKey + "'";

                        if (resultCursor.getCount() == 0) {
                            row = db.insert(TABLE_NAME, "", values);
                            Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                            Log.e(TAG, "Insert SUCCESS");
                            return newUri;
                        } else {
                            ContentValues args = new ContentValues();
                            args.put("value", currentValue);
                            args.put("key", currentKey);
                            row = db.update(TABLE_NAME, args, key1, null);
                            Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                            getContext().getContentResolver().notifyChange(newUri, null);
                            Log.v("insert", values.toString());
                            Log.e(TAG, "Insert SUCCESS");
                            return newUri;
                        }
                    } else if ((hashValueOfMyPort.compareTo(hashValueOfMyPredecessor) > 0)) {
                        //pass it to mypredecessor

                        Message insertToPass = new Message();
                        insertToPass.keyvalue = currentKey + "," + currentValue;
                        insertToPass.myPort = portStr;
                        insertToPass.sendPort = Integer.toString(Integer.valueOf(successor) * 2);
                        insertToPass.type = 4;
                        insertToPass.uri = uri.toString();
                        Log.e(TAG, "Passing to my successor(Two Nodes)" + insertToPass.sendPort + "I am :" + portStr);
                        Log.e(TAG, "Passing key:" + currentKey + "Value:" + currentValue);
                        Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMyPort) < 0) " + (hashValueOfKey.compareTo(hashValueOfMyPort) < 0));
                        Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0)  " + (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0));
                        Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMySuccessor)>0)" + (hashValueOfKey.compareTo(hashValueOfMySuccessor) > 0));
                        new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, insertToPass);
                    }


                }
                //if key is less than me and greater than my predecessor
                else if ((hashValueOfKey.compareTo(hashValueOfMyPort) < 0) && (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0)) {
                    //insert into me
                    //insert
                    Log.e(TAG, " key is less than me and greater than my predecessor, INSERT" + portStr);
                    Cursor resultCursor = getContext().getContentResolver().query(message_uri, null, currentKey, null, null);
                    String key1 = "key= '" + currentKey + "'";

                    if (resultCursor.getCount() == 0) {
                        row = db.insert(TABLE_NAME, "", values);
                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                        Log.e(TAG, "Insert SUCCESS");
                        return newUri;
                    } else {
                        ContentValues args = new ContentValues();
                        args.put("value", currentValue);
                        args.put("key", currentKey);
                        row = db.update(TABLE_NAME, args, key1, null);
                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                        getContext().getContentResolver().notifyChange(newUri, null);
                        Log.v("insert", values.toString());
                        Log.e(TAG, "Insert SUCCESS");
                        return newUri;


                    }
                }
                //weird: less than me, less than my pre and succ
                else if ((hashValueOfKey.compareTo(hashValueOfMyPort) < 0) && (hashValueOfKey.compareTo(hashValueOfMyPredecessor) < 0) && (hashValueOfKey.compareTo(hashValueOfMySuccessor) < 0)) {
                    if (hashValueOfMyPort.compareTo(hashValueOfMySuccessor) < 0) {
                        //insert
                        Log.e(TAG, " weird case insertion" + portStr);
                        Cursor resultCursor = getContext().getContentResolver().query(message_uri, null, currentKey, null, null);
                        String key1 = "key= '" + currentKey + "'";

                        if (resultCursor.getCount() == 0) {
                            row = db.insert(TABLE_NAME, "", values);
                            Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                            Log.e(TAG, "Insert SUCCESS");
                            return newUri;
                        } else {
                            ContentValues args = new ContentValues();
                            args.put("value", currentValue);
                            args.put("key", currentKey);
                            row = db.update(TABLE_NAME, args, key1, null);
                            Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                            getContext().getContentResolver().notifyChange(newUri, null);
                            Log.v("insert", values.toString());
                            Log.e(TAG, "Insert SUCCESS");
                            return newUri;


                        }
                    } else {
                        //pass on
                        Log.e(TAG, "weird case else");


                        Message insertToPass = new Message();
                        insertToPass.keyvalue = currentKey + "," + currentValue;
                        insertToPass.myPort = portStr;
                        insertToPass.sendPort = Integer.toString(Integer.valueOf(successor) * 2);
                        insertToPass.type = 4;
                        insertToPass.uri = uri.toString();
                        Log.e(TAG, "Passing to my successor(Two Nodes)" + insertToPass.sendPort + "I am :" + portStr);
                        Log.e(TAG, "Passing key:" + currentKey + "Value:" + currentValue);
                        Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMyPort) < 0) " + (hashValueOfKey.compareTo(hashValueOfMyPort) < 0));
                        Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0)  " + (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0));
                        Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMySuccessor)>0)" + (hashValueOfKey.compareTo(hashValueOfMySuccessor) > 0));
                        new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, insertToPass);

                    }
                } else {
                    Log.e(TAG, "Compare: Oops, you missed something");
                    Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMyPort) < 0) " + (hashValueOfKey.compareTo(hashValueOfMyPort) < 0));
                    Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0)  " + (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0));
                    Log.e(TAG, "Compare: (hashValueOfKey.compareTo(hashValueOfMySuccessor)>0)" + (hashValueOfKey.compareTo(hashValueOfMySuccessor) > 0));
                }
            } */

/*                      //if thats the case then my predecessor will be larger than me
                            if (hashValueOfMyPredecessor.compareTo(hashValueOfMyPort) > 0) {
                                Log.e(TAG, "Out of Two Nodes, I'm other node(MAYB ERROR) not 5554" + portStr);
                                //Check if key is greater than the predecessor and more than me, then I'll be inserting it
                                if ((hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0) && (hashValueOfMyPort.compareTo(hashValueOfKey) < 0)) {
                                    //insert
                                    Log.e(TAG, "key is greater than the predecessor and more than me, then I'll be inserting it");
                                    Cursor resultCursor = getContext().getContentResolver().query(message_uri, null, currentKey, null, null);
                                    String key1 = "key= '" + currentKey + "'";

                                    if (resultCursor.getCount() == 0) {
                                        row = db.insert(TABLE_NAME, "", values);
                                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                                        Log.e(TAG, "Insert SUCCESS");
                                        return newUri;
                                    } else {
                                        ContentValues args = new ContentValues();
                                        args.put("value", currentValue);
                                        args.put("key", currentKey);
                                        row = db.update(TABLE_NAME, args, key1, null);
                                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                                        getContext().getContentResolver().notifyChange(newUri, null);
                                        Log.v("insert", values.toString());
                                        Log.e(TAG, "Insert SUCCESS");
                                        return newUri;


                                    }
                                } else if ((hashValueOfKey.compareTo(hashValueOfMyPredecessor) < 0)) {
                                    //pass it to my successor
                                    Log.e(TAG, "Passing to my successor(Two Nodes)");
                                    Message insertToPass = new Message();
                                    insertToPass.value = values;
                                    insertToPass.myPort = portStr;
                                    insertToPass.sendPort = Integer.toString(Integer.valueOf(successor) * 2);
                                    insertToPass.uri = uri;
                                    Log.e(TAG, "Passing to my successor(Two Nodes)" + insertToPass.sendPort + "I am :" + portStr);
                                    Log.e(TAG, "Passing key:" + currentKey + "Value:" + currentValue);
                                    new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, insertToPass);
                                }

                            } // end:logic for it you are 5556
                            //I'm the 5554 node then
                            // else if(hashValueOfMySuccessor.compareTo(hashValueOfMyPort)>0){

                            else {
                                Log.e(TAG, "else if(hashValueOfMySuccessor.compareTo(hashValueOfMyPort)>0)  " + hashValueOfMySuccessor.compareTo(hashValueOfMyPort));
                                Log.e(TAG, "After checking I realized Im 5554, please check:" + portStr);

                                //if key is greater than me and less than my successor, just pass it to successor
                                if ((hashValueOfKey.compareTo(hashValueOfMyPort) > 0) && (hashValueOfKey.compareTo(hashValueOfMySuccessor) < 0)) {
                                    //pass it to successor
                                    Log.e(TAG, "Passing to my successor(Two Nodes) as its greater than me and less than my successor, im 5554");
                                    Message insertToPass = new Message();
                                    insertToPass.value = values;
                                    insertToPass.myPort = portStr;
                                    insertToPass.sendPort = Integer.toString(Integer.valueOf(successor) * 2);
                                    insertToPass.type = 4;
                                    insertToPass.uri = uri;
                                    Log.e(TAG, "Passing to my successor(Two Nodes)" + insertToPass.sendPort + "I am :" + portStr);
                                    Log.e(TAG, "Passing key:" + currentKey + "Value:" + currentValue);
                                    new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, insertToPass);


                                } else if ((hashValueOfKey.compareTo(hashValueOfMyPort) > 0) && (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0)) {
                                    //insert
                                    Log.e(TAG, "Inserting in 5554 node (out of two)" + portStr);
                                    Cursor resultCursor = getContext().getContentResolver().query(message_uri, null, currentKey, null, null);
                                    String key1 = "key= '" + currentKey + "'";

                                    if (resultCursor.getCount() == 0) {
                                        row = db.insert(TABLE_NAME, "", values);
                                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                                        Log.e(TAG, "Insert SUCCESS");
                                        return newUri;
                                    } else {
                                        ContentValues args = new ContentValues();
                                        args.put("value", currentValue);
                                        args.put("key", currentKey);
                                        row = db.update(TABLE_NAME, args, key1, null);
                                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                                        getContext().getContentResolver().notifyChange(newUri, null);
                                        Log.v("insert", values.toString());
                                        Log.e(TAG, "Insert SUCCESS");
                                        return newUri;


                                    }

                                }
                                //if its less than me but greater than my predecessor, 51/53/
                                else if ((hashValueOfKey.compareTo(hashValueOfMyPort) < 0) && (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0)) {
                                    //insert
                                    Log.e(TAG, "Inserting in 5554 node (two)" + portStr);
                                    Cursor resultCursor = getContext().getContentResolver().query(message_uri, null, currentKey, null, null);
                                    String key1 = "key= '" + currentKey + "'";

                                    if (resultCursor.getCount() == 0) {
                                        row = db.insert(TABLE_NAME, "", values);
                                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                                        Log.e(TAG, "Insert SUCCESS");
                                        return newUri;
                                    } else {
                                        ContentValues args = new ContentValues();
                                        args.put("value", currentValue);
                                        args.put("key", currentKey);
                                        row = db.update(TABLE_NAME, args, key1, null);
                                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                                        getContext().getContentResolver().notifyChange(newUri, null);
                                        Log.v("insert", values.toString());
                                        Log.e(TAG, "Insert SUCCESS");
                                        return newUri;


                                    }

                                }//End: if its less than me but greater than my predecessor, 51/53/
                                else {
                                    Log.e(TAG, "Something went wrong in two nodes Key:" + currentKey + "Value:" + currentKey + "My Port:" + portStr);
                                }//End:Something went wrong

                            } //end: if u r 5554
*/

            //put flow breack End: There exists only two nodes then



/*
            //If it is not in first or last node
            else {
                //If the key lies between myNode and Predecessor then go ahead and insert
                if ((hashValueOfKey.compareTo(hashValueOfMyPort) < 0) && ((hashValueOfKey.compareTo(hashValueOfMyPredecessor)) > 0)) {
                    Cursor resultCursor = getContext().getContentResolver().query(message_uri, null, currentKey, null, null);
                    String key1 = "key= '" + currentKey + "'";
                    Log.e(TAG,"If the key lies between myNode and Predecessor then go ahead and insert");
                    if (resultCursor.getCount() == 0) {
                        row = db.insert(TABLE_NAME, "", values);
                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                        Log.e(TAG,"Insert SUCCESS");
                        return newUri;
                    } else {
                        ContentValues args = new ContentValues();
                        args.put("value", currentValue);
                        args.put("key", currentKey);
                        row = db.update(TABLE_NAME, args, key1, null);
                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                        getContext().getContentResolver().notifyChange(newUri, null);
                        Log.v("insert", values.toString());
                        Log.e(TAG,"Insert SUCCESS");
                        return newUri;


                    }

                }//End:If the key lies between myNode and Predecessor then go ahead and insert
                //If it doesn't lie in between and is greater than my NodeId(me) then pass it tp successor
                else if(hashValueOfKey.compareTo(hashValueOfMyPort)>0 && (hashValueOfMyPort.compareTo(hashValueOfMySuccessor)>0))
                {
                    if(hashValueOfMyPort.compareTo(hashValueOfMyPredecessor)>0) {
                    Log.e(TAG, "it doesn't lie in between and is greater than my NodeId(me) then pass it to successor");
                    Message insertToPass = new Message();
                    insertToPass.keyvalue = currentKey + "," + currentValue;
                    insertToPass.myPort = portStr;
                    insertToPass.sendPort = Integer.toString(Integer.valueOf(successor) * 2);
                    insertToPass.type = 4;
                    insertToPass.uri = uri.toString();
                    new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, insertToPass);
                    }


                    else if(hashValueOfMyPort.compareTo(hashValueOfMyPredecessor)<0) {
                        //insert into me
                        Log.e(TAG, "key is greater than me and greater than my successor, INSERTING" + portStr);
                        Cursor resultCursor = getContext().getContentResolver().query(message_uri, null, currentKey, null, null);
                        String key1 = "key= '" + currentKey + "'";

                        if (resultCursor.getCount() == 0) {
                            row = db.insert(TABLE_NAME, "", values);
                            Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                            Log.e(TAG, "Insert SUCCESS");
                            return newUri;
                        } else {
                            ContentValues args = new ContentValues();
                            args.put("value", currentValue);
                            args.put("key", currentKey);
                            row = db.update(TABLE_NAME, args, key1, null);
                            Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                            getContext().getContentResolver().notifyChange(newUri, null);
                            Log.v("insert", values.toString());
                            Log.e(TAG, "Insert SUCCESS");
                            return newUri;
                        }
                    }
                }
                //if the key is less than me then give it to my predecessor

//                 Trying to change this
//                else if((hashValueOfKey.compareTo(hashValueOfMyPort)<0) && (hashValueOfKey.compareTo(hashValueOfMyPredecessor)<0)){
//                    if(hashValueOfMyPort.compareTo(hashValueOfMyPredecessor)<    0) {
//                        Log.e(TAG, "it doesn't lie in between and is greater than my NodeId(me) then pass it to predecessor");
//                        Message insertToPass = new Message();
//                        insertToPass.keyvalue = currentKey + "," + currentValue;
//                        insertToPass.myPort = portStr;
//                        insertToPass.sendPort = Integer.toString(Integer.valueOf(predecessor) * 2);
//                        insertToPass.type = 4;
//                        insertToPass.uri = uri.toString();
//                        new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, insertToPass);
//                    }
//                }
                //weird: less than me, less than my pre and succ
                else if((hashValueOfKey.compareTo(hashValueOfMyPort)<0) && (hashValueOfKey.compareTo(hashValueOfMyPredecessor)<0) && (hashValueOfKey.compareTo(hashValueOfMySuccessor)<0)){
                    if(hashValueOfMyPort.compareTo(hashValueOfMySuccessor)<0){
                        //insert
                        Log.e(TAG, " weird case insertion" + portStr);
                        Cursor resultCursor = getContext().getContentResolver().query(message_uri, null, currentKey, null, null);
                        String key1 = "key= '" + currentKey + "'";

                        if (resultCursor.getCount() == 0) {
                            row = db.insert(TABLE_NAME, "", values);
                            Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                            Log.e(TAG, "Insert SUCCESS");
                            return newUri;
                        } else {
                            ContentValues args = new ContentValues();
                            args.put("value", currentValue);
                            args.put("key", currentKey);
                            row = db.update(TABLE_NAME, args, key1, null);
                            Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                            getContext().getContentResolver().notifyChange(newUri, null);
                            Log.v("insert", values.toString());
                            Log.e(TAG, "Insert SUCCESS");
                            return newUri;


                        }
                    }
                    else{
                        //pass on
                        Log.e(TAG,"weird case else");


                        Message insertToPass = new Message();
                        insertToPass.keyvalue=currentKey+","+currentValue;
                        insertToPass.myPort = portStr;
                        insertToPass.sendPort = Integer.toString(Integer.valueOf(successor) * 2);
                        insertToPass.type = 4;
                        insertToPass.uri=uri.toString();
                        Log.e(TAG, "Passing to my successor(Two Nodes)" + insertToPass.sendPort + "I am :" + portStr);
                        Log.e(TAG, "Passing key:" + currentKey + "Value:" + currentValue);
                        new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, insertToPass);

                    }
                }
                else if((hashValueOfKey.compareTo(hashValueOfMyPort)>0) && (hashValueOfMyPort.compareTo(hashValueOfMySuccessor)<0)){
                    Cursor resultCursor = getContext().getContentResolver().query(message_uri, null, currentKey, null, null);
                    String key1 = "key= '" + currentKey + "'";
                    Log.e(TAG,"If the key lies between myNode and Predecessor then go ahead and insert");
                    if (resultCursor.getCount() == 0) {
                        row = db.insert(TABLE_NAME, "", values);
                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                        Log.e(TAG,"Insert SUCCESS");
                        return newUri;
                    } else {
                        ContentValues args = new ContentValues();
                        args.put("value", currentValue);
                        args.put("key", currentKey);
                        row = db.update(TABLE_NAME, args, key1, null);
                        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                        getContext().getContentResolver().notifyChange(newUri, null);
                        Log.v("insert", values.toString());
                        Log.e(TAG,"Insert SUCCESS");
                        return newUri;


                    }
                }
                else{
                    Log.e(TAG,"Compare: Oops, you missed something MORE THAN TWO");
                    Log.e(TAG,"Compare: 2+(hashValueOfKey.compareTo(hashValueOfMyPort) < 0) "+(hashValueOfKey.compareTo(hashValueOfMyPort) < 0) );
                    Log.e(TAG,"Compare: 2+(hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0)  "+ (hashValueOfKey.compareTo(hashValueOfMyPredecessor) > 0));
                    Log.e(TAG,"Compare: 2+(hashValueOfKey.compareTo(hashValueOfMySuccessor)>0)"+(hashValueOfKey.compareTo(hashValueOfMySuccessor)>0) );

                }
            }//Ends :If it is not in first or last node ----BIG CHANGE */


            //DHT logic ends here

        }catch (NoSuchAlgorithmException ex) {
            ex.printStackTrace();
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return uri;
    }//Insert ends here

    //On creating of the content provider, we call and DB Helper which creates the database
    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        db1 = new DBHelper(getContext());
        SQLiteDatabase db = db1.getWritableDatabase();

        //Trying for node request logic
        //Get the port of our AVD
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        Log.e(TAG, "Value of myPort is" + myPort); // this gives 11108, 11112, 11116, 11120 or 11124
        Log.e(TAG, "Avd is" + portStr); // this gives 5554 etc
//rm -rf data/data/edu.buffalo.cse.cse486586.simpledht/

        //If the AVD is the master node i.e. 11108 or AVD:5554 then
        if (myPort.equals("11108")) {
            Log.e(TAG, "I am port 11108 amd trying to create a single node ring" + portStr);
            //Get the nodeId

                //myNodeId = genHash(myPort);
                Log.e(TAG, "Trying to create a single node ring hash for myPort(11108)");
                //RingLargest = myNodeId;
                //RingSmallest = myNodeId;
                successor = portStr;
                predecessor = portStr;
                TotalCountOfNodesInRing++;
                Log.e(TAG, "Created a Single Ring, RingLargest, RingSmallest,Successor, Predecessor:" + portStr);
                Log.e(TAG, "Total Count of Nodes in Ring" + TotalCountOfNodesInRing);

        }
        //Logic ends: If the AVD is the master node i.e. 11108 or AVD:5554
        //If its any other node,
        else {

                //  myNodeId = genHash(myPort);
                Message requestToJoin = new Message();
            requestToJoin.type = 1;
            requestToJoin.myPort = portStr;
            requestToJoin.sendPort = "11108";

                Log.e(TAG, "Step: 1 msgToJoin created, sending from" + portStr + "Sending it to"+requestToJoin.sendPort);
                new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, requestToJoin);

//            catch (NoSuchAlgorithmException e) {
//                Log.e(TAG, "Error while generating hash for" + myPort);
//            }

        }

        //Now call the server task
        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            /*
             * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             *
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            Log.e(TAG, "Can't create a ServerSocket Booh!");
            return false;
        }

        //Node request logic ends here

        return true;

    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // TODO Auto-generated method stub
        SQLiteDatabase db = db1.getReadableDatabase();
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TABLE_NAME);
        Cursor cursor;

        Log.e(TAG, "Inside Query where selection is" + selection);

        if (selection.equals("\"@\"")) {
            Log.e(TAG, "Selection is matched to @");
            cursor = queryBuilder.query(db, projection, null, null, null, null, sortOrder);

            //cursor.moveToFirst();
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
            Log.e(TAG, "cursor.getcount()" + cursor.getCount());

            Log.e(TAG, "To String value of Cursor" + ConvertCursorToString(cursor));


            return cursor;
        }
        //We need to check for * now
        if (selection.equals("\"*\"")) {
            Log.e(TAG, "Fetching entire dump: AVD" + portStr);
            Log.e(TAG, "Selection is matched to *");
            cursor = db.rawQuery("SELECT * FROM message_tbl;", null);
            String StringCursor = ConvertCursorToString(cursor);
            Log.e(TAG, "Selection:*, my successor:" + successor + "My Predecessor:" + predecessor + "I am:" + portStr);
            try {
                if ((genHash(portStr).compareTo(genHash(successor)) == 0) && (genHash(portStr).compareTo(genHash(predecessor)) == 0)) {
                    Log.e(TAG, "Single AVD select *");
                    return cursor;

                } else {
                    Log.e(TAG,"Else block of *");

                    Cursor accumulatedCursor = getAllRecordInit(StringCursor, myPort, successor);
                    Log.e(TAG,"getAllRecordInit returned with cursor having count:"+accumulatedCursor.getCount());
                    if(accumulatedCursor==null){
                        Log.e(TAG, "accumulatedCursor was null: so changing it" + accumulatedCursor);
                        cursor = db.rawQuery("SELECT * FROM message_tbl;", null);
                        return cursor;
                    }
                    Log.e(TAG, "FINALLY getAllRecord: accumulatedCursor not null and its count is" + accumulatedCursor.getCount());


                    return accumulatedCursor;
                }
            } catch (NoSuchAlgorithmException e) {

            }
        }



            else{

                // Cursor resultCursor = getContext().getContentResolver().query(message_uri, null, currentKey, null, null);
                Log.e(TAG, "Got a ? Query from " + portStr);

                String Newselection = "key='" + selection + "'";
                //search key logic
                cursor = queryBuilder.query(db, null, Newselection,
                        null, null, null, null);
                //classic case of insertion and check if your predecessor is greater than you. Handling corner case for values which are less than myself and my predecessor

                cursor.moveToFirst();
                    try {
                        if ((genHash(portStr).compareTo(genHash(successor)) == 0) && (genHash(portStr).compareTo(genHash(predecessor)) == 0)) {
                            Log.e(TAG, "Single AVD select ?");
                            return cursor;

                        }
                    }catch(NoSuchAlgorithmException e){

                    }

                if ((cursor.getCount() == 0) && (!portStr.equals(successor))) {

                    //forward the request

                    Log.e(TAG, "Forwarding the request now: getParticularRecord");
                    Cursor particularcursor = getParticularRecordInit(myPort, selection, successor, null);

                    Log.e(TAG, "FINALLY getParticularRecord Output: " + ConvertCursorToString(particularcursor));

                    return particularcursor;

                } else {
                    Log.e(TAG, "Got my ? query result, returning results to:" + portStr);
                    return cursor;
                }


            }
            return null;
        }




/*
        else {
            Cursor cursor = queryBuilder.query(db, null, Newselection,
                    null, null, null, null);

            Log.e("else block of query", selection);

            return cursor;
        }*/
//AMAIR
    public Cursor getAllRecordInit(String StringCursor,String queryInitiator,String nextsuccessor) {




//                StringCursor = StringCursor + getAllRecord(StringCursor, myPort,successor);
                Message queryMsgToSuccessor = new Message();
                queryMsgToSuccessor.successor = nextsuccessor;
                queryMsgToSuccessor.sendPort = Integer.toString(Integer.valueOf(nextsuccessor)*2);
                queryMsgToSuccessor.queryResponse = StringCursor;
                queryMsgToSuccessor.type = 5;
                queryMsgToSuccessor.queryStartPort = queryInitiator;
                Log.e(TAG,"In getAllRecord, forwarding request to: "+queryMsgToSuccessor.sendPort+"Currently inside:"+myPort);
                Log.e(TAG,"Query Initiator is"+queryMsgToSuccessor.queryStartPort);

                new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryMsgToSuccessor);
        waitForAccumulation =true;
        while(waitForAccumulation){
           // Log.e(TAG,"In while for waitForAccumulation");


        }
        Log.e(TAG,"Must have got results");

                return globalAccumulatedCursor;

    }
    //amair
    public void getAllRecordNotInit(String StringCursor,String queryInitiator,String nextsuccessor) {

         if(queryInitiator.equals(myPort)){
             Log.e(TAG,"Time to check if it containts results or isEmpty");

                if(StringCursor == null || StringCursor.isEmpty()){
                    Log.e(TAG,"String was empty or null");
                    waitForAccumulation = false;
                }
             else {
                    Log.e(TAG,"Yes, it contains results which is:"+StringCursor);
                    Map<String, String> tempmap = convertStringToMap(StringCursor);
                    Log.e(TAG, "Map formed" + tempmap.size());
                    Cursor finalcursor = convertMapToCursor(tempmap);
                    Log.e(TAG, "AMAIR getAllRecords are:" + StringCursor);
                    globalAccumulatedCursor = finalcursor;
                    waitForAccumulation = false;
                }
        }
        else {
            SQLiteDatabase db = db1.getReadableDatabase();
            SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
            queryBuilder.setTables(TABLE_NAME);

            Cursor Avdcursor = db.rawQuery("SELECT * FROM message_tbl;", null);
            String AvdStringCursor = ConvertCursorToString(Avdcursor);


            Log.e(TAG, "Query * Results from AVD:" + portStr + "as follows:" + AvdStringCursor);
            String attachedResults = StringCursor + AvdStringCursor;
            Log.e(TAG, "Accumulated Results are:" + attachedResults);
            Log.e(TAG, "Now passing these results to:" + successor);

            Message queryMsgToSuccessor =new Message();

            queryMsgToSuccessor.sendPort = Integer.toString(Integer.valueOf(nextsuccessor) * 2);

            queryMsgToSuccessor.type = 5;
            queryMsgToSuccessor.queryStartPort = queryInitiator;

            queryMsgToSuccessor.queryResponse = attachedResults;
            Log.e(TAG, "In getAllRecordNotInit, forwarding request to:" + queryMsgToSuccessor.sendPort + "Currently inside:" + myPort);
            Log.e(TAG, "Query Initiator is" + queryMsgToSuccessor.queryStartPort);
            new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryMsgToSuccessor);

        }

    }



    public Cursor getParticularRecordInit(String queryInitiatePort, String selection,String nextSuccessor, String queryResponse){


        Log.e(TAG,"Inside getPartiularRecord, this is AVD:"+portStr);
        String Newselection = "key='" + selection + "'";
        SQLiteDatabase db = db1.getReadableDatabase();
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TABLE_NAME);
        Message queryMsgToSuccessor = new Message();
        Cursor cursor = queryBuilder.query(db, null, Newselection,
                null, null, null, null);
        cursor.moveToFirst();

        if(cursor.getCount() == 0){
            Log.e(TAG,"Record not found in AVD:"+portStr);
            Log.e(TAG,"Forwording it to my successor:"+successor);

            queryMsgToSuccessor.sendPort = Integer.toString(Integer.valueOf(nextSuccessor) * 2);

            queryMsgToSuccessor.type = 6;
            queryMsgToSuccessor.queryStartPort = queryInitiatePort;
            queryMsgToSuccessor.selection = selection;
            queryMsgToSuccessor.queryResponse = null;
            Log.e(TAG, "In getParticularRecord, forwarding request to:" + queryMsgToSuccessor.sendPort + "Currently inside:" + myPort);
            Log.e(TAG, "Query Initiator is" + queryMsgToSuccessor.queryStartPort);
            new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryMsgToSuccessor);

            waitForSingle = true;
            Log.e(TAG,"Just changed waitforSingle to true in"+portStr);


                while (waitForSingle) {
                  //  Log.e(TAG, "In while: waitForSingle" + portStr);

                }
            return globalCursor;

        }
        else{
            Log.e(TAG,"Record found in AVD:"+portStr);
            return cursor;
        }

    }
    public void particularRecordNotInit(String queryInitiatePort, String selection,String nextSuccessor, String queryResponse){
        Log.e(TAG,"Inside particularRecordNotInit, this is AVD:"+portStr);
        String Newselection = "key='" + selection + "'";
        SQLiteDatabase db = db1.getReadableDatabase();
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TABLE_NAME);

        if(queryInitiatePort.equals(myPort)){
            //set the globalCursor to queryResponse
            Log.e(TAG,"AMAIR particularRecordNotInit Found the query Response"+queryResponse);
            Map<String, String> particularRecord = convertStringToMap(queryResponse);
            Log.e(TAG,"AMAIR final record response converted to map with size:"+particularRecord.size());
            Cursor particularRecordCursor = convertMapToCursor(particularRecord);
            Log.e(TAG,"AMAIR cursor count is (mayb 1) :"+particularRecordCursor.getCount());
            globalCursor = particularRecordCursor;
            waitForSingle = false;

        }

        else{
            //check if its there in my DB, if yes append, if no dont append, finally forward
            Log.e(TAG,"Checking my DB");
            Message queryMsgToSuccessor = new Message();
            Cursor cursor = queryBuilder.query(db, null, Newselection,
                    null, null, null, null);
            cursor.moveToFirst();
            if(cursor.getCount()==0){
                Log.e(TAG,"Not Found in"+portStr);
                //forward
                queryMsgToSuccessor.sendPort = Integer.toString(Integer.valueOf(nextSuccessor) * 2);

                queryMsgToSuccessor.type = 6;
                queryMsgToSuccessor.queryStartPort = queryInitiatePort;
                queryMsgToSuccessor.selection = selection;
                queryMsgToSuccessor.queryResponse =queryResponse;
                new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryMsgToSuccessor);
            }
            else{
                queryMsgToSuccessor.sendPort = Integer.toString(Integer.valueOf(nextSuccessor) * 2);
                Log.e(TAG,"Found in"+portStr);
                queryMsgToSuccessor.type = 6;
                queryMsgToSuccessor.queryStartPort = queryInitiatePort;
                queryMsgToSuccessor.selection = selection;
                queryMsgToSuccessor.queryResponse = ConvertCursorToString(cursor);
                new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryMsgToSuccessor);

            }

        }


    }



    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }


    //Server Task for the AVD starts here
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            try {
                ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
                while (true) {
                    Socket soc = serverSocket.accept();
                    Log.e(TAG, "Server socket accepted");
                    InputStream is = soc.getInputStream();
                    ObjectInputStream ois = new ObjectInputStream(is);
                    Object received = ois.readObject();
                    Message receivedMessage = (Message) received;
                    Log.e(TAG, "In Server, Received Message in SeverTask with type:"+receivedMessage.type+"Coming from:"+receivedMessage.myPort+"It has now reached to this port:"+myPort);
                    //If the message received is coming from a node who wants to join the ring, only if its AVD0
                    if (receivedMessage.type == 1) {
                        Log.e(TAG, "Message Type:1, coming from"+receivedMessage.myPort+"It has reached to:"+myPort);

                        if((genHash(portStr).compareTo(genHash(predecessor))==0) && (genHash(portStr).compareTo(genHash(successor))==0)){
                            Log.e(TAG,"BLOCK 1");
                            Log.e(TAG,"Only single node exist currently");
                            Log.e(TAG, "Successor & Predecessor are equal to 5554");
                            Message replyToRequestingNode = new Message();
                            //type:2 is response to join
                            replyToRequestingNode.type = 2;
                            //Nodes join will be have my successor
                            replyToRequestingNode.successor = successor;
                            replyToRequestingNode.predecessor = portStr;
                            int temp = (Integer.valueOf(receivedMessage.myPort)*2);
                            replyToRequestingNode.sendPort = Integer.toString(temp);
                            replyToRequestingNode.myPort = portStr;
                            successor = receivedMessage.myPort;
                            predecessor = receivedMessage.myPort;
                            Log.e(TAG,"I am:"+portStr+"I have changed my successor:"+successor+"Predecessor:"+predecessor);
                            Log.e(TAG,"Asking the port:"+replyToRequestingNode.sendPort+"To change its predecessor"+replyToRequestingNode.predecessor+" and successor to:"+replyToRequestingNode.successor);
                            new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, replyToRequestingNode);
                        }//Ends here:Check if there exists just one node in the entire ring

                        else if((genHash(receivedMessage.myPort).compareTo(genHash(portStr))>0) && (genHash(receivedMessage.myPort).compareTo(genHash(successor))>0) && (genHash(portStr).compareTo(genHash(successor))>0)) {
                            //insert it here
                            Log.e(TAG,"BLOCK 2");
                            Message replyForChangeOfPredecessor = new Message();
                            replyForChangeOfPredecessor.type =3;
                            replyForChangeOfPredecessor.sendPort = Integer.toString((Integer.valueOf(successor)*2));
                            replyForChangeOfPredecessor.myPort = portStr;
                            replyForChangeOfPredecessor.predecessor = receivedMessage.myPort;
                            Log.e(TAG,"BLOCK 2 asking successor:"+successor+"To change its predecessor to:"+replyForChangeOfPredecessor.predecessor);

                            //Now change my successor
                            Message replyToRequestingNode = new Message();
                            replyToRequestingNode.type=2;
                            replyToRequestingNode.predecessor =portStr;
                            replyToRequestingNode.successor = successor;
                            successor = receivedMessage.myPort;
                            replyToRequestingNode.myPort = portStr;
                            replyToRequestingNode.sendPort = Integer.toString((Integer.valueOf(receivedMessage.myPort)*2));
                            Log.e(TAG,"BLOCK 2 Reply to:"+receivedMessage.myPort+replyToRequestingNode.sendPort+"Its new predecessor as:"+replyToRequestingNode.predecessor+"Its new successor as:"+replyToRequestingNode.successor);
                            new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, replyForChangeOfPredecessor);
                            new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, replyToRequestingNode);

                        }
                        else if((genHash(receivedMessage.myPort).compareTo(genHash(portStr))<0) && (genHash(receivedMessage.myPort).compareTo(genHash(successor))<0) && (genHash(portStr).compareTo(genHash(successor))>0)) {
                            //insert it here
                            Log.e(TAG,"BLOCK 3");
                            //inserting it ahead of me
                            Message replyForChangeOfPredecessor = new Message();
                            replyForChangeOfPredecessor.type =3;
                            replyForChangeOfPredecessor.sendPort = Integer.toString((Integer.valueOf(successor)*2));
                            replyForChangeOfPredecessor.myPort = portStr;
                            replyForChangeOfPredecessor.predecessor = receivedMessage.myPort;
                            Log.e(TAG,"BLOCK 3 asking successor:"+successor+"To change its predecessor to:"+replyForChangeOfPredecessor.predecessor);

                            //Now change my successor
                            Message replyToRequestingNode = new Message();
                            replyToRequestingNode.type=2;
                            replyToRequestingNode.predecessor =portStr;
                            replyToRequestingNode.successor = successor;
                            successor = receivedMessage.myPort;
                            replyToRequestingNode.myPort = portStr;
                            replyToRequestingNode.sendPort = Integer.toString((Integer.valueOf(receivedMessage.myPort)*2));
                            Log.e(TAG,"BLOCK 3 Reply to:"+receivedMessage.myPort+replyToRequestingNode.sendPort+"Its new predecessor as:"+replyToRequestingNode.predecessor+"Its new successor as:"+replyToRequestingNode.successor);
                            new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, replyForChangeOfPredecessor);
                            new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, replyToRequestingNode);




                        }
                        else if((genHash(receivedMessage.myPort).compareTo(genHash(portStr))>0) && (genHash(receivedMessage.myPort).compareTo(genHash(successor))<0)) {
                            Log.e(TAG,"BLOCK 4");
                            //insert it here
                            //inserting it ahead of me
                            Message replyForChangeOfPredecessor = new Message();
                            replyForChangeOfPredecessor.type =3;
                            replyForChangeOfPredecessor.sendPort = Integer.toString((Integer.valueOf(successor)*2));
                            replyForChangeOfPredecessor.myPort = portStr;
                            replyForChangeOfPredecessor.predecessor = receivedMessage.myPort;
                                Log.e(TAG,"BLOCK 4 asking successor:"+successor+"To change its predecessor to:"+replyForChangeOfPredecessor.predecessor);
                            //Now change my successor
                            Message replyToRequestingNode = new Message();
                            replyToRequestingNode.type=2;
                            replyToRequestingNode.predecessor =portStr;
                            replyToRequestingNode.successor = successor;
                            successor = receivedMessage.myPort;
                            replyToRequestingNode.myPort = portStr;
                            replyToRequestingNode.sendPort = Integer.toString((Integer.valueOf(receivedMessage.myPort)*2));
                            Log.e(TAG,"BLOCK 4 Reply to:"+receivedMessage.myPort+replyToRequestingNode.sendPort+"Its new predecessor as:"+replyToRequestingNode.predecessor+"Its new successor as:"+replyToRequestingNode.successor);

                            new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, replyForChangeOfPredecessor);
                            new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, replyToRequestingNode);


                        }
                        else{
                            Log.e(TAG,"BLOCK 5");
                            Log.e(TAG,"Pass it to successor");
                            Message forwardRequest = new Message();
                            forwardRequest.type = 1;

                            forwardRequest.sendPort = Integer.toString((Integer.valueOf(successor)*2));;
                            forwardRequest.myPort = receivedMessage.myPort;
                            Log.e(TAG,"Block 5, forwarding the request to join of "+receivedMessage.myPort);
                            Log.e(TAG,"Block 5, forwardRequest to the sendport :"+forwardRequest.sendPort);
                            new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, forwardRequest);
                        }

/*
                        //Check if its between you and your successor,, using HASH
                        //Check if its greater than me && Check it its less than my successor
                       else if(((genHash(portStr).compareTo(genHash(receivedMessage.myPort))<0) && (genHash(successor).compareTo(genHash(receivedMessage.myPort))>0)) || ((genHash(portStr).compareTo(genHash(receivedMessage.myPort))>0) && (genHash(successor).compareTo(genHash(receivedMessage.myPort))<0))){
                            Log.e(TAG, "The node lies IN BETWEEN portStr:"+portStr+"and my successor:"+successor+" And node port is"+receivedMessage.myPort);
                            //Let the successor of me, know that I have changed it's predecessor
                            Message replyToChangeOfPredecessor = new Message();
                            //type=3 means change your predecessor
                            replyToChangeOfPredecessor.type = 3;
                            replyToChangeOfPredecessor.predecessor = receivedMessage.myPort;
                            replyToChangeOfPredecessor.myPort = portStr;
                            replyToChangeOfPredecessor.sendPort = Integer.toString(Integer.valueOf(successor)*2);
                            Log.e(TAG,"Asking "+replyToChangeOfPredecessor.sendPort+"To change its predecessor to:"+replyToChangeOfPredecessor.predecessor);
                            new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, replyToChangeOfPredecessor);


                                //replying back to the node which requested to join
                                Message replyToRequestingNode = new Message();
                                //type:2 is response to join
                                replyToRequestingNode.type = 2;
                                //Nodes join will be have my successor
                                replyToRequestingNode.successor = successor;
                                replyToRequestingNode.predecessor = portStr;
                                replyToRequestingNode.sendPort = Integer.toString(Integer.valueOf(receivedMessage.myPort)*2);
                                replyToRequestingNode.myPort = portStr;
                                successor = receivedMessage.myPort;
                            Log.e(TAG,"Just changed by successor:"+receivedMessage.myPort);
                            Log.e(TAG,"Asking the port:"+replyToRequestingNode.sendPort+"To change its predecessor"+replyToRequestingNode.predecessor+" and successor to:"+replyToRequestingNode.successor);
                            new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, replyToRequestingNode);

                        }
                        //if its greater than me and my successor
                        else if((((genHash(portStr).compareTo(genHash(receivedMessage.myPort)))<0) && ((genHash(successor).compareTo(genHash(receivedMessage.myPort))) < 0)) || (((genHash(portStr).compareTo(genHash(receivedMessage.myPort)))>0) && ((genHash(successor).compareTo(genHash(receivedMessage.myPort))) > 0))){
                            Log.e(TAG,"GREATER than both myself nd successor");
                            Log.e(TAG, "The node greater than portStr:"+portStr+"and my successor:"+successor+"Node which is asking, port value is"+receivedMessage.myPort);
                                    //if its not between me and the last node
                                if((genHash(portStr).compareTo(successor)<0)){
                                    Log.e(TAG,"This is between me:"+myPort+"and the last node:"+successor);
                                Log.e(TAG, "successor is not equal to 5554 and its original value is:"+successor);
                                //this happens if the node doesn't belong in between the current node and it's successor
                               // receivedMessage.sendPort = Integer.toString(Integer.valueOf(successor) * 2);
                                    Message forwardRequest = new Message();
                                    forwardRequest.myPort = receivedMessage.myPort;
                                    forwardRequest.sendPort = Integer.toString(Integer.valueOf(successor) * 2);
                                    forwardRequest.type = 1;

                                //just forward the request to your successor
                                Log.e(TAG, "Forward Join Request to:"+forwardRequest.sendPort+"The request was from"+forwardRequest.myPort);
                                new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, forwardRequest);
                            }


                            //before forwarding please check if its between you and the last node
                            else if((genHash(portStr).compareTo(successor)>0)){
                                Log.e(TAG, "Im greater than my sucessor");
                                //Let the successor of me, know that I have changed it's predecessor
                                Message replyToChangeOfPredecessor = new Message();
                                //type=3 means change your predecessor
                                replyToChangeOfPredecessor.type = 3;
                                replyToChangeOfPredecessor.predecessor = receivedMessage.myPort;
                                replyToChangeOfPredecessor.sendPort = Integer.toString(Integer.valueOf(successor)*2);
                               // replyToChangeOfPredecessor.myPort = myPort;
                                Log.e(TAG, "Type=3 Asking my successor:"+successor+" to change its predecessor value to:"+replyToChangeOfPredecessor.predecessor);
                                new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, replyToChangeOfPredecessor);


                                //then just add it here
                                Message replyToRequestingNode = new Message();
                                //type:2 is response to join
                                replyToRequestingNode.type = 2;
                                //Nodes join will be have my successor
                                replyToRequestingNode.successor = successor;
                                replyToRequestingNode.predecessor = portStr;
                                replyToRequestingNode.sendPort = Integer.toString(Integer.valueOf(receivedMessage.myPort)*2);
                                replyToRequestingNode.myPort = portStr;
                                successor = receivedMessage.myPort;
                                Log.e(TAG,"Sending Type=2 response to"+replyToRequestingNode.sendPort+"Telling it to change Predecessor to:"+replyToRequestingNode.predecessor+"And successor to:"+replyToRequestingNode.successor);
                                new MessageClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, replyToRequestingNode);


                            }


                        }*/




                    }//logic Ends :If the message received is coming from a node who wants to join the ring i.e TYPE=1 ends

                    //TYPE = 2, You just got your predecessor and successor
                    if (receivedMessage.type == 2){
                        Log.e(TAG,"Received TYPE=2 from"+receivedMessage.myPort);
                        successor = receivedMessage.successor;

                        predecessor = receivedMessage.predecessor;
                        Log.e(TAG,"Received TYPE=2 message and change my successor as"+successor+"And my predeccessor to"+predecessor);
                    }//Logic Ends:TYPE = 2, You just got your predecessor and successor

                    //TYPE=3 You are asked to change your predecessor
                    if (receivedMessage.type == 3){
                        predecessor = receivedMessage.predecessor;
                        Log.e(TAG,"Received TYPE=3 message And my predeccessor to"+predecessor);
                    }

                    //TYPE=4 You just received a insert request
                    if(receivedMessage.type==4){
                        Log.e(TAG,"Type=4 Insert forward request received from:"+receivedMessage.myPort+"I am trying to insert it into me"+portStr);
                        String pair = receivedMessage.keyvalue;
                        Log.e(TAG,"Received encoded keyvalue is:"+receivedMessage.keyvalue);
                        String[] keyValue = pair.split(",");
                        Log.e(TAG,"Key after parse:"+keyValue[0]);
                        Log.e(TAG,"Value after parse:"+keyValue[1]);

                        ContentValues contentValues = new ContentValues();

                        contentValues.put("key", keyValue[0]);
                        contentValues.put("value", keyValue[1]);
                        Uri receivedmessage_uri = Uri.parse(receivedMessage.uri);
                        Log.e(TAG,"URI after parse:"+message_uri.toString());
                        insert(receivedmessage_uri, contentValues);
                    }
                    //TYPE=5 starting
                    if(receivedMessage.type==5){
                        Log.e(TAG,"IF BLOCK OF TYPE=5, receivedMessage.queryResponse is "+receivedMessage.queryResponse);
                        getAllRecordNotInit(receivedMessage.queryResponse, receivedMessage.queryStartPort, successor);
                    }

                    if(receivedMessage.type==6){
                        //Cursor currentData = query(message_uri,null,receivedMessage.selection,null,null,null);
                        Log.e(TAG,"Got TYPE 6 coming from"+receivedMessage.myPort+"I am :"+portStr);

                       // Cursor c= query(message_uri,null,receivedMessage.selection,null,null);
                      // getParticularRecord(receivedMessage.queryStartPort, receivedMessage.selection, successor);
                        particularRecordNotInit(receivedMessage.queryStartPort, receivedMessage.selection,successor,receivedMessage.queryResponse);



                    }
                    if(receivedMessage.type==7){
                        //Cursor currentData = query(message_uri,null,receivedMessage.selection,null,null,null);
                        Log.e(TAG,"Got TYPE 7 coming from"+receivedMessage.myPort+"I am :"+portStr);

                        // Cursor c= query(message_uri,null,receivedMessage.selection,null,null);
                        // getParticularRecord(receivedMessage.queryStartPort, receivedMessage.selection, successor);
                        deleteAllRecordNotInit(receivedMessage.queryStartPort,successor);



                    }

                }


            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (ClassNotFoundException ex) {
                ex.printStackTrace();
            }catch (NoSuchAlgorithmException ex) {
                ex.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */

            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */

            try {

                String strReceived = strings[0].trim();

//                ContentValues contentValues = new ContentValues();
//                ContentResolver cr = getContentResolver();
//                contentValues.put("key", Integer.toString(MsgKey));
//                contentValues.put("value", strReceived);
//                cr.insert(message_uri,contentValues);

            } catch (Exception e) {
                Log.e(TAG, "Insert failed");
            }

            return;
        }
    }
    //Server Task along with onProgress finishes here

    //ClientTask starts here
    private class MessageClientTask extends AsyncTask<Message, Void, Void> {

        @Override
        protected Void doInBackground(Message... msg) {
            try {
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */
                Log.e(TAG,"In MessageClientTask, the msg[0] details are: type"+msg[0].type+"Port is"+msg[0].myPort+"Sending this to"+msg[0].sendPort);
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(msg[0].sendPort));
                //Log.e(TAG, "Now requesting to join the ring from " + msg[0].myPort + "this is send to " + msg[0].sendPort);
                OutputStream os = socket.getOutputStream();
                ObjectOutputStream msgObject = new ObjectOutputStream(os);
                msgObject.writeObject(msg[0]);
                os.flush();
                Log.e(TAG, "SUCCESS in MessageClientTask");
                os.close();
                msgObject.close();
                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
                Log.e(TAG,e.getMessage());

            }

            return null;
        }


    } //ClientTask ends here


    //convert the cursor to string
    public String ConvertCursorToString(Cursor resultCursor)
    {
        String row = "";

        if(resultCursor!=null && resultCursor.getCount() != 0)
        {
            resultCursor.moveToFirst();

            while(true)
            {
                int keyIndex = resultCursor.getColumnIndex(ID);
                int valueIndex = resultCursor.getColumnIndex(MESSAGE);

                if (keyIndex == -1 || valueIndex == -1)
                {
                    Log.e(TAG, "Dude, something went wrong while converting cursor to string");
                    resultCursor.close();
                }

                String returnKey = resultCursor.getString(keyIndex);
                String returnValue = resultCursor.getString(valueIndex);

                row = row + returnKey +"," +returnValue + "\n";

                //continue while until last row encountered
                if(!resultCursor.isLast())
                {
                    resultCursor.moveToNext();

                }
                else
                {
                    break;
                }
            }
        }

        return row;
    }

//convert the map to cursor
    public Cursor convertMapToCursor(Map<String, String> cursorMap) {
        //define a mutable cursor with key value as column
        String[] columns = { "key", "value" };

        MatrixCursor cursor = new MatrixCursor(columns);
        //for each entry put its keyy and value to this matrixcursor
        for (Map.Entry<String, String> entry : cursorMap.entrySet()) {

            Object[] row = new Object[cursor.getColumnCount()];

            row[cursor.getColumnIndex("key")] = entry.getKey();
            row[cursor.getColumnIndex("value")] = entry.getValue();

            cursor.addRow(row);
        }
        cursor.close();
        return cursor;
    }

//convert the string to map
    public Map<String, String> convertStringToMap(String finalResult){
        HashMap<String, String> map = new HashMap<>();
        String[] lines = finalResult.split("\\r?\\n");
        //Log.e(TAG,"convertStringToMap"+lines[0]);
        for(String lin:lines){
        String[] keyValue = lin.split(",");
            map.put(keyValue[0],keyValue[1]);

        }
        return map;
    }

}

