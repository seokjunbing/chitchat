package me.juantorres.chitchat;

import android.content.Intent;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class ReplyActivity extends AppCompatActivity {
    ArrayList<Item> itemArr = new ArrayList<>();
    private FirebaseUser mFirebaseUser;
    private DatabaseReference mDatabase;
    private FirebaseAuth mFirebaseAuth;
    private String mUserId;

    // login activity in case the user isn't logged in for some reason
    private void loadLogInView() {
        Intent intent = new Intent(this, LogInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reply);

        // the post key that replies are going to be attached to is passed along with the intent
        Intent intent = getIntent();
        final String postId = intent.getStringExtra("PostId");

        // DB initialization
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        if (mFirebaseUser == null) {
            // Not logged in, launch the Log In activity
            loadLogInView();
        } else {
            mUserId = mFirebaseUser.getUid();
            ArrayList<Item> replyArr = new ArrayList<>();
            ArrayList<Item> upper_post = new ArrayList<>();

            // Set up ListView and adaptor for the main thread
            final ListView listView_upper = (ListView) findViewById(R.id.listView_upper);
            final ItemArrayAdapter mAdapter_upper = new ItemArrayAdapter(this, upper_post, null);
            mAdapter_upper.onPostClickEnabled = false;
            listView_upper.setAdapter(mAdapter_upper);

            // Set up ListView and adaptor for replies
            final ListView listView_lower = (ListView) findViewById(R.id.lower).findViewById(R.id.listView);
            final ItemArrayAdapter mAdapter = new ItemArrayAdapter(this, replyArr, postId);
            mAdapter.onPostClickEnabled = false;
            listView_lower.setAdapter(mAdapter);

            final Button submitEntryButton = (Button) findViewById(R.id.addButton);
            final EditText entryText = (EditText) findViewById(R.id.todoText);

            // Populate the main post that the replies are attaching to
            populateUpperPost(postId, mAdapter_upper);
            // Populate the replies listview
            populateReplies(postId, mAdapter);


            // submit button has a listener
            submitEntryButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if(!entryText.getText().toString().equals("")) {
                        String itemId = mDatabase.child("replies").child(postId).push().getKey();
                        Item item = new Item(itemId, mUserId, entryText.getText().toString());
                        mAdapter.add(item);
                        mDatabase.child("replies").child(postId).child(itemId).setValue(item);
                        entryText.setText("");

                        // hide the keyboard upon submitting a post
                        InputMethodManager inputManager = (InputMethodManager)
                                getSystemService(ReplyActivity.INPUT_METHOD_SERVICE);

                        if (null != inputManager) {
                            inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                                    InputMethodManager.HIDE_NOT_ALWAYS);
                        }
                    }
                }
            });

            // swipe down to refresh the view
            final SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
            swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    // refresh the listviews
                    populateUpperPost(postId, mAdapter_upper);
                    populateReplies(postId, mAdapter);
                    // stop the spinning icon
                    swipeRefreshLayout.setRefreshing(false);
                }

            });

        }
    }

    // Populate the main post that the replies are attaching to
    public void populateUpperPost(String postId, final ItemArrayAdapter mAdapter_upper) {
        mAdapter_upper.clear();
        mDatabase.child("items").child(postId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            // populate the main post
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.child("title").getValue() != null) {
                    Item item = dataSnapshot.getValue(Item.class);
                    mAdapter_upper.add(item);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    // Populate the replies to the main post
    public void populateReplies(String postId, final ItemArrayAdapter mAdapter) {
        // clear the adaptor
        // needed because swipe-refresh also calls this function
        mAdapter.clear();
        mDatabase.child("replies").child(postId).orderByChild("votes").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // populate the replies view
                for (DataSnapshot snapshot: dataSnapshot.getChildren()) {
                    if (snapshot.child("title").getValue() != null) {
                        Item item = snapshot.getValue(Item.class);
                        mAdapter.add(item);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }
}
