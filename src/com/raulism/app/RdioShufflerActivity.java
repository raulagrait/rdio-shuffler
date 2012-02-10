package com.raulism.app;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import com.rdio.android.api.Rdio;
import com.rdio.android.api.RdioApiCallback;
import com.rdio.android.api.RdioListener;
import com.rdio.android.api.RdioSubscriptionType;
import com.rdio.android.api.services.RdioAuthorisationException;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class RdioShufflerActivity extends Activity implements RdioListener, OnClickListener
{
	public static final String TAG = "RdioShuffler";	
	private MediaPlayer _player;	
	
	private static String _collectionKey = null;
	
	// Request code used for startActivityForResult/onActivityResult
	private static final int REQUEST_AUTHORISE_APP = 100;
	private static final int REQUEST_ARTIST_RADIO = 101;

	private static final String PREF_ACCESSTOKEN = "prefs.accesstoken";
	private static final String PREF_ACCESSTOKENSECRET = "prefs.accesstokensecret";
	
	private ImageView _albumArt;
	private ImageView _playPause;
	private ImageView _artistListIcon;
	
	private TextView _trackName;
	private TextView _artistName;
	private TextView _albumName;
	
	private Track _currentTrack;
	private boolean _isPlaying;
	private boolean _isCurrentActivity;

	private SeekBar _trackPositionBar;
	private Handler _threadPositionHandler;
	private Thread _positionThread;
	private OnSeekBarChangeListener _seekBarChangeListener;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        RdioShufflerApp.tryInitialize(this);

        initializeControls();
        updateControls();
        
        initializePositionHandler();
    }
    
    @Override
	public void onDestroy()
    {
		Log.i(TAG, "Cleaning up..");

		// Make sure to call the cleanup method on the API object
		getRdio().cleanup();

		// If we allocated a player, then cleanup after it
		if (_player != null)
		{
			_player.reset();
			_player.release();
			_player = null;
		}

		super.onDestroy();
	}
    
    private void updateControls()
    {
    	updateTrackDisplay();
    	updatePlayPause();
    }
    
    /**
	 * Get the current user, and load their collection to start playback with.
	 * Requires authorization and the Rdio app to be installed.
	 */
	private void doSomething()
	{
		if (getRdio().getSubscriptionState() == RdioSubscriptionType.ANONYMOUS)
		{
			doSomethingWithoutApp();
			return;
		}
		
		Log.i(TAG, "Getting current user");
		showDialog(Utilities.DIALOG_GETTING_USER);
		
		// Get the current user so we can find out their user ID and get their collection key
		List<NameValuePair> args = new LinkedList<NameValuePair>();
		args.add(new BasicNameValuePair("extras", "followingCount,followerCount,username,displayName,subscriptionType,trialEndDate,actualSubscriptionType"));
		
		getRdio().apiCall("currentUser", args, new RdioApiCallback()
		{			
			@Override
			public void onApiSuccess(JSONObject result)
			{
				dismissDialog(Utilities.DIALOG_GETTING_USER);
				onGetCurrentUserSuccess(result);
			}

			@Override
			public void onApiFailure(String methodName, Exception e)
			{
				dismissDialog(Utilities.DIALOG_GETTING_USER);
				onGetCurrentUserFailure(e);						
			}
		});
	}
	
	private void onGetCurrentUserSuccess(JSONObject result)
	{
		try
		{
			result = result.getJSONObject("result");
			Log.i(TAG, result.toString(2));
			
			// c<userid> is the 'collection radio source' key
			_collectionKey = result.getString("key").replace('s', 'c');
			
			loadMoreTracks();
		}
		catch (Exception e)
		{
			Log.e(TAG, "Failed to handle JSONObject: ", e);
		}
	}
	
	private void onGetCurrentUserFailure(Exception e)
	{
		Log.e(TAG, "getCurrentUser failed. ", e);
		if (e instanceof RdioAuthorisationException)
		{
			doSomethingWithoutApp();
		}	
	}
	
	private void loadMoreTracks()
	{
		if (getRdio().getSubscriptionState() == RdioSubscriptionType.ANONYMOUS)
		{
			onOutOfTracks();
			return;
		}

		showDialog(Utilities.DIALOG_GETTING_COLLECTION);
		List<NameValuePair> args = new LinkedList<NameValuePair>();
		args.add(new BasicNameValuePair("keys", _collectionKey));
		args.add(new BasicNameValuePair("count", "50"));
		
		getRdio().apiCall("get", args, new RdioApiCallback()
		{			
			@Override
			public void onApiSuccess(JSONObject result)
			{
				onGetCollectionSuccess(result);
			}

			@Override
			public void onApiFailure(String methodName, Exception e)
			{
				dismissDialog(Utilities.DIALOG_GETTING_COLLECTION);
				Log.e(TAG, methodName + " failed: ", e);
			}
		});		
	}
	
	private void onGetCollectionSuccess(JSONObject result)
	{
		try
		{
			result = result.getJSONObject("result");
			result = result.getJSONObject(_collectionKey);
			
			List<Track> trackKeys = new LinkedList<Track>();
			JSONArray tracks = result.getJSONArray("tracks");
			for (int i = 0; i < tracks.length(); i++)
			{
				JSONObject trackObject = tracks.getJSONObject(i);
				Track track = Track.fromJson(trackObject);
				
				Log.d(TAG, "Found track: " + track.getKey() + " => " + track.getTrackName());
				trackKeys.add(track);				
			}

			if (trackKeys.size() > 1)
			{
				getTrackQueue().addAll(trackKeys);
			}

			dismissDialog(Utilities.DIALOG_GETTING_COLLECTION);
			
			// If we are currently active and we're not playing something, then load something up
			if (_isCurrentActivity && !isPlayingSomething())
			{
				next(true);
			}
		}
		catch (Exception e)
		{
			dismissDialog(Utilities.DIALOG_GETTING_COLLECTION);
			Log.e(TAG, "Failed to handle JSONObject: ", e);
		}
	}
	
	private boolean isPlayingSomething()
	{
		return (_player != null && _player.isPlaying());
	}

	private void onOutOfTracks()
	{
		Log.i(TAG, "Anonymous user! No more tracks to play.");

		// Notify the user we're out of tracks
		Toast.makeText(this, getString(R.string.no_more_tracks), Toast.LENGTH_LONG).show();

		// Then helpfully point them to the market to go install Rdio ;)
		Intent installRdioIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pname:com.rdio.android.ui"));
		installRdioIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(installRdioIntent);

		finish();
	}
	
	private void next(final boolean manualPlay)
	{
		stopPositionThread();
		
		if (_player != null)
		{
			_player.stop();
			_player.release();
			_player = null;
		}

		final Track track = getTrackQueue().poll();
		if (getTrackQueue().size() < 3)
		{
			Log.i(TAG, "Track queue depleted, loading more tracks");
			loadMoreTracks();
		}

		if (track == null)
		{
			Log.e(TAG, "Track is null! Size of queue: " + getTrackQueue().size());
			return;
		}
		
		// Load the next track in the background and prep the player (to start buffering)
		// Do this in a bkg thread so it doesn't block the main thread in .prepare()
		AsyncTask<Track, Void, Track> task = new AsyncTask<Track, Void, Track>()
		{
			@Override
			protected Track doInBackground(Track... params)
			{
				Track track = params[0];
				loadTrackInBackground(track, manualPlay);
				return track;
			}

			@Override
			protected void onPostExecute(Track track)
			{
				setIsPlaying(true);
			}
		};
		task.execute(track);
		
		// Fetch album art in the background and then update the UI on the main thread
		AsyncTask<Track, Void, Bitmap> artworkTask = new AsyncTask<Track, Void, Bitmap>() {

			@Override
			protected Bitmap doInBackground(Track... params)
			{
				Track track = params[0];				
				return loadAlbumArtInBackground(track);
			}
			
			@Override
			protected void onPostExecute(Bitmap artwork)
			{
				setAlbumArt(artwork);
			}			
		};
		artworkTask.execute(track);

		setCurrentTrack(track);
	}
	
	private void setIsPlaying(boolean isPlaying)
	{
		_isPlaying = isPlaying;
		updatePlayPause();
	}
	
	private void setCurrentTrack(Track track)
	{
		_currentTrack = track;
		updateTrackDisplay();
	}
	
	private void updateTrackDisplay()
	{
		int visibility = (_currentTrack == null) ? View.GONE : View.VISIBLE;	
		
		_trackName.setVisibility(visibility);
		_artistName.setVisibility(visibility);
		_albumName.setVisibility(visibility);
		
		if (_currentTrack != null)
		{
			_trackName.setText(_currentTrack.getTrackName());
			_artistName.setText(_currentTrack.getArtistName());				
			_albumName.setText(_currentTrack.getAlbumName());
		}
	}
	
	private void loadTrackInBackground(Track track, boolean manualPlay)
	{
		try
		{
			_player = getRdio().getPlayerForTrack(track.getKey(), null, manualPlay);
			_player.prepare();
			_player.setOnCompletionListener(new OnCompletionListener()
			{						
				@Override
				public void onCompletion(MediaPlayer mp)
				{
					next(false);					
				}
			});					
			
			_player.start();

			startPositionThread();
		}
		catch (Exception e)
		{
			Log.e("Test", "Exception " + e);
		}
	}
	
	private Bitmap loadAlbumArtInBackground(Track track)
	{
		Bitmap bm = null;
		try
		{
			String artworkUrl = track.getAlbumArt().replace("square-200", "square-600");
			Log.i(TAG, "Downloading album art: " + artworkUrl);			
			try
			{
				URL aURL = new URL(artworkUrl);
				URLConnection conn = aURL.openConnection();
				conn.connect();
				InputStream is = conn.getInputStream();
				BufferedInputStream bis = new BufferedInputStream(is);
				bm = BitmapFactory.decodeStream(bis);
				bis.close();
				is.close();
			}
			catch (IOException e)
			{
				Log.e(TAG, "Error getting bitmap", e);
			}
		}
		catch (Exception e)
		{
			Log.e(TAG, "Error downloading artwork", e);
			bm = null;
		}
		return bm;
	}
	
	private void setAlbumArt(Bitmap artwork)
	{
		if (artwork != null)
		{
			_albumArt.setImageBitmap(artwork);
		}
		else
		{
			_albumArt.setImageResource(R.drawable.blank_album_art);
		}
	}
	
	/**
	 * Get Rdio's site-wide heavy rotation and play 30s samples.
	 * Doesn't require auth or the Rdio app to be installed
	 */
	private void doSomethingWithoutApp()
	{
		
	}
	
	private void playPause()
	{
		if (_player != null)
		{
			boolean isPlaying = _player.isPlaying();
			if (isPlaying)
			{
				_player.pause();
			}
			else
			{
				_player.start();
			}
			setIsPlaying(!isPlaying);			
		}
		else
		{
			next(true);
		}
	}
	
	private void updatePlayPause()
	{
		int resourceId = _isPlaying ? R.drawable.pause : R.drawable.play;
		_playPause.setImageResource(resourceId);
	}
    
    /*************************
	 * RdioListener Interface
	 *************************/

	/*
	 * Dispatched by the Rdio object when the Rdio object is done initialising, and a connection
	 * to the Rdio app service has been established.  If authorised is true, then we reused our
	 * existing OAuth credentials, and the API is ready for use.
	 * @see com.rdio.android.api.RdioListener#onRdioReady()
	 */
    @Override
	public void onRdioReady()
    {
    	Log.i(TAG, "User state is " + getRdio().getSubscriptionState() + " fullstream " + getRdio().canUserPlayFullStreams());
		doSomething();		
	}
    
	/*
	 * Dispatched by the Rdio object once the setTokenAndSecret call has finished, and the credentials are
	 * ready to be used to make API calls.  The token & token secret are passed in so that you can
	 * save/cache them for future re-use.
	 * @see com.rdio.android.api.RdioListener#onRdioAuthorised(java.lang.String, java.lang.String)
	 */
	@Override
	public void onRdioAuthorised(String accessToken, String accessTokenSecret)
	{
		Log.i(TAG, "Application authorised, saving access token & secret.");
		Log.d(TAG, "Access token: " + accessToken);
		Log.d(TAG, "Access token secret: " + accessTokenSecret);
		
		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		Editor editor = settings.edit();
		editor.putString(PREF_ACCESSTOKEN, accessToken);
		editor.putString(PREF_ACCESSTOKENSECRET, accessTokenSecret);
		editor.commit();
		
		doSomething();
	}	

	/*
	 * Dispatched by the Rdio object when app approval is needed.  Take the authorisation intent given
	 * and invoke the activity for it
	 * @see com.rdio.android.api.RdioListener#onRdioUserAppApprovalNeeded(android.content.Intent)
	 */
	@Override	
	public void onRdioUserAppApprovalNeeded(Intent authorisationIntent)
	{
		try
		{
			startActivityForResult(authorisationIntent, REQUEST_AUTHORISE_APP);
		}
		catch (ActivityNotFoundException e)
		{
			// Rdio app not found
			Log.e(TAG, "Rdio app not found, limited to 30s samples.");
		}
	}

	@Override
	public void onRdioUserPlayingElsewhere()
	{
		// TODO Auto-generated method stub
	}
	
	/*************************
	 * Activity overrides
	 *************************/	
	@Override
	protected Dialog onCreateDialog(int id)
	{
		int messageId = Utilities.getProgressDialogMessage(id);
		if (messageId != 0)
		{
			return ProgressDialog.show(this, "", getResources().getString(messageId));
		}
		return null;
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode)
		{
		case REQUEST_AUTHORISE_APP:
			onRequestAuthorisationResult(resultCode, data);		
			break;
		case REQUEST_ARTIST_RADIO:
			onRequestArtistRadioResult(resultCode, data);
			break;
		default:
			break;
		}
	}

	private void onRequestAuthorisationResult(int resultCode, Intent data)
	{
		if (resultCode == Rdio.RESULT_AUTHORISATION_ACCEPTED)
		{
			Log.i(TAG, "User authorised our app.");
			getRdio().setTokenAndSecret(data);
		}
		else if (resultCode == Rdio.RESULT_AUTHORISATION_REJECTED)
		{
			Log.i(TAG, "User rejected our app.");
		}
	}

	private void onRequestArtistRadioResult(int resultCode, Intent data)
	{
		if (resultCode == RESULT_OK)
		{
			next(true);
		}
	}

	private void initializeControls()
	{
		_artistListIcon = (ImageView) findViewById(R.id.artistListIcon);
		_artistListIcon.setOnClickListener(this);
		
        ImageView nextImage = (ImageView) findViewById(R.id.next);
        nextImage.setOnClickListener(this);        

        _playPause = (ImageView) findViewById(R.id.playPause);
        _playPause.setOnClickListener(this);

        _albumArt = (ImageView)findViewById(R.id.albumArt);
        
        _trackName = (TextView) findViewById(R.id.trackName);
        _trackName.setVisibility(View.GONE);
        
        _albumName = (TextView) findViewById(R.id.albumName);
        _albumName.setVisibility(View.GONE);
        
        _artistName = (TextView) findViewById(R.id.artistName);
        _artistName.setVisibility(View.GONE);
        
        _trackPositionBar = (SeekBar) findViewById(R.id.trackPositionBar);
        _trackPositionBar.setOnSeekBarChangeListener(getOnSeekBarChangeListener());
	}	
	
	private Rdio getRdio()
	{
		return RdioShufflerApp.getInstance().getRdio();
	}
	
	private Queue<Track> getTrackQueue()
	{
		return RdioShufflerApp.getInstance().getTrackQueue();
	}

	@Override
	public void onClick(View view)
	{
		switch (view.getId())
		{
		case R.id.next:
			onNextClicked();
			break;
		case R.id.playPause:
			onPlayPauseClicked();
			break;
		case R.id.artistListIcon:
			onArtistListIconClicked();
			break;
		}		
	}
	
	private void onNextClicked()
	{
		next(true);
	}
	
	private void onPlayPauseClicked()
	{
		playPause();
	}

	private void onArtistListIconClicked()
	{
		Intent intent = new Intent();
		intent.setClass(this, ArtistRadioActivity.class);
		startActivityForResult(intent, REQUEST_ARTIST_RADIO);
	}
	
	@Override
    public void onPause()
    {        
        super.onPause();
        _isCurrentActivity = false;
    }
	
	@Override
	public void onResume()
	{
		super.onResume();
		_isCurrentActivity = true;
	}
	
    private void startPositionThread()
    {
    	_positionThread = new Thread(new Runnable()
    	{		
			@Override
			public void run()
			{
				try
				{
					int currentPosition = 0;
					int trackDuration = _player.getDuration();
					_trackPositionBar.setMax(trackDuration);

					while (_player != null)
					{
						currentPosition = _player.getCurrentPosition();						

						Message msg = new Message();
						msg.what = currentPosition;
						_threadPositionHandler.sendMessage(msg);

						Thread.sleep(1000);
					}
				}
				catch (InterruptedException e)
				{				
					e.printStackTrace();
				}
				catch (IllegalStateException e)
				{
					e.printStackTrace();
				}
			}
		});
    	_positionThread.start();
    }
    
    private void stopPositionThread()
    {
    	if (_positionThread != null)
    	{
    		_positionThread = null;
    	}
    }
    
    private void initializePositionHandler()
    {
        _threadPositionHandler = new Handler()
        {
    		public void handleMessage(Message msg)
    		{
    			super.handleMessage(msg);
    			_trackPositionBar.setProgress(msg.what);
    		}
    	};
    }
    
    private OnSeekBarChangeListener getOnSeekBarChangeListener()
    {
    	if (_seekBarChangeListener == null)
    	{
    		_seekBarChangeListener = new OnSeekBarChangeListener()
    		{				
				@Override
				public void onStopTrackingTouch(SeekBar seekBar)
				{
				}
				
				@Override
				public void onStartTrackingTouch(SeekBar seekBar)
				{
				}
				
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
				{
					if (fromUser && isPlayingSomething())
					{
						_player.seekTo(progress);
					}
				}
			};
    	}
    	return _seekBarChangeListener;
    }
}