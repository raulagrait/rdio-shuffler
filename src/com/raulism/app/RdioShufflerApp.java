package com.raulism.app;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.rdio.android.api.Rdio;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class RdioShufflerApp {
	
	private static RdioShufflerApp _instance;
	private Context _context;
	private Activity _parentActivity;
	
	private static Rdio _rdio;
	private List<Artist> _artistList;
	private Queue<Track> _trackQueue;
	
	private static String _accessToken = null;
	private static String _accessTokenSecret = null;
	
	private static final String APP_KEY = "your_app_key_here";
	private static final String APP_SECRET = "your_app_secret_here";
	private static final String PREF_ACCESSTOKEN = "prefs.accesstoken";
	private static final String PREF_ACCESSTOKENSECRET = "prefs.accesstokensecret";
	
	protected RdioShufflerApp(Context context, Activity parentActivity)
    {
        _context = context;
        _parentActivity = parentActivity;
    }

	public static void tryInitialize(Activity parentActivity)
    {
        try
        {
            if (_instance == null)
            {
                Context applicationContext = parentActivity.getApplicationContext();
                _instance = new RdioShufflerApp(applicationContext, parentActivity);
                _instance.initialize();
            }
        }
        catch (Exception e)
        {
        }
    }
	
	public static RdioShufflerApp getInstance()
    {
        return _instance;
    }

    public Context getContext()
    {
        return _context;
    }
	
	private void initialize()
    {
		initializeRdio();
		initializeTrackQueue();
    }
	
	private void initializeRdio()
	{
		if (_rdio == null) {
			
        	SharedPreferences settings = _parentActivity.getPreferences(Activity.MODE_PRIVATE);
        	_accessToken = settings.getString(PREF_ACCESSTOKEN, null);
        	_accessTokenSecret = settings.getString(PREF_ACCESSTOKENSECRET, null);
        	
        	if (_accessToken == null || _accessTokenSecret == null)
        	{
        		_accessToken = _accessTokenSecret = null;
        	}
        	else
        	{
        		Log.d(RdioShufflerActivity.TAG, "Found cached credentials:");
				Log.d(RdioShufflerActivity.TAG, "Access token: " + _accessToken);
				Log.d(RdioShufflerActivity.TAG, "Access token secret: " + _accessTokenSecret);
        	}
        	
        	_rdio = new Rdio(APP_KEY, APP_SECRET, _accessToken, _accessTokenSecret, _parentActivity, (RdioShufflerActivity) _parentActivity);
        }
	}
	
	public Rdio getRdio()
	{
		return _rdio;
	}
	
	public void initializeTrackQueue()
	{
		_trackQueue = new LinkedList<Track>();
	}
	
	public void setArtistList(List<Artist> artistList)
	{
		if (artistList != null)
		{
			if (_artistList == null || (artistList.size() != _artistList.size()))
			{
				_artistList = artistList;
			}
		}
	}
	
	public List<Artist> getArtistList()
	{
		return _artistList;
	}
	
	public Queue<Track> getTrackQueue()
	{
		return _trackQueue;
	}
}
