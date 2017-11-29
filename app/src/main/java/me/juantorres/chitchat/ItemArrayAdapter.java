package me.juantorres.chitchat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;


public class ItemArrayAdapter extends ArrayAdapter<Item> {
    private static String debugTag = "HELLO";
    final DatabaseReference mDatabase;
    final DatabaseReference repliesDB;
    SharedPreferences sharedPreferences;
    public Boolean onPostClickEnabled = true;
    private String upperPostId;

    public ItemArrayAdapter(Context context, ArrayList<Item> items, String upperPostId) {
        super(context, R.layout.item_posting, items);
        Log.d(debugTag, items.toString());
        mDatabase = FirebaseDatabase.getInstance().getReference().child("items");

        this.upperPostId = upperPostId;
        repliesDB = FirebaseDatabase.getInstance().getReference().child("replies");

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @NonNull
    @Override
    public View getView(final int position, @Nullable final View convertView, @NonNull final ViewGroup parent) {
        View view = convertView;

        if (view == null) {
            LayoutInflater layoutInflater = LayoutInflater.from(getContext());
            view = layoutInflater.inflate(R.layout.item_posting, null);
        }
        final Item item = getItem(position);
        final String sharedPreferencesKey = String.format(Locale.US, "KEY_%s", item.getId());
        final SharedPreferences.Editor editor = sharedPreferences.edit();

        final DatabaseReference childDatabaseRef = mDatabase.child(item.getId());


        if (item != null) {
            // SET INITIAL VIEW
            String score = String.valueOf(item.getVotes());
            String title = item.getTitle();


            if (item.getTitle() != ((TextView) view.findViewById(R.id.posting_text)).getText()) {
                Log.d(debugTag, "ITEM.title " + item.getTitle());
                LayoutInflater layoutInflater = LayoutInflater.from(getContext());
                view = layoutInflater.inflate(R.layout.item_posting, null);
            }

            final TextView finalContentTextView = view.findViewById(R.id.posting_text);
            final TextView finalScoreTextView = view.findViewById(R.id.post_score);
            final ImageButton finalUpvoteBtn = view.findViewById(R.id.upvote_button);
            final ImageButton finalDownvoteBtn = view.findViewById(R.id.downvote_button);

            finalContentTextView.setText(title);
            finalScoreTextView.setText(score);
            // Set initial state of buttons from info about the posts user previously voted on
            String prevStatus = sharedPreferences.getString(sharedPreferencesKey, "NULL");
            if (prevStatus.equals("UP")) {
                updateVoteButtons(finalUpvoteBtn, finalDownvoteBtn, VoteAction.UP);
            } else if (prevStatus.equals("DOWN")) {
                updateVoteButtons(finalUpvoteBtn, finalDownvoteBtn, VoteAction.DOWN);
            }

            final ValueEventListener updateValueListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    Item newItem = dataSnapshot.getValue(Item.class);
                    Log.d(debugTag, "Item's votes: " + item.getVotes());
                    if (newItem.getVotes() != item.getVotes()) {
                        Log.d(debugTag, "they're different");
                        item.setVotes(newItem.getVotes());  // only interested in updating the votes
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                }
            };

            // LISTENERS
            finalUpvoteBtn.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // when upperPostId is not null, we are in replies screen
                            if (upperPostId == null) {
                                Query query = mDatabase.child(item.getId()).limitToFirst(Integer.MAX_VALUE);
                                query.addValueEventListener(updateValueListener);
                            }

                            VoteAction action;
                            // Update database object
                            HashMap<String, Object> updates = new HashMap<>();

                            String prevInfo = sharedPreferences.getString(sharedPreferencesKey, "NULL");
                            if (!prevInfo.equals("NULL")) {
                                // up-voted -> un-doing
                                if (prevInfo.equals("UP")) {
                                    updates.put("votes", item.downVote());
                                    editor.remove(sharedPreferencesKey);
                                    action = VoteAction.NEUTRAL;
                                } else {  // downvoted -> now upvoted
                                    item.setVotes(item.getVotes() + 2);
                                    updates.put("votes", item.getVotes());
                                    editor.putString(sharedPreferencesKey, "UP");
                                    action = VoteAction.UP;
                                }
                            } else {  // neutral
                                updates.put("votes", item.upVote());
                                editor.putString(sharedPreferencesKey, "UP");
                                action = VoteAction.UP;
                            }
                            editor.commit();  // Changes should be saved immediately

                            if (upperPostId == null) {  // when upperPostId is not null, we are in replies screen
                                childDatabaseRef.updateChildren(updates);
                            } else {
                                repliesDB.child(upperPostId).child(item.getId()).updateChildren(updates);
                            }

                            // And update the view
                            finalScoreTextView.setText(String.valueOf(item.getVotes()));
                            updateVoteButtons(finalUpvoteBtn, finalDownvoteBtn, action);
                        }
                    }
            );

            finalDownvoteBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // UPDATE THE CURRENT ITEM'S LOCAL VERSION
                    if (upperPostId == null) { // when upperPostId is not null, we are in replies screen
                        Query query = mDatabase.child(item.getId()).limitToFirst(Integer.MAX_VALUE);
                        query.addValueEventListener(updateValueListener);
                    }

                    // UPDATE THE DATABASE
                    VoteAction action;
                    HashMap<String, Object> updates = new HashMap<>();
                    String prevInfo = sharedPreferences.getString(sharedPreferencesKey, "NULL");
                    if (!prevInfo.equals("NULL")) {         // --PREVIOUSLY VOTED--
                        if (prevInfo.equals("UP")) {        // Prev: upvoted -> downvoted
                            item.setVotes(item.getVotes() - 2);
                            updates.put("votes", item.getVotes());
                            editor.putString(sharedPreferencesKey, "DOWN");
                            action = VoteAction.DOWN;
                        } else {                            // Prev: downvoted -> un-doing
                            updates.put("votes", item.upVote());
                            editor.remove(sharedPreferencesKey);
                            action = VoteAction.NEUTRAL;
                        }
                    } else {                                // Prev: neutral
                        updates.put("votes", item.downVote());
                        editor.putString(sharedPreferencesKey, "DOWN");
                        action = VoteAction.DOWN;
                    }
                    editor.commit();  // Save the change in voting by the user
                    if (upperPostId == null) {  // when upperPostId is not null, we are in replies screen
                        childDatabaseRef.updateChildren(updates);
                    } else {
                        repliesDB.child(upperPostId).child(item.getId()).updateChildren(updates);
                    }

                    // AND UPDATE THE VIEW
                    finalScoreTextView.setText(String.valueOf(item.getVotes()));
                    updateVoteButtons(finalUpvoteBtn, finalDownvoteBtn, action);
                }
            });

            finalContentTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onPostClick(position);
                }
            });


        }
        return view;
    }

    /**
     * Update the colors for the upvote and downvote buttons
     */
    void updateVoteButtons(ImageButton upvoteBtn, ImageButton downvoteBtn, VoteAction voteAction) {
        int upvoteBtnColorRes;
        int downvoteBtnColorRes;

        if (voteAction == VoteAction.UP) {
            upvoteBtnColorRes = R.color.upvoteColor;
            downvoteBtnColorRes = R.color.novoteColor;
        } else if (voteAction == VoteAction.DOWN) {
            upvoteBtnColorRes = R.color.novoteColor;
            downvoteBtnColorRes = R.color.downvoteColor;
        } else { // neutral
            upvoteBtnColorRes = R.color.novoteColor;
            downvoteBtnColorRes = R.color.novoteColor;
        }
        upvoteBtn.setColorFilter(getContext().getColor(upvoteBtnColorRes));
        downvoteBtn.setColorFilter(getContext().getColor(downvoteBtnColorRes));
    }

    enum VoteAction {UP, DOWN, NEUTRAL}

    // when the post is clicked, user is taken to the replies screen
    // onPostclickEnabled is a bool to differentiate between main message board and the
    // replies board.
    void onPostClick(int position) {
        if (onPostClickEnabled) {
            Intent intent = new Intent(getContext(), ReplyActivity.class);
            intent.putExtra("PostId", getItem(position).getId());
            getContext().startActivity(intent);
        }
    }

    // on data change, sort the arraylist and notify that the contents changed
    @Override
    public void notifyDataSetChanged() {
        this.setNotifyOnChange(false);

        this.sort(new Comparator<Item>() {
            @Override
            public int compare(Item lhs, Item rhs) {
                return rhs.getVotes() - lhs.getVotes();
            }
        });

        this.setNotifyOnChange(true);
        super.notifyDataSetChanged();
    }
}
