package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import java.io.Serializable;

/**
 * Created by abdul on 3/31/15.
 */
public class Message  implements Serializable {

    private static final long serialVersionUID = 1L;

    public int type; // Message Type, 1--> JOIN  2-->REPLYToJoinRequest, 3-->Change Precessor, 4--->Insert forwarded request,5--->getAllRecordNotInit, 6-->particularRecordNotInit, 7--->deleteAllRecordNotInit
    public String myPort; //Sender's Port
    public String predecessor; // Used to store predecessor port of a node
    public String successor; // Used to store successor port of a node
    public String RingSmallest; // Used to store smallest node ID in the ring
    public String RingLargest; // Used to store largest node ID in the ring
    public String sendPort; // Port of the receiver
    public String queryStartPort; // Port of message that started the query or global query
    public String uri; // Uri used for insert, query, delete (converted to string)
    public String selection; // The key to query or delete
    public String keyvalue;
    public String queryResponse;


    public Message(){

    }

    public Message(int type, String myPort, String predecessor, String successor, String smallestID, String largestID, String sendPort){
        this.type = type;
        this.myPort = myPort;
        this.predecessor = predecessor;
        this.successor = successor;
        this.RingSmallest = smallestID;
        this.RingLargest = largestID;
        this.sendPort = sendPort;


    }
}

