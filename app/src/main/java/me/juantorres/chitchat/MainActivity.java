/*
 * Jun Bing, Juan Torres
 * Based on code by Varun Mishra.
 *
 * November 2017
 */
package me.juantorres.chitchat;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;
    private DatabaseReference mDatabase;
    SharedPreferences sharedPrefereces;
    private String mUserId;
    private FusedLocationProviderClient mFusedLocationClient;
    private String debugTag = "M_ACT";

    final double radius = 50; // 50km


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Auth and Database Reference
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        // And GeoFire
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        final GeoFire geoFire = new GeoFire(mDatabase.child("items_location"));
        // And other things
        sharedPrefereces = PreferenceManager.getDefaultSharedPreferences(this);

        if (mFirebaseUser == null) {
            // Not logged in, launch the Log In activity
            loadLogInView();
        } else {
            mUserId = mFirebaseUser.getUid();

            // Set up ListView: DO NOT USE ARRAYS; THEY ARE NOT DYNAMIC -> can't add items in
            // real time.
            final ArrayList<Item> itemArr = new ArrayList<>();
            final ListView listView = (ListView) findViewById(R.id.listView);
            final ItemArrayAdapter mAdapter = new ItemArrayAdapter(this, itemArr, null);
            listView.setAdapter(mAdapter);

            // Add items via the Button and EditText at the bottom of the view.
            final EditText entryText = (EditText) findViewById(R.id.todoText);
            final Button submitEntryButton = (Button) findViewById(R.id.addButton);
            final ArrayList<Item> arrList = new ArrayList<Item>();

            submitEntryButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (!entryText.getText().toString().equals("")) {
                        if (ActivityCompat.checkSelfPermission(MainActivity.this,
                                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                                &&
                                ActivityCompat.checkSelfPermission(MainActivity.this,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                                ) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                                    100);
                            return;
                        }

                        mFusedLocationClient.getLastLocation().addOnSuccessListener(
                                MainActivity.this, new OnSuccessListener<Location>() {
                                    @Override
                                    public void onSuccess(Location location) {
                                        // Got last known location. In some rare situations this can be null.
                                        if (location != null) {
                                            // Logic to handle location object
                                            String itemId = mDatabase.child("items").push().getKey();
                                            Item item = new Item(itemId, mUserId, entryText.getText().toString());
                                            mDatabase.child("items").child(itemId).setValue(item);
                                            geoFire.setLocation(itemId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                                            entryText.setText("");
                                            mAdapter.add(item);
                                        }
                                    }
                                });

                        InputMethodManager inputManager = (InputMethodManager)
                                getSystemService(MainActivity.INPUT_METHOD_SERVICE);

                        if (null != inputManager) {
                            inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                                    InputMethodManager.HIDE_NOT_ALWAYS);
                        }
                    }


                }
            });
            // Actually load the posts
            loadPosts(mAdapter);

            // Reload all the posts by swiping down
            final SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
            swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    Log.d(debugTag, "swiped!\n");
                    loadPosts(mAdapter);
                    swipeRefreshLayout.setRefreshing(false);
                }

            });

        }
    }

    private void loadPosts(final ItemArrayAdapter mAdapter) {
        final GeoFire geoFire = new GeoFire(mDatabase.child("items_location"));
        mAdapter.clear();

        // Check permissions
        if (ActivityCompat.checkSelfPermission(MainActivity.this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                &&
                ActivityCompat.checkSelfPermission(MainActivity.this,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    100);

            return;
        }

        // Get the posts that are nearby. Each post is associated with one location
        // when first created; we use it to filter them.
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(MainActivity.this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {

                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            // Logic to handle location object
                            final GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(location.getLatitude(), location.getLongitude()), radius);
                            Log.d("loc", "loc" + location);

                            geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
                                @Override
                                public void onKeyEntered(String key, GeoLocation location) {
                                    // For each post in the database, this gets called once.
                                    // For posts in the db before, this is when the view is loaded.
                                    // For new posts, this is triggered when they hit the db.
                                    mDatabase.child("items").child(key).addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot dataSnapshot) {
                                            if (dataSnapshot.child("title").getValue() != null) {
                                                Item item = dataSnapshot.getValue(Item.class);
                                                mAdapter.add(item);
                                            }
                                        }

                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {
                                        }
                                    });
                                }

                                @Override
                                public void onKeyMoved(String key, GeoLocation location) {
                                    geoQuery.removeAllListeners();
                                }

                                @Override
                                public void onGeoQueryReady() {
                                    geoQuery.removeAllListeners();
                                    mAdapter.notifyDataSetChanged();
                                }

                                @Override
                                public void onGeoQueryError(DatabaseError error) {
                                    geoQuery.removeAllListeners();
                                }

                                @Override
                                public void onKeyExited(String key) {
                                }
                            });
                        }
                    }
                });


    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void loadLogInView() {
        Intent intent = new Intent(this, LogInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_logout) {
            mFirebaseAuth.signOut();
            loadLogInView();
        }
        return super.onOptionsItemSelected(item);
    }
}
