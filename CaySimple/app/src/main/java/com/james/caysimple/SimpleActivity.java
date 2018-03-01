package com.james.caysimple;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cz.msebera.android.httpclient.Header;

public class SimpleActivity extends AppCompatActivity {

    String cayenneemail = "cayenneemail@gmail.com";
    String cayennepassword = "cayennepassword";

    String deviceID = "af749630-e884-11e7-848a-61efd1c01e7d";
    String sensorIDtemp = "ddb68760-e889-11e7-8934-ff70c6ef636b";
    String sensorIDcycles = "2a3b2970-062f-11e8-bc3e-1943cd9613f7";
    String sensorIDstatus = "28fead40-085d-11e8-8620-addae6ef14ff";

    String deviceID2 = "d4fcd880-e9a7-11e7-80de-0f99a32d7a93";
    String sensorIDtemp2 = "3f733430-ea74-11e7-8934-ff70c6ef636b";


    public final class CayDataPoint  {
        double v = 0;
        String ts = "";
        String unit = "";
        String device_type = "";
        String sensorID;
        String deviceID;
        TextView tv;
        Date reqSend = null;
        Date reqRecv = null;
        int Pending = 0;

        public CayDataPoint (String devID, String senID, TextView where){
            deviceID = devID;
            sensorID = senID;
            tv = where;
        }

        public void update(){
            String urlString = "https://platform.mydevices.com/v1.1/telemetry/" + deviceID +
                    "/sensors/" + sensorID + "/summaries?type=" + "latest";

            AsyncHttpClient getCayenne = new AsyncHttpClient();
            getCayenne.addHeader("Authorization", "Bearer "+ authToken);
            reqSend = new Date();
            Pending =  1;
            getCayenne.get(urlString, new AsyncHttpResponseHandler() {

                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                    // called when response HTTP status is "200 OK"
                    try {
                        reqRecv = new Date();
                        Pending = 0;
                        JSONArray ja = new JSONArray(new String(response));
                        JSONObject jo = ja.getJSONObject(0);

                        v = jo.getDouble("v");
                        ts = jo.getString("ts");
                        unit = jo.getString("unit");
                        device_type = jo.getString("device_type");

                        if (tv != null) {
                            write_it();
                        }

                    } catch (Exception e) {
                        UpdateStatus(0, e.getMessage());
                    }
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                    reqRecv = new Date();
                    Pending = 0;
                    UpdateStatus(statusCode, e.getMessage() + ", query=" + String.valueOf(reqRecv.getTime()-reqSend.getTime()) + "ms");
                }
            });
        }

        public void updategraph(int leftorright){

            Date start;
            long startdate;
            long enddate;
            final int lor = leftorright;  // 0 for primary/left scale, 1 for right/secondary scale

            start = new Date();
            enddate = start.getTime();
            startdate = enddate - 24 * 3600000 ;   // 24 hours of data in ms

            String urlString = "https://platform.mydevices.com/v1.1/telemetry/" + deviceID +
                    "/sensors/" + sensorID + "/summaries?type=custom&startDate=" + String.valueOf(startdate) + "&endDate=" + String.valueOf(enddate);

            AsyncHttpClient getCayenne = new AsyncHttpClient();
            getCayenne.addHeader("Authorization", "Bearer "+ authToken);
            reqSend = new Date();

            getCayenne.get(urlString, new AsyncHttpResponseHandler() {

                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                    // called when response HTTP status is "200 OK"
                    try {
                        reqRecv = new Date();
                        JSONArray ja = new JSONArray(new String(response));
                        //JSONObject jo = ja.getJSONObject(0);

                        GraphView graph = (GraphView) findViewById(R.id.graph);

                        // clear graph if we are writing the primary series
                        if (lor == 0){
                            graph.removeAllSeries();
                        }

                        List<DataPoint> dataPoints = new ArrayList<>(ja.length()-1);

                        Date x = new Date();
                        JSONObject first = ja.getJSONObject(0);
                        JSONObject last = ja.getJSONObject(ja.length()-1);
                        long firsttime = getDate (first.getString("ts"));
                        long lasttime = getDate(last.getString("ts"));
                        String unit = first.getString("unit");

                        double hoursback = 0;

                        for(int i=0; i< ja.length() ; i++){
                            JSONObject jo = ja.getJSONObject(ja.length()-i-1);
                            v = jo.getDouble("v");
                            ts = jo.getString("ts");
                            //unit = jo.getString("unit");
                            //device_type = jo.getString("device_type");

                            hoursback = (-lasttime + getDate(ts)) / (60000 * 60.0) ;

                            dataPoints.add(new DataPoint(hoursback,v));
                        }

                        LineGraphSeries<DataPoint> ser = new LineGraphSeries<>(dataPoints.toArray(new
                                DataPoint[dataPoints.size()]));

                        graph.setTitle("<<< old  8 hours scrollable  new >>>>");
                        graph.getGridLabelRenderer().setNumHorizontalLabels(4);
                        graph.getGridLabelRenderer().setNumVerticalLabels(5);

                        graph.getGridLabelRenderer().setVerticalAxisTitle("Temp " + unit);
                        graph.getGridLabelRenderer().setHorizontalAxisTitle(MMMddhhmmtime(last.getString("ts"))  + " + hours");

                        //graph.getViewport().setScalable(true);
                        graph.getViewport().setScrollable(true);        // scroll X axis to see 24 hours in 8 hour window
                        //graph.getViewport().setScalableY(true);
                        //graph.getViewport().setScrollableY(true);

                        graph.getViewport().setYAxisBoundsManual(true);
                        graph.getViewport().setMinY(0);
                        graph.getViewport().setMaxY(80);

                        graph.getViewport().setXAxisBoundsManual(true);
                        graph.getViewport().setMinX(hoursback - 8); // hoursback should contain highest value in series, about 24
                        graph.getViewport().setMaxX(hoursback);     // display 8 hours originally at recent 8 hours

                        if (lor == 0) {
                            graph.addSeries(ser);
                        } else {
                            graph.getSecondScale().addSeries(ser);
                            graph.getSecondScale().setMinY(17);
                            graph.getSecondScale().setMaxY(23);
                            ser.setColor(Color.RED);
                            graph.getGridLabelRenderer().setVerticalLabelsSecondScaleColor(Color.RED);
                        }

                        String xxx = String.valueOf(ja.length()) + " points, query=" + String.valueOf(reqRecv.getTime()-reqSend.getTime()) + "ms";
                        AddtoBigBox(xxx);

                    } catch (Exception e) {
                        UpdateStatus(0, e.getMessage());
                    }
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                    reqRecv = new Date();
                    Pending = 0;
                    UpdateStatus(statusCode, e.getMessage());
                }
            });
        }

        public void write_it(){

            long old = howold(this.ts);
            String xxx = String.valueOf(this.v) + " " + this.unit + "  " + hmmssatime(this.ts) + ", -" + String.valueOf(old) + "s";

            tv.setText(xxx);
            AddtoBigBox(xxx + ", query=" + String.valueOf(reqRecv.getTime()-reqSend.getTime()) + "ms");
        }


        // all the date and string handling
        // everything here in Mountain Standard Time


        public SimpleDateFormat longDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        public SimpleDateFormat hmmssaDateFormat = new SimpleDateFormat("h:mm:ss a" );
        public SimpleDateFormat MMMddhhmmDateFormat = new SimpleDateFormat("MMMdd hh:mm aa" );

        public long getDate(String ts) {
            Date parsedate = new Date();
            try {
                parsedate = longDateFormat.parse(ts);
            } catch (ParseException ex) {
            }
            // convert to mountain time
            return parsedate.getTime();
        }

        public String MMMddhhmmtime(String ts) {
            Date parsedate = new Date();
            try {
                parsedate = longDateFormat.parse(ts);
            } catch (ParseException ex) {
            }
            // convert to mountain time -7
            return MMMddhhmmDateFormat.format(new Date(parsedate.getTime() -7 * 3600000 ));
        }
        public String hmmssatime(String ts) {
            Date parsedate = new Date();
            try {
                parsedate = longDateFormat.parse(ts);
            } catch (ParseException ex) {
            }
            // convert to mountain time
            return hmmssaDateFormat.format(new Date(parsedate.getTime() -7 * 3600000 ));
        }

        public long howold(String ts) {
            Date parsedate = new Date();
            Date now = new Date();

            try {
                parsedate = longDateFormat.parse(ts);
            } catch (ParseException ex) {
            }

            Date sampletime = new Date(parsedate.getTime() - 7 * 3600000 );
            return (now.getTime() - sampletime.getTime()) / 1000;
        }

    };

    // Beginning of the local variables of SimpleActivity

    public CayDataPoint currentTemp;
    public CayDataPoint currentCycle;
    public CayDataPoint currentStatus;
    public CayDataPoint currentTemp2;


    public String authToken = "";           // from cayenne
    public String BigString = "";           // contents of the BigBox for errors and staus


    public void UpdateStatus (Integer statusCode, String str){
        AddtoBigBox("(" + String.valueOf(statusCode) + ") " + str);
    }

    public void AddtoBigBox(String what){
        BigString = "=> " + what + "\r\n" + BigString ;
        TextView tvResult2 = (TextView) findViewById(R.id.BigBox);
        tvResult2.setText(BigString);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple);

        currentTemp = new CayDataPoint(deviceID, sensorIDtemp, (TextView) findViewById(R.id.newDataTemp));
        currentTemp2 = new CayDataPoint(deviceID2, sensorIDtemp2, (TextView) findViewById(R.id.newDataTemp2));

        currentCycle = new CayDataPoint(deviceID, sensorIDcycles, (TextView) findViewById(R.id.newDataCycle));
        currentStatus = new CayDataPoint(deviceID, sensorIDstatus, (TextView) findViewById(R.id.newDataStatus));

        getTime();
    }

    public void timeButtonClicked(View v) {
        getTime();

    }

    public void cayenneauthButtonClicked(View v) {
        getCayenneAuth();
    }

    public void cayenneButtonAllClicked(View v) {
        AddtoBigBox(" ... Update All");
        currentTemp.update();
        currentTemp2.update();

        currentStatus.update();
        currentCycle.update();

        currentTemp.updategraph(0);
        currentTemp2.updategraph(1);

        // Note:  unlikely internet will have updated values by the time this code runs!

        TextView tv = findViewById(R.id.newDataTemp);
        if (currentTemp.v < 20) {
            tv.setTextColor(Color.BLUE);
        } else if (currentTemp.v < 35) {
            tv.setTextColor(Color.YELLOW);
        } else {
            tv.setTextColor(Color.RED);
        }
    }

    public void cayenneButtonTempClicked(View v) {
        AddtoBigBox(" ... Update Temp");

        currentTemp.update();
        currentTemp2.update();

        TextView tv = findViewById(R.id.newDataTemp);
        if (currentTemp.v < 20) {
            tv.setTextColor(Color.BLUE);
        } else if (currentTemp.v < 35) {
            tv.setTextColor(Color.YELLOW);
        } else {
            tv.setTextColor(Color.RED);
        }
    }


    public void getTime() {

        // Test basic internet functionality

        AsyncHttpClient client = new AsyncHttpClient();

        client.get("https://postman-echo.com/time/now", new AsyncHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                // called when response HTTP status is "200 OK"
                UpdateStatus(statusCode,new String(response));

                // set the global variable for authorization, and turn on the button

                Button button = (Button) findViewById(R.id.btnGetAuth);
                button.setVisibility(View.VISIBLE);

                if (authToken == "") {
                    getCayenneAuth();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                // called when response HTTP status is "4XX" (eg. 401, 403, 404)
                UpdateStatus(statusCode, e.getMessage());
            }
        });
    }

    public void getCayenneAuth() {

        // get auth number from cayenne based on email and password

        authToken = "";

        AsyncHttpClient getCayenne = new AsyncHttpClient();
        RequestParams params = new RequestParams();
        params.put("grant_type", "password");
        params.put("email", cayenneemail);
        params.put("password", cayennepassword);

        getCayenne.post("https://auth.mydevices.com/oauth/token", params, new AsyncHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                // called when response HTTP status is "200 OK"
                UpdateStatus(statusCode,new String(response));

                try {
                    JSONObject jo = new JSONObject(new String(response));

                    authToken = jo.getString("access_token");

                    // set the global variable for authorization, and turn on the button

                    Button button2a = (Button) findViewById(R.id.btnGetALL);
                    button2a.setVisibility(View.VISIBLE);

                    // could update your sensors here

                } catch (Exception e) {
                    UpdateStatus(0, e.getMessage());
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                // called when response HTTP status is "4XX" (eg. 401, 403, 404)

                UpdateStatus(statusCode, e.getMessage());
            }

        });
    }
}

