# chitchat


![chitchat](./mockup.png)


chitchat is a location based board for people to post their thoughts and read others’.
All posts are filtered based on the user's current location (50km radius).


## User Interactions
Upon sign-in or sign-up, the message board screen is shown. This board contains messages that are 
filtered based on the user's current location. The user has a choice of upvoting or downvoting 
the posts (a user can only upvote or downvote a post once). A user can reply to others' posts: if the user clicks a post, they are taken to 
the replies screen. The replies screen shows all the replies attached to the post. A user can then
add their own reply to the post or upvote/downvote other replies.

Swipe down on the feed to refresh.

## Backend
* Uses Firebase and Geofire
* Firebase 
    * stores the individual posts, votes, replies, and locations
    * handles user signup/signin
    * database is structured as follows:
    
```

|
|------- items
|           |-- postKey1
|           |    |-- authorKey
|           |    |-- selfKey
|           |    |-- textContent
|           |    |-- votes
|           |
|           |-- postKey2
|                |-- authorKey
|                |-- selfKey
|                |-- textContent
|                |-- votes
|
|
|------- itemsLocation
|           |-- postKey1
|           |    |-- geofireLocation
|           |
|           |-- postKey2
|                |-- geofireLocation
|
|
|------- replies
            |-- postKey1
            |    |-- replyKey1
            |    |      |-- authorKey
            |    |      |-- selfKey
            |    |      |-- textContent
            |    |      |-- votes
            |    |
            |    |-- replyKey2
            |           |-- authorKey
            |           |-- selfKey
            |           |-- textContent
            |           |-- votes
            |
            |-- postKey2
                 |-- replyKey1
                        |-- authorKey
                        |-- selfKey
                        |-- textContent
                        |-- votes
   
```
* Geofire 
    * Does location-based querie by getting the user's current location and then querying for all
    the posts within a certain radius (in this case, 50 km). These are the posts a certain user sees.
