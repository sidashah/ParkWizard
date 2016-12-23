package edu.columbia.coms6998.parkwizard;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 * Created by Siddharth on 12/22/2016.
 */

public class UpdateParkingFragment extends Fragment {

    ListView listView;
    Button updateBtn;
    Location userLocation;
    final int MY_PERMISSIONS_REQUEST_ACCESS_LOCATION = 201;
    ArrayList<Info> parkingLocationInfos = new ArrayList<>();
    boolean loaded = false;

    int selectedPosition = -1;

    class Info{
        String id;
        String name;
        int available;
    }

    @Override
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        View rootView = layoutInflater.inflate(R.layout.fragment_updateparking, viewGroup, false);
        listView = (ListView) rootView.findViewById(R.id.lvParkingLocations);
        updateBtn = (Button) rootView.findViewById(R.id.btnUpdate);

        // specify an adapter (see also next example)
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        getUserLocation();
        if(!loaded) {
            if (userLocation != null) {
                loadParkingLocations();
            } else {
                Toast.makeText(getActivity(), "We need your location", Toast.LENGTH_SHORT).show();
            }
            loaded=true;
        }
    }

    void getUserLocation(){
        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_LOCATION);
        } else {
            LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
            Criteria criteria = new Criteria();
            userLocation = locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, false));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                            && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    } else {
                        LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
                        Criteria criteria = new Criteria();
                        userLocation = locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, false));
                    }
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }


    void loadParkingLocations(){

        new AsyncTask<Void, Void, String>(){
            @Override
            protected String doInBackground(Void... voids) {

                StringBuffer response = new StringBuffer();

                try {
                    String searchpageurl = getString(R.string.getupdatelocationsurl);

                    // Read user id from the config file
                    SharedPreferences sp = getActivity().getSharedPreferences("USER_PROFILE", Context.MODE_PRIVATE);
                    String userid = sp.getString("userid", "");

                    //call to backend
                    String parameters = "?id=" + userid + "&lat=" + userLocation.getLatitude() + "&lon=" + userLocation.getLongitude();
                    URL url = new URL(searchpageurl + parameters);
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();

                    // optional default is GET
                    con.setRequestMethod("GET");

                    int responseCode = con.getResponseCode();

                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(con.getInputStream()));
                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return response.toString();
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                //update recyclerview
                Log.d("JSON",s);
                try {
                    JSONObject jsonObject = new JSONObject(s);
                    JSONArray jsonArray = jsonObject.getJSONArray("parkings");

                    if(jsonArray.length() == 0){
                       Toast.makeText(getActivity().getApplicationContext(), jsonObject.getString("message"),Toast.LENGTH_SHORT).show();
                    }

                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject outerjo = jsonArray.getJSONObject(i);
                        JSONObject innerjo = outerjo.getJSONObject("location");
                        //LatLng latLng = new LatLng(innerjo.getDouble("lat"), innerjo.getDouble("lon"));
                        String  id = outerjo.getString("locid");
                        String name = outerjo.getString("name");

                        UpdateParkingFragment.Info information = new Info();
                        information.name = name;
                        information.id = id;
                        parkingLocationInfos.add(information);
                    }

                    final CustomAdapter adapter = new CustomAdapter(getActivity(), R.layout.card_parking);
                    listView.setAdapter(adapter);
                    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            selectedPosition = i;
                            adapter.notifyDataSetChanged();
                        }
                    });

                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    public class CustomAdapter extends BaseAdapter {
        // store the context (as an inflated layout)
        private LayoutInflater inflater;
        // store the resource (typically list_item.xml)
        private int resource;

        public CustomAdapter(Context context, int resource) {
            this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            this.resource = resource;
        }

        public int getCount() {
            return parkingLocationInfos.size();
        }

        public Object getItem(int position) {
            return parkingLocationInfos.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            // reuse a given view, or inflate a new one from the xml
            View view;

            if (convertView == null) {
                view = this.inflater.inflate(resource, parent, false);
            } else {
                view = convertView;
            }

            return this.bindData(view, position);
        }

        public View bindData(View view, int position) {
            // make sure it's worth drawing the view
            if (parkingLocationInfos.get(position) == null) {
                return view;
            }
            Info item = parkingLocationInfos.get(position);
            TextView tv = (TextView)view.findViewById(R.id.tv_parking);
            tv.setText(item.name);
            view.setClickable(false);
            if(position == selectedPosition){
                view.setBackgroundColor(Color.BLUE);
            } else{
                view.setBackgroundColor(Color.WHITE);
            }

            return view;
        }
    }


}