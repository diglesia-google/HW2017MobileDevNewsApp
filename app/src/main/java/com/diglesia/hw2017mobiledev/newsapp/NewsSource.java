package com.diglesia.hw2017mobiledev.newsapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.util.LruCache;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class NewsSource {
    private static final int IMAGE_CACHE_COUNT = 100;

    public interface ArticleListener {
        void onArticlesReceived(List<? extends Article> articles);
    }

    public interface SourceListener {
        void onSourcesReceived(List<Source> sourceList);
    }

    private static NewsSource sNewsSource;

    private Context mContext;
    private RequestQueue mRequestQueue;
    private ImageLoader mImageLoader;

    public static NewsSource get(Context context) {
        if (sNewsSource == null) {
            sNewsSource = new NewsSource(context);
        }
        return sNewsSource;
    }

    private NewsSource(Context context) {
        mContext = context;
        mRequestQueue = Volley.newRequestQueue(context);
        mImageLoader = new ImageLoader(mRequestQueue, new ImageLoader.ImageCache() {
            private final LruCache<String, Bitmap> mCache = new LruCache<>(IMAGE_CACHE_COUNT);
            public void putBitmap(String url, Bitmap bitmap) {
                mCache.put(url, bitmap);
            }
            public Bitmap getBitmap(String url) {
                return mCache.get(url);
            }
        });
    }

    public ImageLoader getImageLoader() {
        return mImageLoader;
    }

    // News API methods

    public void getSources(SourceListener sourceListener) {
        final SourceListener sourceListenerInternal = sourceListener;
        String url = "https://newsapi.org/v1/sources?language=en";

        JsonObjectRequest jsonObjRequest = new JsonObjectRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        List<Source> sourceList = new ArrayList<>();
                        try {
                            JSONArray sourceObjs = response.getJSONArray("sources");
                            for (int i = 0; i < sourceObjs.length(); i++) {
                                Source source = new Source(sourceObjs.getJSONObject(i));
                                sourceList.add(source);
                            }
                            sourceListenerInternal.onSourcesReceived(sourceList);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            sourceListenerInternal.onSourcesReceived(null);
                            Toast.makeText(mContext, "Could not unpack sources.", Toast.LENGTH_SHORT);
                        }

                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        sourceListenerInternal.onSourcesReceived(null);
                        Toast.makeText(mContext, "Could not get sources.", Toast.LENGTH_SHORT);
                    }
                });

        mRequestQueue.add(jsonObjRequest);
    }

    public void getArticles(String sourceId, final ArticleListener articleListener) {
        String apiKey = mContext.getString(R.string.news_api_key);
        String url = "https://newsapi.org/v1/articles?source=" + sourceId + "&apiKey=" + apiKey;
        JsonObjectRequest jsonObjRequest = new JsonObjectRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        List<Article> articleList = new ArrayList<>();
                        try {
                            JSONArray articleObjArray = response.getJSONArray("articles");
                            for (int i = 0; i< articleObjArray.length(); i++) {
                                JSONObject articleObj = articleObjArray.getJSONObject(i);
                                Article article = new Article(articleObj);
                                articleList.add(article);
                            }
                            // End of loop, list of ARTICLES ready to display
                            articleListener.onArticlesReceived(articleList);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            articleListener.onArticlesReceived(null);
                            Toast.makeText(mContext, "Could not unpack articles.", Toast.LENGTH_SHORT);
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.i("DEI", error.toString());
                        articleListener.onArticlesReceived(null);
                        Toast.makeText(mContext, "Could not unpack articles.", Toast.LENGTH_SHORT);
                    }
                });

        mRequestQueue.add(jsonObjRequest);
    }

    //====FIREBASE METHODS

    public void getSharedArticles(final ArticleListener articleListener) {
        DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference();
        DatabaseReference articlesRef = databaseRef.child("articles");
        articlesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<SharedArticle> sharedArticles = new ArrayList<>();
                Iterable<DataSnapshot> iter = dataSnapshot.getChildren();
                for (DataSnapshot articleSnapshot : iter) {
                    SharedArticle sharedArticle = new SharedArticle(articleSnapshot);
                    sharedArticles.add(sharedArticle);
                }
                articleListener.onArticlesReceived(sharedArticles);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public void getMySharedArticles(String userId, final ArticleListener articleListener) {
        DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference();
        DatabaseReference articlesRef = databaseRef.child("articles");
        Query myArticlesQuery = articlesRef.orderByChild("userId").equalTo(userId);

        myArticlesQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<SharedArticle> sharedArticles = new ArrayList<>();
                Iterable<DataSnapshot> iter = dataSnapshot.getChildren();
                for (DataSnapshot articleSnapshot : iter) {
                    SharedArticle sharedArticle = new SharedArticle(articleSnapshot);
                    sharedArticles.add(sharedArticle);
                }
                articleListener.onArticlesReceived(sharedArticles);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public void shareArticle(SharedArticle sharedArticle) {
        DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference();
        DatabaseReference articlesRef = databaseRef.child("articles");
        DatabaseReference newArticleRef = articlesRef.push();

        Map<String,String> articleValMap = new HashMap<>();
        articleValMap.put("title", sharedArticle.getTitle());
        articleValMap.put("url", sharedArticle.getArticleUrl());
        articleValMap.put("imgUrl", sharedArticle.getImageUrl());
        articleValMap.put("userId", sharedArticle.getUserId());
        articleValMap.put("userComment", sharedArticle.getUserComment());
        articleValMap.put("userName", sharedArticle.getUserName());

        newArticleRef.setValue(articleValMap, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                if (databaseError == null) {
                    Toast.makeText(mContext, "share complete!", Toast.LENGTH_SHORT).show();
                } else {

                }
            }
        });

    }

    public void unshareArticle(SharedArticle sharedArticle) {
        DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference();
        DatabaseReference articlesRef = databaseRef.child("articles");
        DatabaseReference articleRef = articlesRef.child(sharedArticle.getFirebaseKey());
        articleRef.removeValue();
    }


}
